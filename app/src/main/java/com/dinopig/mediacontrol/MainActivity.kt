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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixTheme(
                colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                RootScreen()
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

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

@Composable
private fun RootScreen() {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    val homeScrollBehavior = MiuixScrollBehavior()
    val aboutScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            if (pagerState.currentPage == 0) {
                TopAppBar(title = "媒体控制通知", scrollBehavior = homeScrollBehavior)
            } else {
                TopAppBar(title = "关于", scrollBehavior = aboutScrollBehavior)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    icon = Icons.Default.Home,
                    label = "主页"
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    icon = Icons.Default.Info,
                    label = "关于"
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            if (page == 0) HomeScreen(homeScrollBehavior) else AboutScreen(aboutScrollBehavior)
        }
    }
}

@Composable
private fun HomeScreen(scrollBehavior: MiuixScrollBehavior) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("debug_info", Context.MODE_PRIVATE) }

    var notificationGranted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var notificationAskedBefore by remember {
        mutableStateOf(prefs.getBoolean("notification_permission_asked", false))
    }
    var masterEnabled by remember {
        mutableStateOf(prefs.getBoolean("master_enabled", false) && isNotificationPermissionGranted(context))
    }
    var debugNotificationsOn by remember {
        mutableStateOf(prefs.getBoolean("debug_notifications_enabled", false))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationGranted = granted }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = isNotificationPermissionGranted(context)
                if (!notificationGranted && masterEnabled) {
                    masterEnabled = false
                    prefs.edit().putBoolean("master_enabled", false).apply()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SmallTitle(text = "开关", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "服务总开关",
                summary = when {
                    !notificationGranted -> "需要先开启「通知权限」才能启用"
                    masterEnabled -> "正在运行，Spotify 播放时会生成通知"
                    else -> "已关闭，不会生成任何通知"
                },
                checked = masterEnabled,
                onCheckedChange = { checked ->
                    if (checked && !notificationGranted) {
                        if (!notificationAskedBefore) {
                            notificationAskedBefore = true
                            prefs.edit().putBoolean("notification_permission_asked", true).apply()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            openAppDetailsSettings(context)
                        }
                    } else {
                        masterEnabled = checked
                        prefs.edit().putBoolean("master_enabled", checked).apply()
                    }
                }
            )
        }

        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(
                text = "第 1 步：授予「通知权限」\n\n第 2 步：授予「通知使用权」\n\n" +
                    "授权后，本 App 会读取 Spotify 当前的播放状态，并在通知栏里重新生成一条带完整按钮的通知。\n\n" +
                    "第 3 步：把本 App 加入省电策略白名单 / 允许自启动，否则 HyperOS 可能会在后台把它杀掉。",
                modifier = Modifier.padding(16.dp)
            )
        }

        SmallTitle(text = "通知", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = "通知权限",
                    summary = if (notificationGranted) "已授权" else "未授权，点击开启",
                    checked = notificationGranted,
                    onCheckedChange = {
                        if (!notificationAskedBefore) {
                            notificationAskedBefore = true
                            prefs.edit().putBoolean("notification_permission_asked", true).apply()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notificationGranted = true
                            }
                        } else {
                            openAppDetailsSettings(context)
                        }
                    }
                )

                TextButton(
                    text = "打开通知使用权设置",
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }

        SmallTitle(text = "日志", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
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
                    text = "查看调试信息",
                    onClick = { context.startActivity(Intent(context, DebugActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutScreen(scrollBehavior: MiuixScrollBehavior) {
    val context = LocalContext.current
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionName = packageInfo.versionName ?: "未知"
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "媒体控制通知", style = MiuixTheme.textStyles.title3)
                Text(text = "版本 $versionName ($versionCode)")
                Text(text = "用一条独立通知，把 HyperOS 锁屏卡片裁剪掉的 Spotify 控件重新显示出来。")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                text = "查看 GitHub 仓库",
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dinopig1219/MediaControlNotification"))
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }
}
