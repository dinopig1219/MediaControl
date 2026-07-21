package com.example.mediacontrol

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager

/**
 * 通知里每个按钮的 PendingIntent 都指向这里。
 * 重新拿一次 active session，再把动作转发给真正的 MediaController（比如 Spotify）。
 * repeat / like 走 ACTION_CUSTOM，用 sendCustomAction 把原本的 action id 发回去。
 */
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
            ACTION_CUSTOM -> {
                val customAction = intent.getStringExtra(EXTRA_CUSTOM_ACTION) ?: return
                transport.sendCustomAction(customAction, null)
            }
        }
    }
}
