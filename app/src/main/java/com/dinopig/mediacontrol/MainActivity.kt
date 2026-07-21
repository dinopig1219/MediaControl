package com.dinopig.mediacontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var statusText: TextView? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            statusText?.text = if (granted) {
                "通知权限：已授予 ✓ 接下来去开通知使用权。"
            } else {
                "没有通知权限的话，系统会直接吞掉本 App 生成的通知，请重新授予。"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 200, 64, 64)
        }

        val infoText = TextView(this).apply {
            text = "第 1 步：授予「通知权限」（安卓 13 及以上必须要有，否则系统会直接吞掉通知）\n\n" +
                "第 2 步：授予「通知使用权」\n\n" +
                "授权后，本 App 会读取 Spotify 当前的播放状态，" +
                "并在通知栏里重新生成一条带完整按钮（包含 repeat / like）的通知。\n\n" +
                "第 3 步：把本 App 加入省电策略白名单 / 允许自启动，" +
                "否则 HyperOS 可能会在后台把它杀掉。"
            textSize = 15f
        }

        statusText = TextView(this).apply {
            text = currentPermissionStatus()
            textSize = 13f
            setPadding(0, 24, 0, 24)
        }

        val permissionButton = Button(this).apply {
            text = "授予通知权限"
            setOnClickListener { requestNotificationPermissionIfNeeded() }
        }

        val listenerButton = Button(this).apply {
            text = "打开通知使用权设置"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        val debugButton = Button(this).apply {
            text = "查看调试信息"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DebugActivity::class.java))
            }
        }

        layout.addView(infoText)
        layout.addView(statusText)
        layout.addView(permissionButton)
        layout.addView(listenerButton)
        layout.addView(debugButton)
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        statusText?.text = currentPermissionStatus()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        statusText?.text = "通知权限已经有了，去开通知使用权吧。"
    }

    private fun currentPermissionStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return "当前系统版本不需要单独申请通知权限。"
        }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) "通知权限：已授予 ✓" else "通知权限：尚未授予 ✗（点下面按钮授予）"
    }
}
