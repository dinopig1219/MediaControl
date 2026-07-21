package com.dinopig.mediacontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class MediaControlListenerService : NotificationListenerService() {

    companion object {
        const val CHANNEL_ID = "media_control_patch"
        const val NOTIFICATION_ID = 9001

        // 要监听哪些 App 的媒体会话。要支持别的 App（网易云/QQ音乐等）就把包名加进来。
        val TARGET_PACKAGES = setOf("com.spotify.music")
    }

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = updateNotification()
        override fun onMetadataChanged(metadata: MediaMetadata?) = updateNotification()
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
            // 理论上走不到这里：能触发 onListenerConnected 说明权限已经给了
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
    }

    // NotificationListenerService 强制要求实现，这里用不到通知内容本身
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun pickController(controllers: List<MediaController>?) {
        activeController?.unregisterCallback(controllerCallback)
        val target = controllers?.firstOrNull { it.packageName in TARGET_PACKAGES }
        activeController = target
        target?.registerCallback(controllerCallback)
        updateNotification()
    }

    private fun updateNotification() {
        val controller = activeController
        val state = controller?.playbackState

        if (controller == null || state == null || state.state == PlaybackState.STATE_NONE) {
            cancelNotification()
            return
        }

        val metadata = controller.metadata
        val isPlaying = state.state == PlaybackState.STATE_PLAYING

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "正在播放")
            .setContentText(metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "")
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { builder.setLargeIcon(it) }

        builder.addAction(standardAction(android.R.drawable.ic_media_previous, "上一首", MediaActionReceiver.ACTION_SKIP_PREV))
        builder.addAction(
            if (isPlaying) standardAction(android.R.drawable.ic_media_pause, "暂停", MediaActionReceiver.ACTION_PAUSE)
            else standardAction(android.R.drawable.ic_media_play, "播放", MediaActionReceiver.ACTION_PLAY)
        )
        builder.addAction(standardAction(android.R.drawable.ic_media_next, "下一首", MediaActionReceiver.ACTION_SKIP_NEXT))

        // 这里就是被 HyperOS 吃掉的按钮：repeat / like 等，通过 PlaybackState 的 custom actions 拿回来
        state.customActions?.forEach { builder.addAction(customAction(it)) }

        val compactIndices = listOf(0, 1, 2).let { base ->
            if (state.customActions?.isNotEmpty() == true) base + 3 else base
        }.take(3).toIntArray()

        builder.setStyle(MediaStyle().setShowActionsInCompactView(*compactIndices))

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, builder.build())
    }

    private fun standardAction(icon: Int, title: String, action: String): NotificationCompat.Action {
        val intent = Intent(this, MediaActionReceiver::class.java).apply { this.action = action }
        val pi = PendingIntent.getBroadcast(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(icon, title, pi).build()
    }

    private fun customAction(customAction: PlaybackState.CustomAction): NotificationCompat.Action {
        val intent = Intent(this, MediaActionReceiver::class.java).apply {
            action = MediaActionReceiver.ACTION_CUSTOM
            putExtra(MediaActionReceiver.EXTRA_CUSTOM_ACTION, customAction.action)
        }
        val pi = PendingIntent.getBroadcast(
            this, customAction.action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(customAction.icon, customAction.name, pi).build()
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "媒体控制补丁", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
