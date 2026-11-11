package com.assistant.floatscroller

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.assistant.floatscroller.adb.LocalAdbManager
import com.assistant.floatscroller.databinding.ActivityMainBinding
import com.assistant.floatscroller.service.FloatingWindowService
import com.assistant.floatscroller.utils.PermissionUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sp: SharedPreferences

    companion object {
        private const val SP_NAME = "float_local_adb_config"
        private const val KEY_PORT = "key_port"
        private const val KEY_MODE = "key_command_mode"
        private const val KEY_INTERVAL = "key_interval"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sp = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

        initViews()
        loadPreferences()
        setupAdbListener()
    }

    override fun onResume() {
        super.onResume()
        updateOverlayUI()
        updateAdbUI(LocalAdbManager.isConnected, if (LocalAdbManager.isConnected) getString(R.string.adb_status_connected) else getString(R.string.adb_status_disconnected))
        updateFloatingSwitchUI()
    }

    private fun initViews() {
        // 1. 悬浮窗权限授权按钮
        binding.btnGrantOverlay.setOnClickListener {
            if (!PermissionUtils.hasOverlayPermission(this)) {
                PermissionUtils.requestOverlayPermission(this)
            }
        }

        // 2. 本地无线调试连接 / 断开按钮
        binding.btnConnectAdb.setOnClickListener {
            if (LocalAdbManager.isConnected) {
                LocalAdbManager.disconnect()
            } else {
                val portStr = binding.etPort.text?.toString()?.trim() ?: ""
                val port = portStr.toIntOrNull()
                if (port == null || port <= 1024 || port > 65535) {
                    Toast.makeText(this, "请输入正确的 5 位无线调试端口号", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                sp.edit().putString(KEY_PORT, portStr).apply()
                LocalAdbManager.connect(this, port)
            }
        }

        // 3. 悬浮窗开关
        binding.switchFloating.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startFloatingService()
            } else {
                stopFloatingService()
            }
        }

        // 4. 指令模式切换
        binding.rgCommandMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_mode_keyevent -> LocalAdbManager.MODE_KEYEVENT
                R.id.rb_mode_mouse -> LocalAdbManager.MODE_MOUSE
                R.id.rb_mode_swipe -> LocalAdbManager.MODE_SWIPE
                else -> LocalAdbManager.MODE_KEYEVENT
            }
            sp.edit().putInt(KEY_MODE, mode).apply()
            LocalAdbManager.currentMode = mode
        }

        // 5. 自动翻页时间滑动条
        binding.sbInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sec = if (progress < 3) 3 else progress
                binding.tvIntervalVal.text = "${sec} 秒"
                if (fromUser) {
                    sp.edit().putInt(KEY_INTERVAL, sec).apply()
                    LocalAdbManager.intervalSeconds = sec
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupAdbListener() {
        LocalAdbManager.onConnectionStateListener = { connected, message ->
            updateAdbUI(connected, message)
        }
    }

    private fun updateAdbUI(connected: Boolean, message: String?) {
        if (connected) {
            binding.tvAdbStatus.apply {
                text = message ?: getString(R.string.adb_status_connected)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_success))
            }
            binding.btnConnectAdb.text = getString(R.string.btn_disconnect)
        } else {
            binding.tvAdbStatus.apply {
                text = message ?: getString(R.string.adb_status_disconnected)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_warn))
            }
            binding.btnConnectAdb.text = getString(R.string.btn_connect)
        }
    }

    private fun updateOverlayUI() {
        val hasOverlay = PermissionUtils.hasOverlayPermission(this)
        binding.btnGrantOverlay.apply {
            isEnabled = !hasOverlay
            text = if (hasOverlay) getString(R.string.btn_granted) else getString(R.string.btn_grant)
        }
    }

    private fun updateFloatingSwitchUI() {
        val isRunning = FloatingWindowService.isRunning
        binding.switchFloating.isChecked = isRunning
        binding.tvFloatingStatus.text = if (isRunning) {
            getString(R.string.tv_floating_status_running)
        } else {
            getString(R.string.tv_floating_status_stopped)
        }
    }

    private fun loadPreferences() {
        val savedPort = sp.getString(KEY_PORT, "")
        if (!savedPort.isNullOrEmpty()) {
            binding.etPort.setText(savedPort)
        }

        val savedMode = sp.getInt(KEY_MODE, LocalAdbManager.MODE_KEYEVENT)
        when (savedMode) {
            LocalAdbManager.MODE_KEYEVENT -> binding.rbModeKeyevent.isChecked = true
            LocalAdbManager.MODE_MOUSE -> binding.rbModeMouse.isChecked = true
            LocalAdbManager.MODE_SWIPE -> binding.rbModeSwipe.isChecked = true
        }
        LocalAdbManager.currentMode = savedMode

        val savedInterval = sp.getInt(KEY_INTERVAL, 10)
        binding.sbInterval.progress = savedInterval
        binding.tvIntervalVal.text = "${savedInterval} 秒"
        LocalAdbManager.intervalSeconds = savedInterval
    }

    private fun startFloatingService() {
        if (!PermissionUtils.hasOverlayPermission(this)) {
            Toast.makeText(this, "请先授予【悬浮窗显示权限】", Toast.LENGTH_SHORT).show()
            binding.switchFloating.isChecked = false
            PermissionUtils.requestOverlayPermission(this)
            return
        }

        if (!LocalAdbManager.isConnected) {
            Toast.makeText(this, "提示：本地 ADB 尚未连接，请先输入端口点击【连接】", Toast.LENGTH_LONG).show()
        }

        val intent = Intent(this, FloatingWindowService::class.java)
        startService(intent)
        binding.tvFloatingStatus.text = getString(R.string.tv_floating_status_running)
        Toast.makeText(this, "悬浮窗已开启！", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        stopService(intent)
        binding.tvFloatingStatus.text = getString(R.string.tv_floating_status_stopped)
    }
}
