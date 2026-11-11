package com.assistant.floatscroller.adb

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AdbProtocol {
    const val CMD_CNXN = 0x4e584e43 // "CNXN"
    const val CMD_AUTH = 0x48545541 // "AUTH"
    const val CMD_OPEN = 0x4e45504f // "OPEN"
    const val CMD_OKAY = 0x59414b4f // "OKAY"
    const val CMD_CLSE = 0x45534c43 // "CLSE"
    const val CMD_WRTE = 0x45545257 // "WRTE"

    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSAPUBLICKEY = 3

    const val ADB_VERSION = 0x01000000
    const val MAX_DATA = 4096

    class Message(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataChecksum: Int,
        val magic: Int,
        val payload: ByteArray
    )

    fun calculateChecksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }

    /**
     * 发送一条 ADB 协议帧
     */
    fun writeMessage(
        out: OutputStream,
        command: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray? = null
    ) {
        val data = payload ?: ByteArray(0)
        val checksum = calculateChecksum(data)
        val magic = command xor -0x1 // command ^ 0xFFFFFFFF

        val buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(data.size)
        buffer.putInt(checksum)
        buffer.putInt(magic)

        synchronized(out) {
            out.write(buffer.array())
            if (data.isNotEmpty()) {
                out.write(data)
            }
            out.flush()
        }
    }

    /**
     * 读取一条 ADB 协议帧
     */
    fun readMessage(input: InputStream): Message {
        val header = ByteArray(24)
        var readCount = 0
        while (readCount < 24) {
            val count = input.read(header, readCount, 24 - readCount)
            if (count == -1) throw IllegalStateException("ADB 连接意外中断")
            readCount += count
        }

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val dataLength = buf.int
        val dataChecksum = buf.int
        val magic = buf.int

        val payload = ByteArray(dataLength)
        var payloadRead = 0
        while (payloadRead < dataLength) {
            val count = input.read(payload, payloadRead, dataLength - payloadRead)
            if (count == -1) throw IllegalStateException("读取 ADB Payload 意外中断")
            payloadRead += count
        }

        return Message(command, arg0, arg1, dataLength, dataChecksum, magic, payload)
    }
}
