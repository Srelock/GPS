package com.motorider.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.motorider.MainActivity
import com.motorider.R

class RadioPlayerService : Service() {

    private var player: ExoPlayer? = null
    private var lastTitle: String = "Radio stopped"

    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "motorider_radio_channel"

        const val ACTION_PLAY = "com.motorider.action.RADIO_PLAY"
        const val ACTION_PAUSE = "com.motorider.action.RADIO_PAUSE"
        const val ACTION_STOP = "com.motorider.action.RADIO_STOP"

        const val EXTRA_STATION_NAME = "station_name"
        const val EXTRA_STATION_URL = "station_url"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ensurePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val name = intent.getStringExtra(EXTRA_STATION_NAME) ?: "Radio"
                val url = intent.getStringExtra(EXTRA_STATION_URL)
                if (!url.isNullOrBlank()) {
                    play(name = name, url = url)
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopPlaybackAndService()
        }

        // Keep service alive while playing/paused. Notification is updated by actions.
        return START_STICKY
    }

    private fun ensurePlayer() {
        if (player != null) return
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateNotification()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateNotification()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("RadioPlayerService", "ExoPlayer Error: ${error.message}", error)
                    lastTitle = "Error: ${error.errorCodeName}"
                    updateNotification()
                }
            })
        }
    }

    private fun play(name: String, url: String) {
        ensurePlayer()
        lastTitle = "Playing: $name"

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setTag(name)
            .build()

        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }

        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun pause() {
        player?.pause()
        lastTitle = "Paused"
        updateNotification()
    }

    private fun stopPlaybackAndService() {
        player?.stop()
        lastTitle = "Radio stopped"
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, RadioPlayerService::class.java).apply { action = ACTION_PAUSE }
        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RadioPlayerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPlaying = player?.isPlaying == true
        val playStateText = when {
            isPlaying -> "Playing"
            player?.playbackState == Player.STATE_BUFFERING -> "Buffering"
            player?.playbackState == Player.STATE_READY -> "Paused"
            else -> "Stopped"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("$playStateText • $lastTitle")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .setOngoing(isPlaying)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Web radio playback"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}

