package com.assistant.floatscroller

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.assistant.floatscroller.databinding.ActivityMainBinding
import com.assistant.floatscroller.service.AutoScrollAccessibilityService
import com.assistant.floatscroller.service.FloatingWindowService
import com.assistant.floatscroller.utils.PermissionUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sp: SharedPreferences

    companion object {
        private const val SP_NAME = "float_scroller_config"
        private const val KEY_MODE = "key_mode"
        private const val KEY_INTERVAL = "key_interval"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

        initViews()
        loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()
        updateFloatingSwitchUI()
    }

    private fun initViews() {
        // 1. 悬浮窗权限授权按钮
        binding.btnGrantOverlay.setOnClickListener {
            if (!PermissionUtils.hasOverlayPermission(this)) {
                PermissionUtils.requestOverlayPermission(this)
            }
        }

        // 2. 无障碍服务权限授权按钮
        binding.btnGrantAccessibility.setOnClickListener {
            if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
                PermissionUtils.requestAccessibilityPermission(this)
            }
        }

        // 3. 悬浮窗启停开关
        binding.switchFloating.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startFloatingService()
            } else {
                stopFloatingService()
            }
        }

        // 4. 模式切换
        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_mode_video) {
                AutoScrollAccessibilityService.MODE_VIDEO
            } else {
                AutoScrollAccessibilityService.MODE_BOOK
            }
            sp.edit().putInt(KEY_MODE, mode).apply()
            AutoScrollAccessibilityService.instance?.currentMode = mode
        }

        // 5. 自动翻页间隔设置
        binding.sbInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sec = if (progress < 3) 3 else progress
                binding.tvIntervalVal.text = "${sec} 秒"
                if (fromUser) {
                    sp.edit().putInt(KEY_INTERVAL, sec).apply()
                    AutoScrollAccessibilityService.instance?.intervalSeconds = sec
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadPreferences() {
        val savedMode = sp.getInt(KEY_MODE, AutoScrollAccessibilityService.MODE_VIDEO)
        if (savedMode == AutoScrollAccessibilityService.MODE_VIDEO) {
            binding.rbModeVideo.isChecked = true
        } else {
            binding.rbModeBook.isChecked = true
        }
        AutoScrollAccessibilityService.instance?.currentMode = savedMode

        val savedInterval = sp.getInt(KEY_INTERVAL, 10)
        binding.sbInterval.progress = savedInterval
        binding.tvIntervalVal.text = "${savedInterval} 秒"
        AutoScrollAccessibilityService.instance?.intervalSeconds = savedInterval
    }

    private fun updatePermissionUI() {
        // 悬浮窗权限
        val hasOverlay = PermissionUtils.hasOverlayPermission(this)
        binding.btnGrantOverlay.apply {
            isEnabled = !hasOverlay
            text = if (hasOverlay) getString(R.string.btn_granted) else getString(R.string.btn_grant)
        }

        // 无障碍权限
        val hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(this)
        binding.btnGrantAccessibility.apply {
            isEnabled = !hasAccessibility
            text = if (hasAccessibility) getString(R.string.btn_granted) else getString(R.string.btn_grant)
        }
    }

    private fun updateFloatingSwitchUI() {
        val isRunning = FloatingWindowService.isRunning
        binding.switchFloating.isChecked = isRunning
        binding.tvFloatingStatus.text = if (isRunning) "悬浮窗已开启 (正在运行)" else "悬浮窗未运行"
    }

    private fun startFloatingService() {
        if (!PermissionUtils.hasOverlayPermission(this)) {
            Toast.makeText(this, "请先开启【悬浮窗显示权限】", Toast.LENGTH_SHORT).show()
            binding.switchFloating.isChecked = false
            PermissionUtils.requestOverlayPermission(this)
            return
        }

        if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
            Toast.makeText(this, "请先开启【无障碍辅助服务】", Toast.LENGTH_SHORT).show()
            binding.switchFloating.isChecked = false
            PermissionUtils.requestAccessibilityPermission(this)
            return
        }

        val intent = Intent(this, FloatingWindowService::class.java)
        startService(intent)
        binding.tvFloatingStatus.text = "悬浮窗已开启 (正在运行)"
        Toast.makeText(this, "悬浮窗已开启，可切换至短视频或电子书应用使用", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        stopService(intent)
        binding.tvFloatingStatus.text = "悬浮窗未运行"
    }
}
