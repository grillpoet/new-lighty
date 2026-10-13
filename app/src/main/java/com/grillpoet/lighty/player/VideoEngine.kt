package com.grillpoet.lighty.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import com.grillpoet.lighty.core.DebugLog
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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

/**
 * 视频播放引擎（ExoPlayer 实现）。
 *
 * 支持 mp4 / m3u8 / HLS 等格式。
 *
 * 不设置固定 Referer：不同源的 CDN 需求不同，
 * 带错误 Referer 反而可能被拒。UA 保留，大部分 CDN 接受浏览器 UA。
 */
class VideoEngine(context: Context) : PlaybackEngine {

    private companion object {
        const val TICK_MS = 200L
        const val TAG = "VideoEngine"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /** 数据源：只带 UA，不带 Referer */
    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(mapOf("User-Agent" to UA))

    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    /** 暴露给 UI 层绑定 PlayerView */
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()


    private var currentUrl: String = ""
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var tickJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    /** 是否正在缓冲（加载中）。UI 用它显示转圈 */
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startTick() else stopTick()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                    }
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        _isPlaying.value = false
                        stopTick()
                    }
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val msg = "ExoPlayer: ${error.errorCodeName}, " +
                        "cause=${error.cause?.message}"
                android.util.Log.e(TAG, msg, error)
                DebugLog.error("$msg\n${currentUrl.take(300)}")
                _isBuffering.value = false
            }
        })
    }

    override fun play(url: String) {
        android.util.Log.d(TAG, "play url=${url.take(150)}")
        currentUrl = url
        _isBuffering.value = true
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun resume() {
        exoPlayer.play()
    }

    override fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _isBuffering.value = false
        stopTick()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    override fun release() {
        stopTick()
        exoPlayer.release()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun startTick() {
        stopTick()
        tickJob = scope.launch {
            while (isActive) {
                _currentPosition.value = exoPlayer.currentPosition
                delay(TICK_MS)
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }
}