package com.assistant.floatscroller.adb

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors

object LocalAdbManager {
    private const val TAG = "LocalAdbManager"

    const val MODE_KEYEVENT = 0    // 物理键盘方向键 (KEYCODE_DPAD_UP / DOWN)
    const val MODE_MOUSE = 1       // 鼠标滚轮指令 (input roll 0 1 / -1)

    var currentMode: Int = MODE_KEYEVENT
    var onConnectionStateListener: ((Boolean, String?) -> Unit)? = null

    private var currentConnection: AdbConnection? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    val isConnected: Boolean
        get() = currentConnection?.isConnected == true

    /**
     * 连接本地无线调试
     * 支持传入端口 (如 "38421") 或带 IP 的完整地址 (如 "192.168.1.5:38421")
     */
    fun connect(context: Context, inputTarget: String) {
        executor.execute {
            try {
                currentConnection?.close()

                val (targetHost, targetPort) = parseHostAndPort(inputTarget)
                if (targetPort <= 0) {
                    mainHandler.post {
                        onConnectionStateListener?.invoke(false, "端口号格式无效")
                        Toast.makeText(context, "请输入有效的 5 位端口号", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                // 候选 IP 列表 (优先用户输入的 IP，其次 127.0.0.1，再次手机分配的局域网 WiFi IP)
                val candidateHosts = mutableListOf<String>()
                if (!targetHost.isNullOrBlank()) {
                    candidateHosts.add(targetHost)
                }
                candidateHosts.add("127.0.0.1")
                candidateHosts.addAll(getLocalIpAddresses())
                val hostsToTry = candidateHosts.distinct()

                mainHandler.post {
                    onConnectionStateListener?.invoke(false, "正在连接无线调试端口 $targetPort ...")
                }

                val keyPair = AdbKeyPair.getOrGenerate(context.applicationContext)
                var connectedConn: AdbConnection? = null
                var lastException: Exception? = null

                for (host in hostsToTry) {
                    try {
                        Log.d(TAG, "尝试连接: $host:$targetPort")
                        val conn = AdbConnection(host = host, port = targetPort, keyPair = keyPair)
                        conn.connect()
                        if (conn.isConnected) {
                            connectedConn = conn
                            Log.d(TAG, "连接成功: $host:$targetPort")
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "连接 $host:$targetPort 失败: ${e.message}")
                        lastException = e
                    }
                }

                if (connectedConn != null) {
                    currentConnection = connectedConn
                    mainHandler.post {
                        onConnectionStateListener?.invoke(true, "连接成功！本地 ADB 在线 ✓")
                        Toast.makeText(context, "无线调试连接成功！物理按键已就绪", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    currentConnection = null
                    val errorMsg = lastException?.message ?: "无法连接到该端口"
                    mainHandler.post {
                        onConnectionStateListener?.invoke(false, "连接失败: $errorMsg")
                        Toast.makeText(context, "连接失败: $errorMsg\n请确认系统设置中已开启无线调试", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "连接过程异常", e)
                currentConnection = null
                mainHandler.post {
                    onConnectionStateListener?.invoke(false, "连接异常: ${e.message}")
                }
            }
        }
    }

    private fun parseHostAndPort(raw: String): Pair<String?, Int> {
        val trimmed = raw.trim()
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            val host = parts[0].trim()
            val port = parts[1].trim().toIntOrNull() ?: -1
            return Pair(host, port)
        }
        val port = trimmed.toIntOrNull() ?: -1
        return Pair(null, port)
    }

    private fun getLocalIpAddresses(): List<String> {
        val list = mutableListOf<String>()
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        inetAddress.hostAddress?.let { list.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun disconnect() {
        currentConnection?.close()
        currentConnection = null
        onConnectionStateListener?.invoke(false, "已断开连接")
    }

    /**
     * 单次触发：上键 / 鼠标滚轮向上
     */
    fun scrollUp(context: Context?) {
        val command = when (currentMode) {
            MODE_KEYEVENT -> "input keyevent 19" // 物理方向上键 KEYCODE_DPAD_UP
            MODE_MOUSE -> "input roll 0 -1"      // 鼠标滚轮向上
            else -> "input keyevent 19"
        }
        executeShell(command, context)
    }

    /**
     * 单次触发：下键 / 鼠标滚轮向下
     */
    fun scrollDown(context: Context?) {
        val command = when (currentMode) {
            MODE_KEYEVENT -> "input keyevent 20" // 物理方向下键 KEYCODE_DPAD_DOWN
            MODE_MOUSE -> "input roll 0 1"       // 鼠标滚轮向下
            else -> "input keyevent 20"
        }
        executeShell(command, context)
    }

    /**
     * 异步向系统发送 Shell 命令
     */
    fun executeShell(command: String, context: Context?) {
        executor.execute {
            try {
                val conn = currentConnection
                if (conn != null && conn.isConnected) {
                    conn.exec(command)
                    Log.d(TAG, "发送指令成功: $command")
                } else {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                    process.waitFor()
                    Log.d(TAG, "Root 指令执行成功: $command")
                }
            } catch (e: Exception) {
                Log.e(TAG, "指令发送失败: $command", e)
                context?.let { ctx ->
                    mainHandler.post {
                        Toast.makeText(ctx, "未连接到无线调试，请先在主界面点击【连接】", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
