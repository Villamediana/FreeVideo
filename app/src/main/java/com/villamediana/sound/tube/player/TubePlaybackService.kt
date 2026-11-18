package com.villamediana.sound.tube.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.villamediana.sound.MainActivity
import com.villamediana.sound.SoundApplication

/**
 * "Tocar em segundo plano": a media session around [TubePlayer]'s ExoPlayer, so the video's sound
 * goes on with the app closed, with play/pause in the notification, the lock screen and headphones.
 * Started only when that setting is on (while the app is on screen); stopped when the player closes.
 */
@OptIn(UnstableApi::class)
class TubePlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = SoundApplication.graph.tube.player.exoPlayer()
        if (player == null) {
            stopSelf()
            return
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away from Recents: stop for good.
        SoundApplication.graph.tube.player.close()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            // Only while the app is on screen (Android refuses background starts): that's when a video opens.
            runCatching { context.startService(Intent(context, TubePlaybackService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TubePlaybackService::class.java)) }
        }
    }
}
