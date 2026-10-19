package com.beans.music

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class BeansPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var backScreenEngine: BackScreenMusicEngine? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val tickerRunnable = object : Runnable {
        override fun run() {
            mediaSession?.player?.let { p ->
                val sessionId = if (p is ExoPlayer) p.audioSessionId else 0
                backScreenEngine?.updatePlaybackState(
                    playing = p.isPlaying,
                    positionMs = p.currentPosition.coerceAtLeast(0L),
                    totalDurationMs = p.duration.coerceAtLeast(0L),
                    audioSessionId = sessionId
                )
            }
            mainHandler.postDelayed(this, 100L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        backScreenEngine = BackScreenMusicEngine(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                backScreenEngine?.updatePlaybackState(
                    playing = isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    totalDurationMs = player.duration.coerceAtLeast(0L),
                    audioSessionId = player.audioSessionId
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                backScreenEngine?.updatePlaybackState(
                    playing = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    totalDurationMs = player.duration.coerceAtLeast(0L),
                    audioSessionId = player.audioSessionId
                )
            }
        })

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        mainHandler.post(tickerRunnable)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(tickerRunnable)
        backScreenEngine?.release()
        backScreenEngine = null

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
