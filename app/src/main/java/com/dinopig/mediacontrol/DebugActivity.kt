package com.dinopig.mediacontrol

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DebugActivity : AppCompatActivity() {

    private lateinit var contentText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }

        val refreshButton = Button(this).apply {
            text = "刷新"
            setOnClickListener { loadDebugInfo() }
        }

        contentText = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(this).apply {
            addView(contentText)
        }

        root.addView(refreshButton)
        root.addView(scrollView)
        setContentView(root)

        loadDebugInfo()
    }

    override fun onResume() {
        super.onResume()
        loadDebugInfo()
    }

    private fun loadDebugInfo() {
        val info = getSharedPreferences("debug_info", Context.MODE_PRIVATE)
            .getString("last_debug_info", null)
        contentText.text = info ?: "还没有数据。请先播放 Spotify，确保已授权通知使用权。"
    }
}
