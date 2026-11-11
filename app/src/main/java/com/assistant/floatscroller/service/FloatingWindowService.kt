package com.assistant.floatscroller.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import com.assistant.floatscroller.R
import com.assistant.floatscroller.adb.LocalAdbManager
import kotlin.math.abs

class FloatingWindowService : Service() {

    companion object {
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private var vibrator: Vibrator? = null

    private lateinit var layoutBubble: FrameLayout
    private lateinit var layoutPanel: LinearLayout
    private lateinit var btnAutoScroll: ImageButton

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        initFloatingView()
        setupListeners()
    }

    @SuppressLint("InflateParams")
    private fun initFloatingView() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 400
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_panel, null)

        layoutBubble = floatingView.findViewById(R.id.layout_bubble)
        layoutPanel = floatingView.findViewById(R.id.layout_panel)
        btnAutoScroll = floatingView.findViewById(R.id.btn_auto_scroll)

        windowManager.addView(floatingView, params)
    }

    private fun setupListeners() {
        val btnCollapse = floatingView.findViewById<ImageButton>(R.id.btn_collapse)
        val btnScrollUp = floatingView.findViewById<ImageButton>(R.id.btn_scroll_up)
        val btnScrollDown = floatingView.findViewById<ImageButton>(R.id.btn_scroll_down)
        val btnClose = floatingView.findViewById<ImageButton>(R.id.btn_close_panel)

        // 1. 模拟上翻（物理键盘方向上键 / 鼠标滚轮上）
        btnScrollUp.setOnClickListener {
            vibrateFeedback()
            LocalAdbManager.scrollUp(this)
        }

        // 2. 模拟下翻（物理键盘方向下键 / 鼠标滚轮下）
        btnScrollDown.setOnClickListener {
            vibrateFeedback()
            LocalAdbManager.scrollDown(this)
        }

        // 3. 自动翻页切换
        btnAutoScroll.setOnClickListener {
            vibrateFeedback()
            val isScrolling = LocalAdbManager.toggleAutoScroll(this)
            updateAutoScrollIcon(isScrolling)
        }

        // 4. 收起为迷你气泡
        btnCollapse.setOnClickListener {
            vibrateFeedback()
            collapseToBubble()
        }

        // 5. 点击气泡展开面板
        layoutBubble.setOnClickListener {
            vibrateFeedback()
            expandToPanel()
        }

        // 6. 关闭悬浮窗
        btnClose.setOnClickListener {
            stopSelf()
        }

        // 监听自动翻页状态变化
        LocalAdbManager.onAutoScrollStateChangeListener = { isScrolling ->
            updateAutoScrollIcon(isScrolling)
        }

        // 拖拽与松手贴边
        enableDrag(layoutPanel)
        enableDrag(layoutBubble)
    }

    private fun updateAutoScrollIcon(isScrolling: Boolean) {
        btnAutoScroll.setImageResource(if (isScrolling) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun collapseToBubble() {
        layoutPanel.visibility = View.GONE
        layoutBubble.visibility = View.VISIBLE
    }

    private fun expandToPanel() {
        layoutBubble.visibility = View.GONE
        layoutPanel.visibility = View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isMoving = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isMoving = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(floatingView, params)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isMoving) {
                            val screenWidth = resources.displayMetrics.widthPixels
                            params.x = if (params.x + view.width / 2 < screenWidth / 2) 20 else screenWidth - view.width - 20
                            windowManager.updateViewLayout(floatingView, params)
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        LocalAdbManager.stopAutoScroll()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
