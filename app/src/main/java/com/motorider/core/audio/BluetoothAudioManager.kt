package com.motorider.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio announcements for motorcycle intercoms (Sena, Cardo, etc.)
 * 
 * Features:
 * - Text-to-Speech for speed warnings and weather alerts
 * - Bluetooth audio routing for helmet intercoms
 * - Audio focus management for system integration
 * - Announcement queuing and rate limiting
 */
@Singleton
class BluetoothAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var textToSpeech: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    // Rate limiting to avoid spam
    private var lastAnnouncementTime: Long = 0
    private val minAnnouncementIntervalMs = 10_000L // 10 seconds minimum between announcements
    
    // Announcement queue
    private val announcementQueue = mutableListOf<String>()
    
    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initializeTts()
    }
    
    /**
     * Initialize Text-to-Speech engine.
     */
    private fun initializeTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.let { tts ->
                    // Set language
                    val result = tts.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || 
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // Fall back to default locale
                        tts.setLanguage(Locale.getDefault())
                    }
                    
                    // Configure for Bluetooth audio
                    tts.setSpeechRate(1.1f) // Slightly faster for brief announcements
                    tts.setPitch(1.0f)
                    
                    // Set audio attributes for media playback (routes to Bluetooth)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        tts.setAudioAttributes(audioAttributes)
                    }
                    
                    // Set progress listener
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }
                        
                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            releaseAudioFocus()
                            processNextAnnouncement()
                        }
                        
                        @Deprecated("Deprecated in API 21")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            releaseAudioFocus()
                        }
                        
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                            releaseAudioFocus()
                        }
                    })
                    
                    _isReady.value = true
                }
            }
        }
    }
    
    /**
     * Announce a speed warning.
     * 
     * @param currentSpeed Current speed in km/h
     * @param speedLimit Speed limit in km/h
     */
    fun announceSpeedWarning(currentSpeed: Int, speedLimit: Int) {
        val message = "Speed warning. Currently $currentSpeed kilometers per hour. Limit is $speedLimit."
        queueAnnouncement(message)
    }
    
    /**
     * Announce weather conditions.
     * 
     * @param description Weather description from WindCalculator
     */
    fun announceWeatherAlert(description: String) {
        queueAnnouncement(description)
    }
    
    /**
     * Announce trip statistics.
     * 
     * @param distanceKm Distance traveled in km
     * @param durationMinutes Trip duration in minutes
     */
    fun announceTripStats(distanceKm: Double, durationMinutes: Int) {
        val message = "Trip update. ${String.format("%.1f", distanceKm)} kilometers in $durationMinutes minutes."
        queueAnnouncement(message)
    }
    
    /**
     * Speak a custom message immediately.
     */
    fun speak(message: String, urgent: Boolean = false) {
        if (urgent) {
            // Skip queue for urgent messages
            speakInternal(message)
        } else {
            queueAnnouncement(message)
        }
    }
    
    /**
     * Queue an announcement with rate limiting.
     */
    private fun queueAnnouncement(message: String) {
        val now = System.currentTimeMillis()
        
        // Rate limiting
        if (now - lastAnnouncementTime < minAnnouncementIntervalMs && announcementQueue.isNotEmpty()) {
            // Replace queued message with newer one
            announcementQueue.clear()
            announcementQueue.add(message)
            return
        }
        
        announcementQueue.add(message)
        
        if (!_isSpeaking.value) {
            processNextAnnouncement()
        }
    }
    
    /**
     * Process the next announcement in queue.
     */
    private fun processNextAnnouncement() {
        if (announcementQueue.isEmpty()) return
        
        val message = announcementQueue.removeAt(0)
        speakInternal(message)
    }
    
    /**
     * Internal speak function with audio focus handling.
     */
    private fun speakInternal(message: String) {
        if (!_isReady.value) return
        
        val tts = textToSpeech ?: return
        
        // Request audio focus
        requestAudioFocus()
        
        lastAnnouncementTime = System.currentTimeMillis()
        
        val utteranceId = UUID.randomUUID().toString()
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    
    /**
     * Request audio focus for announcement.
     */
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { }
                .build()
            
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }
    
    /**
     * Release audio focus after announcement.
     */
    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }
    
    /**
     * Stop any current speech.
     */
    fun stop() {
        textToSpeech?.stop()
        announcementQueue.clear()
        _isSpeaking.value = false
    }
    
    /**
     * Check if Bluetooth audio is connected.
     */
    fun isBluetoothConnected(): Boolean {
        return audioManager?.isBluetoothA2dpOn == true || 
               audioManager?.isBluetoothScoOn == true
    }
    
    /**
     * Clean up resources.
     */
    fun destroy() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        _isReady.value = false
    }
}
