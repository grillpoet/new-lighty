package com.grillpoet.lighty.ui.vm

import androidx.lifecycle.ViewModel
import com.grillpoet.lighty.player.VideoEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VideoPlayerUiState(
    val isFullscreen: Boolean = false
)

/**
 * 视频播放器 VM：只负责 ExoPlayer 与全屏状态。
 * 内容（title / episodes / pages）由 ContentViewModel 管理。
 */
class VideoPlayerViewModel(
    private val videoEngine: VideoEngine
) : ViewModel() {

    private val _state = MutableStateFlow(VideoPlayerUiState())
    val state: StateFlow<VideoPlayerUiState> = _state.asStateFlow()

    val isPlaying: StateFlow<Boolean> = videoEngine.isPlaying
    val currentPosition: StateFlow<Long> = videoEngine.currentPosition
    val duration: StateFlow<Long> = videoEngine.duration
    val isBuffering: StateFlow<Boolean> = videoEngine.isBuffering

    val exoPlayer get() = videoEngine.exoPlayer

    fun play(url: String) = videoEngine.play(url)
    fun pause() = videoEngine.pause()
    fun resume() = videoEngine.resume()

    fun togglePlayPause() {
        if (videoEngine.isPlaying.value) videoEngine.pause()
        else videoEngine.resume()
    }

    fun seekTo(ms: Long) = videoEngine.seekTo(ms)
    fun stop() = videoEngine.stop()

    fun setFullscreen(v: Boolean) {
        _state.update { it.copy(isFullscreen = v) }
    }

    override fun onCleared() {
        videoEngine.stop()
    }
}
