package com.assistant.floatscroller.adb

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class AdbConnection(
    private val host: String = "127.0.0.1",
    private val port: Int,
    private val keyPair: AdbKeyPair
) {
    companion object {
        private const val TAG = "AdbConnection"
        private const val CONNECT_TIMEOUT_MS = 5000
    }

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val localIdCounter = AtomicInteger(1)
    var isConnected = false
        private set

    /**
     * 连接并完成 ADB 认证握手
     */
    fun connect() {
        val s = Socket()
        s.tcpNoDelay = true
        s.keepAlive = true
        s.soTimeout = 15000 // 15秒超时，防止等待弹窗时卡死
        s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

        val input = s.getInputStream()
        val output = s.getOutputStream()

        this.socket = s
        this.inputStream = input
        this.outputStream = output

        // 1. 发送 CNXN 请求
        val banner = "host::FloatScroller\u0000".toByteArray(Charsets.UTF_8)
        AdbProtocol.writeMessage(output, AdbProtocol.CMD_CNXN, AdbProtocol.ADB_VERSION, AdbProtocol.MAX_DATA, banner)

        // 2. 处理系统响应与授权
        var handshaked = false
        var sentPublicKey = false

        while (!handshaked) {
            val msg = AdbProtocol.readMessage(input)
            when (msg.command) {
                AdbProtocol.CMD_CNXN -> {
                    Log.d(TAG, "ADB 握手成功！系统标识: ${String(msg.payload)}")
                    handshaked = true
                    isConnected = true
                }
                AdbProtocol.CMD_AUTH -> {
                    if (msg.arg0 == AdbProtocol.AUTH_TYPE_TOKEN) {
                        if (!sentPublicKey) {
                            // 第一次尝试私钥签名响应挑战
                            try {
                                val signed = keyPair.sign(msg.payload)
                                AdbProtocol.writeMessage(output, AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_TYPE_SIGNATURE, 0, signed)
                                sentPublicKey = true
                            } catch (e: Exception) {
                                Log.w(TAG, "签名失败，直接发送公钥: ${e.message}")
                                AdbProtocol.writeMessage(output, AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_TYPE_RSAPUBLICKEY, 0, keyPair.publicKeyBytes)
                            }
                        } else {
                            // 签名未通过，发送公钥触发系统在屏幕上弹出“是否允许调试”弹窗
                            Log.d(TAG, "发送 ADB 公钥，等待用户在系统弹窗中确认允许...")
                            AdbProtocol.writeMessage(output, AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_TYPE_RSAPUBLICKEY, 0, keyPair.publicKeyBytes)
                        }
                    }
                }
                else -> {
                    Log.w(TAG, "收到未预期的 ADB 帧: 0x${Integer.toHexString(msg.command)}")
                }
            }
        }
    }

    /**
     * 执行单条 Shell 指令（如 input keyevent 19）
     */
    fun exec(command: String) {
        val out = outputStream ?: throw IllegalStateException("ADB 尚未连接")
        val input = inputStream ?: throw IllegalStateException("ADB 尚未连接")

        val localId = localIdCounter.incrementAndGet()
        val cmdPayload = "shell:$command\u0000".toByteArray(Charsets.UTF_8)

        // 发送 OPEN 命令打开一个 shell 流
        AdbProtocol.writeMessage(out, AdbProtocol.CMD_OPEN, localId, 0, cmdPayload)

        // 读取响应直到流关闭
        while (true) {
            val msg = AdbProtocol.readMessage(input)
            if (msg.arg1 == localId) {
                when (msg.command) {
                    AdbProtocol.CMD_OKAY -> {
                        // 远程已接收打开
                    }
                    AdbProtocol.CMD_WRTE -> {
                        // 远程返回的命令输出，给远端回复 OKAY
                        AdbProtocol.writeMessage(out, AdbProtocol.CMD_OKAY, localId, msg.arg0)
                    }
                    AdbProtocol.CMD_CLSE -> {
                        // 命令执行完成，远程关闭了流
                        AdbProtocol.writeMessage(out, AdbProtocol.CMD_CLSE, localId, msg.arg0)
                        return
                    }
                }
            }
        }
    }

    /**
     * 关闭连接
     */
    fun close() {
        isConnected = false
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
    }
}
