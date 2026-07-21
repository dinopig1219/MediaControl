package com.dinopig.mediacontrol

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat

class MediaActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY = "com.dinopig.mediacontrol.ACTION_PLAY"
        const val ACTION_PAUSE = "com.dinopig.mediacontrol.ACTION_PAUSE"
        const val ACTION_SKIP_NEXT = "com.dinopig.mediacontrol.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.dinopig.mediacontrol.ACTION_SKIP_PREV"
        const val ACTION_CUSTOM = "com.dinopig.mediacontrol.ACTION_CUSTOM"
        const val EXTRA_CUSTOM_ACTION = "extra_custom_action"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(context, MediaControlListenerService::class.java)

        val frameworkController = try {
            manager.getActiveSessions(component)
                .firstOrNull { it.packageName in MediaControlListenerService.TARGET_PACKAGES }
        } catch (e: SecurityException) {
            null
        } ?: return

        val controller = MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(frameworkController.sessionToken))
        val transport = controller.transportControls

        when (intent.action) {
            ACTION_PLAY -> transport.play()
            ACTION_PAUSE -> transport.pause()
            ACTION_SKIP_NEXT -> transport.skipToNext()
            ACTION_SKIP_PREV -> transport.skipToPrevious()
            ACTION_CUSTOM -> {
                val customAction = intent.getStringExtra(EXTRA_CUSTOM_ACTION) ?: return
                transport.sendCustomAction(customAction, null)
            }
        }
    }
}
