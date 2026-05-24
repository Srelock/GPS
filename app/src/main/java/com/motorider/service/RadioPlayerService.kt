package com.motorider.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent
import android.content.pm.ServiceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.motorider.MainActivity
import com.motorider.R
import com.motorider.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@AndroidEntryPoint
class RadioPlayerService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private var player: ExoPlayer? = null
    private var lastTitle: String = "Radio stopped"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSessionCompat

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
        // Media session must exist before ExoPlayer: player callbacks call createNotification()
        // which reads mediaSession.sessionToken.
        initMediaSession()
        ensurePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            MediaButtonReceiver.handleIntent(mediaSession, intent)
            return if (isPlaybackActive()) START_STICKY else START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PLAY -> {
                val name = intent.getStringExtra(EXTRA_STATION_NAME) ?: "Radio"
                val url = intent.getStringExtra(EXTRA_STATION_URL)
                if (!url.isNullOrBlank()) {
                    promoteToForeground()
                    play(name = name, url = url)
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopPlaybackAndService()
        }

        return if (isPlaybackActive()) START_STICKY else START_NOT_STICKY
    }

    private fun isPlaybackActive(): Boolean {
        val state = player?.playbackState
        return player?.isPlaying == true ||
            state == Player.STATE_BUFFERING ||
            state == Player.STATE_READY
    }

    private fun initMediaSession() {
        // Build session in a local val first: calling setPlaybackStateCompat during `.apply { }`
        // would still read lateinit mediaSession before this assignment completes (crash on Android).
        val session = MediaSessionCompat(
            this,
            "RadioPlayerService",
            ComponentName(this, androidx.media.session.MediaButtonReceiver::class.java),
            null
        ).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    // If we already have a playing item, just resume. Otherwise play selected station.
                    if (player?.currentMediaItem != null) {
                        player?.play()
                        setPlaybackStateCompat(isPlaying = true)
                        updateNotification()
                        return
                    }
                    playSelectedStationOrFirst()
                }

                override fun onPause() {
                    pause()
                    setPlaybackStateCompat(isPlaying = false)
                }

                override fun onStop() {
                    stopPlaybackAndService()
                }

                override fun onSkipToNext() {
                    playRelativeStation(delta = 1)
                }

                override fun onSkipToPrevious() {
                    playRelativeStation(delta = -1)
                }
            })

            isActive = true
        }
        mediaSession = session
        setPlaybackStateCompat(isPlaying = false)
    }

    private fun setPlaybackStateCompat(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )
    }

    private fun playSelectedStationOrFirst() {
        val stations = settingsRepository.radioStations.value
        if (stations.isEmpty()) return
        val selectedId = settingsRepository.selectedStationId.value
        val station = stations.firstOrNull { it.id == selectedId } ?: stations.first()
        playStation(station)
    }

    private fun playRelativeStation(delta: Int) {
        val stations = settingsRepository.radioStations.value
        if (stations.isEmpty()) return

        val selectedId = settingsRepository.selectedStationId.value
        val currentIndex = stations.indexOfFirst { it.id == selectedId }
        val safeIndex = if (currentIndex == -1) 0 else currentIndex
        val nextIndex = (safeIndex + delta + stations.size) % stations.size

        playStation(stations[nextIndex])
    }

    private fun playStation(station: SettingsRepository.RadioStation) {
        settingsRepository.setSelectedStation(station.id)
        play(name = station.name, url = station.url)
    }

    private fun ensurePlayer() {
        if (player != null) return
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (::mediaSession.isInitialized) {
                        updateNotification()
                        setPlaybackStateCompat(isPlaying = isPlaying)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (::mediaSession.isInitialized) {
                        updateNotification()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("RadioPlayerService", "ExoPlayer Error: ${error.message}", error)
                    lastTitle = "Error: ${error.errorCodeName}"
                    if (::mediaSession.isInitialized) {
                        updateNotification()
                    }
                }
            })
        }
    }

    private fun promoteToForeground() {
        startForegroundTyped(
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private fun play(name: String, url: String) {
        ensurePlayer()
        lastTitle = "Loading: $name"
        promoteToForeground()

        serviceScope.launch {
            val resolvedUrl = resolveStreamUrl(url)

            lastTitle = "Playing: $name"

            val mediaItem = MediaItem.Builder()
                .setUri(resolvedUrl)
                .setMediaId(resolvedUrl)
                .setTag(name)
                .build()

            player?.apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }

            setPlaybackStateCompat(isPlaying = true)
            updateNotification()
        }
    }

    private suspend fun resolveStreamUrl(url: String): String {
        var resolved = url
        val looksLikePls =
            url.contains(".pls", ignoreCase = true) || url.contains("playlists/", ignoreCase = true)
        if (looksLikePls) {
            resolvePlsUrl(url)?.let { resolved = it }
        }
        return refreshStreamAuth(resolved)
    }

    private suspend fun resolvePlsUrl(plsUrl: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(plsUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MotoRider/1.0 (Android)")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            connection.inputStream.bufferedReader().use { reader ->
                reader.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("File1=", ignoreCase = true) }
                    ?.substringAfter("=", missingDelimiterValue = "")
                    ?.trim()
                    ?.takeIf { it.startsWith("http", ignoreCase = true) }
            }
        }.getOrNull()
    }

    /**
     * Bauer / Global streams use a short-lived epoch key; refresh it on every play.
     */
    private fun refreshStreamAuth(url: String): String {
        val needsSkey = listOf("hellorayo.co.uk", "planetradio.co.uk", "sharp-stream.com")
            .any { host -> url.contains(host, ignoreCase = true) }
        if (!needsSkey) return url

        val epoch = (System.currentTimeMillis() / 1000).toString()
        val skeyParam = Regex("aw_0_1st\\.skey=[^&]*", RegexOption.IGNORE_CASE)
        return if (skeyParam.containsMatchIn(url)) {
            url.replace(skeyParam, "aw_0_1st.skey=$epoch")
        } else {
            val separator = if (url.contains("?")) "&" else "?"
            "$url${separator}aw_0_1st.skey=$epoch"
        }
    }

    private fun pause() {
        player?.pause()
        lastTitle = "Paused"
        if (::mediaSession.isInitialized) {
            setPlaybackStateCompat(isPlaying = false)
        }
        updateNotification()
    }

    private fun stopPlaybackAndService() {
        player?.stop()
        lastTitle = "Radio stopped"
        if (::mediaSession.isInitialized) {
            setPlaybackStateCompat(isPlaying = false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        if (!::mediaSession.isInitialized) return
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

        val nextPendingIntent = buildMediaButtonPendingIntent(
            this,
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
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
                android.R.drawable.ic_media_next,
                "Next",
                nextPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
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
        serviceScope.cancel()
        player?.release()
        player = null
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
    }
}

