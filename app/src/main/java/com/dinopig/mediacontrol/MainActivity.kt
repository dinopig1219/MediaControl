package com.dinopig.mediacontrol

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.System) }
            MiuixTheme(controller = controller) {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    var permissionStatus by remember { mutableStateOf(currentPermissionStatus(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionStatus = if (granted) {
            "通知权限：已授予 ✓ 接下来去开通知使用权。"
        } else {
            "没有通知权限的话，系统会直接吞掉本 App 生成的通知，请重新授予。"
        }
    }

    val prefs = remember { context.getSharedPreferences("debug_info", Context.MODE_PRIVATE) }
    var debugNotificationsOn by remember {
        mutableStateOf(prefs.getBoolean("debug_notifications_enabled", false))
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "媒体控制补丁", style = MiuixTheme.textStyles.title1)

            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "第 1 步：授予「通知权限」\n\n第 2 步：授予「通知使用权」\n\n" +
                        "授权后，本 App 会读取 Spotify 当前的播放状态，并在通知栏里重新生成一条带完整按钮的通知。\n\n" +
                        "第 3 步：把本 App 加入省电策略白名单 / 允许自启动，否则 HyperOS 可能会在后台把它杀掉。",
                    modifier = Modifier.padding(16.dp)
                )
            }

            Text(text = permissionStatus)

            TextButton(
                text = "授予通知权限",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            permissionStatus = "通知权限已经有了，去开通知使用权吧。"
                        }
                    } else {
                        permissionStatus = "当前系统版本不需要单独申请通知权限。"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(
                text = "打开通知使用权设置",
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            )

            SwitchPreference(
                title = "显示调试通知",
                summary = "默认关闭，开启后通知栏会多一条调试信息",
                checked = debugNotificationsOn,
                onCheckedChange = { checked ->
                    debugNotificationsOn = checked
                    prefs.edit().putBoolean("debug_notifications_enabled", checked).apply()
                }
            )

            TextButton(
                text = "查看调试信息（App 内完整版）",
                onClick = { context.startActivity(Intent(context, DebugActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun currentPermissionStatus(context: Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return "当前系统版本不需要单独申请通知权限。"
    }
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    return if (granted) "通知权限：已授予 ✓" else "通知权限：尚未授予 ✗（点下面按钮授予）"
}
