package com.dinopig.mediacontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media.app.NotificationCompat.MediaStyle

class MediaControlListenerService : NotificationListenerService() {

    companion object {
        const val CHANNEL_ID = "media_control_patch"
        const val NOTIFICATION_ID = 9001
        const val DEBUG_NOTIFICATION_ID = 9002
        val TARGET_PACKAGES = setOf("com.spotify.music")
    }

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeController: MediaControllerCompat? = null
    private var activePackageName: String = ""

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = updateNotification()
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) = updateNotification()
        override fun onSessionDestroyed() {
            activeController = null
            cancelNotification()
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers -> pickController(controllers) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val component = ComponentName(this, MediaControlListenerService::class.java)
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            pickController(mediaSessionManager.getActiveSessions(component))
        } catch (e: SecurityException) {
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun pickController(controllers: List<android.media.session.MediaController>?) {
        activeController?.unregisterCallback(controllerCallback)
        val target = controllers?.firstOrNull { it.packageName in TARGET_PACKAGES }
        activePackageName = target?.packageName ?: ""
        activeController = target?.let {
            MediaControllerCompat(this, MediaSessionCompat.Token.fromToken(it.sessionToken))
        }
        activeController?.registerCallback(controllerCallback)
        updateNotification()
    }

    private fun updateNotification() {
        val controller = activeController
        val state = controller?.playbackState

        if (controller == null || state == null || state.state == PlaybackStateCompat.STATE_NONE) {
            cancelNotification()
            return
        }

        val metadata = controller.metadata
        val isPlaying = state.state == PlaybackStateCompat.STATE_PLAYING

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "正在播放")
            .setContentText(metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "")
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)?.let { builder.setLargeIcon(it) }

        builder.addAction(standardAction(R.drawable.ic_thin_previous, "上一首", MediaActionReceiver.ACTION_SKIP_PREV))
        builder.addAction(
            if (isPlaying) standardAction(R.drawable.ic_thin_pause, "暂停", MediaActionReceiver.ACTION_PAUSE)
            else standardAction(R.drawable.ic_thin_play, "播放", MediaActionReceiver.ACTION_PLAY)
        )
        builder.addAction(standardAction(R.drawable.ic_thin_next, "下一首", MediaActionReceiver.ACTION_SKIP_NEXT))

        // 直接把 Spotify 自己给的 custom actions（含它自己的图标）原样渲染出来，
        // 状态由 Spotify 自己维护，我们不猜测、不自己画状态。
        state.customActions?.forEach { builder.addAction(customAction(it)) }

        val customCount = state.customActions?.size ?: 0
        val compactIndices = (0 until minOf(3, 3 + customCount)).toList().take(3).toIntArray()
        builder.setStyle(MediaStyle().setShowActionsInCompactView(*compactIndices))

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, builder.build())

        // 完整调试信息一直存起来，App 里的 DebugActivity 随时能看到最新的
        saveDebugInfo(state)

        // 调试通知本身要不要发，看用户在 App 里那个开关
        val debugNotificationsOn = getSharedPreferences("debug_info", Context.MODE_PRIVATE)
            .getBoolean("debug_notifications_enabled", false)
        if (debugNotificationsOn) {
            showDebugNotification(state)
        } else {
            getSystemService(NotificationManager::class.java).cancel(DEBUG_NOTIFICATION_ID)
        }
    }

    private fun buildDebugText(state: PlaybackStateCompat): String {
        val sb = StringBuilder()
        sb.append("更新时间: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}\n\n")
        sb.append("包名: $activePackageName\n")
        sb.append("actions bitmask: ${state.actions}\n")
        sb.append("customActions:\n")
        if (state.customActions.isNullOrEmpty()) {
            sb.append("  (无)\n")
        } else {
            state.customActions.forEach {
                sb.append("  name=${it.name}\n  action=${it.action}\n  icon=${it.icon}\n\n")
            }
        }
        return sb.toString()
    }

    private fun saveDebugInfo(state: PlaybackStateCompat) {
        getSharedPreferences("debug_info", Context.MODE_PRIVATE)
            .edit()
            .putString("last_debug_info", buildDebugText(state))
            .apply()
    }

    private fun showDebugNotification(state: PlaybackStateCompat) {
        val debugBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("调试信息")
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildDebugText(state)))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)

        getSystemService(NotificationManager::class.java).notify(DEBUG_NOTIFICATION_ID, debugBuilder.build())
    }

    private fun standardAction(icon: Int, title: String, action: String): NotificationCompat.Action {
        val intent = Intent(this, MediaActionReceiver::class.java).apply { this.action = action }
        val pi = PendingIntent.getBroadcast(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(icon, title, pi).build()
    }

    private fun customAction(customAction: PlaybackStateCompat.CustomAction): NotificationCompat.Action {
        val intent = Intent(this, MediaActionReceiver::class.java).apply {
            action = MediaActionReceiver.ACTION_CUSTOM
            putExtra(MediaActionReceiver.EXTRA_CUSTOM_ACTION, customAction.action)
        }
        val pi = PendingIntent.getBroadcast(
            this, customAction.action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = try {
            val remoteResources = packageManager.getResourcesForApplication(activePackageName)
            IconCompat.createWithResource(remoteResources, activePackageName, customAction.icon)
        } catch (e: Exception) {
            IconCompat.createWithResource(resources, packageName, android.R.drawable.ic_menu_help)
        }
        return NotificationCompat.Action.Builder(icon, customAction.name.toString(), pi).build()
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        getSystemService(NotificationManager::class.java).cancel(DEBUG_NOTIFICATION_ID)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "媒体控制通知", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
