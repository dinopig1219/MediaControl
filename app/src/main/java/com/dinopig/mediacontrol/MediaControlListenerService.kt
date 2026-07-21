package com.dinopig.mediacontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.Rating
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
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
    }

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
        val actionsBitmask = state.actions

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "正在播放")
            .setContentText(metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "")
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { builder.setLargeIcon(it) }

        // 标准三键
        builder.addAction(standardAction(android.R.drawable.ic_media_previous, "上一首", MediaActionReceiver.ACTION_SKIP_PREV))
        builder.addAction(
            if (isPlaying) standardAction(android.R.drawable.ic_media_pause, "暂停", MediaActionReceiver.ACTION_PAUSE)
            else standardAction(android.R.drawable.ic_media_play, "播放", MediaActionReceiver.ACTION_PLAY)
        )
        builder.addAction(standardAction(android.R.drawable.ic_media_next, "下一首", MediaActionReceiver.ACTION_SKIP_NEXT))

        // 循环：Spotify 是走 ACTION_SET_REPEAT_MODE，不是 custom action
        if (actionsBitmask and PlaybackState.ACTION_SET_REPEAT_MODE != 0L) {
            val label = when (controller.repeatMode) {
                PlaybackState.REPEAT_MODE_ONE -> "循环: 单曲"
                PlaybackState.REPEAT_MODE_ALL, PlaybackState.REPEAT_MODE_GROUP -> "循环: 全部"
                else -> "循环: 关闭"
            }
            builder.addAction(standardAction(android.R.drawable.ic_popup_sync, label, MediaActionReceiver.ACTION_TOGGLE_REPEAT))
        }

        // 随机播放
        if (actionsBitmask and PlaybackState.ACTION_SET_SHUFFLE_MODE != 0L) {
            val shuffleOn = controller.shuffleMode != PlaybackState.SHUFFLE_MODE_NONE
            builder.addAction(
                standardAction(
                    android.R.drawable.ic_popup_sync,
                    if (shuffleOn) "随机: 开" else "随机: 关",
                    MediaActionReceiver.ACTION_TOGGLE_SHUFFLE
                )
            )
        }

        // like：Spotify 走 ACTION_SET_RATING + Rating 对象，不是 custom action
        if (actionsBitmask and PlaybackState.ACTION_SET_RATING != 0L) {
            val currentRating = metadata?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
            val loved = when (controller.ratingType) {
                Rating.RATING_HEART -> currentRating?.hasHeart() == true
                Rating.RATING_THUMB_UP_DOWN -> currentRating?.isRated == true && currentRating.isThumbUp
                else -> false
            }
            builder.addAction(
                standardAction(
                    if (loved) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off,
                    if (loved) "已喜欢" else "喜欢",
                    MediaActionReceiver.ACTION_TOGGLE_LIKE
                )
            )
        }

        // 保留：以防其它 App 是走 custom actions 这条路
        state.customActions?.forEach { builder.addAction(customAction(it)) }

        builder.setStyle(MediaStyle().setShowActionsInCompactView(0, 1, 2))

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
