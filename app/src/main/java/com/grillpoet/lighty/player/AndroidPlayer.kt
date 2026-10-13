package com.grillpoet.lighty.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.grillpoet.lighty.core.DebugLog

class AndroidPlayer(context: Context) : PlaybackEngine {

    private companion object {
        const val TAG = "AndroidPlayer"
        const val TICK_MS = 200L
    }

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var tickJob: Job? = null

    private var currentUrl: String = ""

    private var focusRequest: AudioFocusRequest? = null
    private var pausedByFocusLoss = false

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    // ==================== 音频焦点 ====================

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
                pausedByFocusLoss = false
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (_isPlaying.value) {
                    pause()
                    pausedByFocusLoss = true
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocusLoss) {
                    resume()
                    pausedByFocusLoss = false
                }
            }
        }
    }

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(false)
            .build()
        focusRequest = req
        val result = audioManager.requestAudioFocus(req)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // ==================== 播放控制 ====================

    override fun play(url: String) {
        if (!requestFocus()) {
            Log.w(TAG, "audio focus denied")
            return
        }
        currentUrl = url
        stopInternal()
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
        }
        try {
            mp.setDataSource(url)
            mp.setOnPreparedListener { player ->
                _duration.value = player.duration.coerceAtLeast(0).toLong()
                player.start()
                _isPlaying.value = true
                startTick()
            }
            mp.setOnCompletionListener {
                _isPlaying.value = false
                _currentPosition.value = _duration.value
                stopTick()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error: what=$what extra=$extra")
                DebugLog.error("MediaPlayer: what=$what extra=$extra\n${currentUrl.take(300)}")
                _isPlaying.value = false
                stopTick()
                true
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.w(TAG, "setDataSource failed", e)
            runCatching { mp.release() }
            mediaPlayer = null
            abandonFocus()
        }
    }

    override fun pause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            runCatching { mp.pause() }
            _isPlaying.value = false
            stopTick()
        }
    }

    override fun resume() {
        val mp = mediaPlayer ?: return
        if (!mp.isPlaying) {
            if (!requestFocus()) return
            runCatching { mp.start() }
            _isPlaying.value = true
            startTick()
        }
    }

    override fun stop() {
        stopInternal()
        abandonFocus()
        _currentPosition.value = 0L
        _duration.value = 0L
        _isPlaying.value = false
    }

    override fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        runCatching { mp.seekTo(positionMs.toInt()) }
        _currentPosition.value = positionMs
    }

    override fun release() {
        stop()
        scope.coroutineContext[Job.Key]?.cancel()
    }

    // ==================== 私有 ====================

    private fun stopInternal() {
        stopTick()
        mediaPlayer?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        mediaPlayer = null
        _isPlaying.value = false
    }

    private fun startTick() {
        stopTick()
        tickJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (runCatching { mp.isPlaying }.getOrDefault(false)) {
                        _currentPosition.value = mp.currentPosition.toLong()
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }
}