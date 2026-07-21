package com.example.mediacontrol

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 只做一件事：引导用户开启"通知使用权"权限。
 * 开启之后 MediaControlListenerService 就会自动在后台运行，
 * 只要 Spotify 在播放，通知栏就会出现带 repeat/like 按钮的通知。
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 200, 64, 64)
        }

        val text = TextView(this).apply {
            text = "第 1 步：授予「通知使用权」\n\n" +
                "授权后，本 App 会读取 Spotify 当前的播放状态，" +
                "并在通知栏里重新生成一条带完整按钮（包含 repeat / like）的通知。\n\n" +
                "第 2 步：把本 App 加入省电策略白名单 / 允许自启动，" +
                "否则 HyperOS 可能会在后台把它杀掉。"
            textSize = 15f
        }

        val button = Button(this).apply {
            text = "打开通知使用权设置"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        layout.addView(text)
        layout.addView(button)
        setContentView(layout)
    }
}
