package com.dinopig.mediacontrol

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

private fun isNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("debug_info", Context.MODE_PRIVATE) }

    var notificationGranted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var masterEnabled by remember { mutableStateOf(prefs.getBoolean("master_enabled", true)) }
    var debugNotificationsOn by remember {
        mutableStateOf(prefs.getBoolean("debug_notifications_enabled", false))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationGranted = granted }

    // App 从设置页返回时，重新检查一次权限的真实状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = isNotificationPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "媒体控制补丁", style = MiuixTheme.textStyles.title3)

            SwitchPreference(
                title = "服务总开关",
                summary = if (masterEnabled) "正在运行，Spotify 播放时会生成通知" else "已关闭，不会生成任何通知",
                checked = masterEnabled,
                onCheckedChange = { checked ->
                    masterEnabled = checked
                    prefs.edit().putBoolean("master_enabled", checked).apply()
                }
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "第 1 步：授予「通知权限」\n\n第 2 步：授予「通知使用权」\n\n" +
                        "授权后，本 App 会读取 Spotify 当前的播放状态，并在通知栏里重新生成一条带完整按钮的通知。\n\n" +
                        "第 3 步：把本 App 加入省电策略白名单 / 允许自启动，否则 HyperOS 可能会在后台把它杀掉。",
                    modifier = Modifier.padding(16.dp)
                )
            }

            SwitchPreference(
                title = "通知权限",
                summary = if (notificationGranted) "已授权" else "未授权，点击开启",
                checked = notificationGranted,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationGranted = true
                        }
                    } else {
                        // Android 不允许 App 自己收回已授予的权限，跳到系统设置页让用户手动关闭
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                }
            )

            TextButton(
                text = "打开通知使用权设置",
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            )

            Text(text = "日志", style = MiuixTheme.textStyles.title3)

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
