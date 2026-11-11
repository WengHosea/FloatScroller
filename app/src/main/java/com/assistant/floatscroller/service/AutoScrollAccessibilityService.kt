package com.assistant.floatscroller.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

class AutoScrollAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoScrollService"
        var instance: AutoScrollAccessibilityService? = null
            private set

        const val MODE_VIDEO = 0  // 短视频模式：大滑动距离，快速响应
        const val MODE_BOOK = 1   // 电子书模式：中等滑动距离，平滑柔和
    }

    private val handler = Handler(Looper.getMainLooper())
    var currentMode: Int = MODE_VIDEO
    var intervalSeconds: Int = 10
    var isAutoScrolling: Boolean = false
        private set

    // 状态变动回调通知悬浮窗更新图标
    var onAutoScrollStateChangeListener: ((Boolean) -> Unit)? = null

    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (isAutoScrolling) {
                scrollDown()
                handler.postDelayed(this, intervalSeconds * 1000L)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoScroll()
        instance = null
        Log.d(TAG, "无障碍服务已销毁")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 无需监听具体事件，专注于手势分发
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务中断")
        stopAutoScroll()
    }

    /**
     * 模拟向下翻页（相当于键盘向下键、鼠标向下滚轮；视觉上是屏幕向上滑动，显示下一页/下一个视频）
     */
    fun scrollDown() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        performSwipe(isScrollDown = true)
    }

    /**
     * 模拟向上翻页（相当于键盘向上键、鼠标向上滚轮；视觉上是屏幕向下滑动，显示上一页/上一个视频）
     */
    fun scrollUp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        performSwipe(isScrollDown = false)
    }

    /**
     * 开启/停止自动翻页
     */
    fun toggleAutoScroll(): Boolean {
        if (isAutoScrolling) {
            stopAutoScroll()
        } else {
            startAutoScroll()
        }
        return isAutoScrolling
    }

    fun startAutoScroll() {
        isAutoScrolling = true
        onAutoScrollStateChangeListener?.invoke(true)
        handler.removeCallbacks(autoScrollRunnable)
        // 开启后立即翻动一次，随后进入周期
        scrollDown()
        handler.postDelayed(autoScrollRunnable, intervalSeconds * 1000L)
    }

    fun stopAutoScroll() {
        isAutoScrolling = false
        onAutoScrollStateChangeListener?.invoke(false)
        handler.removeCallbacks(autoScrollRunnable)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun performSwipe(isScrollDown: Boolean) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val centerX = screenWidth / 2f
        val startY: Float
        val endY: Float
        val duration: Long

        if (currentMode == MODE_VIDEO) {
            // 短视频模式：大距离、快速干脆滑动
            duration = 130L
            if (isScrollDown) {
                startY = screenHeight * 0.78f
                endY = screenHeight * 0.22f
            } else {
                startY = screenHeight * 0.22f
                endY = screenHeight * 0.78f
            }
        } else {
            // 电子书模式：中距离平滑翻页
            duration = 240L
            if (isScrollDown) {
                startY = screenHeight * 0.65f
                endY = screenHeight * 0.35f
            } else {
                startY = screenHeight * 0.35f
                endY = screenHeight * 0.65f
            }
        }

        val swipePath = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(swipePath, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "手势分发成功: ${if (isScrollDown) "下翻" else "上翻"}")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "手势分发被系统取消")
            }
        }, null)
    }
}
