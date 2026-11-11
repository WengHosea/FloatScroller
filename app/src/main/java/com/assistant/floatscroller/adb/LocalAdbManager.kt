package com.assistant.floatscroller.adb

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.util.concurrent.Executors

object LocalAdbManager {
    private const val TAG = "LocalAdbManager"

    const val MODE_KEYEVENT = 0    // 物理键盘方向键
    const val MODE_MOUSE = 1       // 鼠标滚轮指令
    const val MODE_SWIPE = 2       // 硬件级原生滑动

    var currentMode: Int = MODE_KEYEVENT
    var intervalSeconds: Int = 10
    var isAutoScrolling: Boolean = false
        private set

    var onAutoScrollStateChangeListener: ((Boolean) -> Unit)? = null
    var onConnectionStateListener: ((Boolean, String?) -> Unit)? = null

    private var currentConnection: AdbConnection? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (isAutoScrolling) {
                scrollDown(null)
                mainHandler.postDelayed(this, intervalSeconds * 1000L)
            }
        }
    }

    val isConnected: Boolean
        get() = currentConnection?.isConnected == true

    /**
     * 连接本地无线调试端口
     */
    fun connect(context: Context, port: Int) {
        executor.execute {
            try {
                // 如果已有连接先关闭
                currentConnection?.close()

                mainHandler.post {
                    onConnectionStateListener?.invoke(false, "正在连接端口 $port ...")
                }

                val keyPair = AdbKeyPair.getOrGenerate(context.applicationContext)
                val conn = AdbConnection(port = port, keyPair = keyPair)
                conn.connect()
                currentConnection = conn

                mainHandler.post {
                    onConnectionStateListener?.invoke(true, "连接成功！本地 ADB 在线")
                    Toast.makeText(context, "无线调试连接成功！已获得物理按键权限", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "连接失败", e)
                currentConnection = null
                mainHandler.post {
                    onConnectionStateListener?.invoke(false, "连接失败: ${e.message}（请确认无线调试已开启）")
                    Toast.makeText(context, "连接失败，请确认无线调试端口正确并开启", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        stopAutoScroll()
        currentConnection?.close()
        currentConnection = null
        onConnectionStateListener?.invoke(false, "已断开连接")
    }

    /**
     * 触发向上翻页（按键上 / 鼠标滚轮上）
     */
    fun scrollUp(context: Context?) {
        val command = when (currentMode) {
            MODE_KEYEVENT -> "input keyevent 19" // KEYCODE_DPAD_UP
            MODE_MOUSE -> "input roll 0 -1"      // 鼠标滚轮向上
            MODE_SWIPE -> {
                val metrics = context?.resources?.displayMetrics
                val w = metrics?.widthPixels ?: 1080
                val h = metrics?.heightPixels ?: 2400
                "input swipe ${w / 2} ${h / 4} ${w / 2} ${h * 3 / 4} 150"
            }
            else -> "input keyevent 19"
        }
        executeShell(command, context)
    }

    /**
     * 触发向下翻页（按键下 / 鼠标滚轮下）
     */
    fun scrollDown(context: Context?) {
        val command = when (currentMode) {
            MODE_KEYEVENT -> "input keyevent 20" // KEYCODE_DPAD_DOWN
            MODE_MOUSE -> "input roll 0 1"       // 鼠标滚轮向下
            MODE_SWIPE -> {
                val metrics = context?.resources?.displayMetrics
                val w = metrics?.widthPixels ?: 1080
                val h = metrics?.heightPixels ?: 2400
                "input swipe ${w / 2} ${h * 3 / 4} ${w / 2} ${h / 4} 150"
            }
            else -> "input keyevent 20"
        }
        executeShell(command, context)
    }

    /**
     * 切换自动翻页
     */
    fun toggleAutoScroll(context: Context?): Boolean {
        if (isAutoScrolling) {
            stopAutoScroll()
        } else {
            startAutoScroll(context)
        }
        return isAutoScrolling
    }

    fun startAutoScroll(context: Context?) {
        isAutoScrolling = true
        onAutoScrollStateChangeListener?.invoke(true)
        mainHandler.removeCallbacks(autoScrollRunnable)
        scrollDown(context)
        mainHandler.postDelayed(autoScrollRunnable, intervalSeconds * 1000L)
    }

    fun stopAutoScroll() {
        isAutoScrolling = false
        onAutoScrollStateChangeListener?.invoke(false)
        mainHandler.removeCallbacks(autoScrollRunnable)
    }

    /**
     * 异步执行 Shell 命令
     */
    fun executeShell(command: String, context: Context?) {
        executor.execute {
            try {
                val conn = currentConnection
                if (conn != null && conn.isConnected) {
                    conn.exec(command)
                    Log.d(TAG, "本地 ADB 指令执行成功: $command")
                } else {
                    // 尝试 fallback 到普通 su 命令（如果机器已经 Root）
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                    process.waitFor()
                    Log.d(TAG, "Root 指令执行成功: $command")
                }
            } catch (e: Exception) {
                Log.e(TAG, "指令执行失败: $command", e)
                context?.let { ctx ->
                    mainHandler.post {
                        Toast.makeText(ctx, "未连接到无线调试，请先在主界面点击【连接】", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
