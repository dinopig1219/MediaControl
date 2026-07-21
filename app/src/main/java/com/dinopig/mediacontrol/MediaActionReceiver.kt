package com.dinopig.mediacontrol

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

class MediaActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY = "com.dinopig.mediacontrol.ACTION_PLAY"
        const val ACTION_PAUSE = "com.dinopig.mediacontrol.ACTION_PAUSE"
        const val ACTION_SKIP_NEXT = "com.dinopig.mediacontrol.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.dinopig.mediacontrol.ACTION_SKIP_PREV"
        const val ACTION_TOGGLE_REPEAT = "com.dinopig.mediacontrol.ACTION_TOGGLE_REPEAT"
        const val ACTION_TOGGLE_SHUFFLE = "com.dinopig.mediacontrol.ACTION_TOGGLE_SHUFFLE"
        const val ACTION_TOGGLE_LIKE = "com.dinopig.mediacontrol.ACTION_TOGGLE_LIKE"
        const val ACTION_CUSTOM = "com.dinopig.mediacontrol.ACTION_CUSTOM"
        const val EXTRA_CUSTOM_ACTION = "extra_custom_action"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(context, MediaControlListenerService::class.java)

        val controller = try {
            manager.getActiveSessions(component)
                .firstOrNull { it.packageName in MediaControlListenerService.TARGET_PACKAGES }
        } catch (e: SecurityException) {
            null
        } ?: return

        val transport = controller.transportControls
        when (intent.action) {
            ACTION_PLAY -> transport.play()
            ACTION_PAUSE -> transport.pause()
            ACTION_SKIP_NEXT -> transport.skipToNext()
            ACTION_SKIP_PREV -> transport.skipToPrevious()

            ACTION_TOGGLE_REPEAT -> {
                val next = when (controller.repeatMode) {
                    PlaybackState.REPEAT_MODE_NONE -> PlaybackState.REPEAT_MODE_ALL
                    PlaybackState.REPEAT_MODE_ALL, PlaybackState.REPEAT_MODE_GROUP -> PlaybackState.REPEAT_MODE_ONE
                    else -> PlaybackState.REPEAT_MODE_NONE
                }
                transport.setRepeatMode(next)
            }

            ACTION_TOGGLE_SHUFFLE -> {
                val next = if (controller.shuffleMode == PlaybackState.SHUFFLE_MODE_NONE)
                    PlaybackState.SHUFFLE_MODE_ALL else PlaybackState.SHUFFLE_MODE_NONE
                transport.setShuffleMode(next)
            }

            ACTION_TOGGLE_LIKE -> {
                val current = controller.metadata?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
                when (controller.ratingType) {
                    Rating.RATING_HEART -> {
                        val loved = current?.hasHeart() == true
                        transport.setRating(Rating.newHeartRating(!loved))
                    }
                    Rating.RATING_THUMB_UP_DOWN -> {
                        val up = current?.isRated == true && current.isThumbUp
                        transport.setRating(Rating.newThumbRating(!up))
                    }
                    else -> { /* 不支持评分类型，忽略 */ }
                }
            }

            ACTION_CUSTOM -> {
                val customAction = intent.getStringExtra(EXTRA_CUSTOM_ACTION) ?: return
                transport.sendCustomAction(customAction, null)
            }
        }
    }
}
