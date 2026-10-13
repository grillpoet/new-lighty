package com.grillpoet.lighty.player

import kotlinx.coroutines.flow.StateFlow

/**
 * 播放引擎接口。
 *
 * 音频由 AndroidPlayer 实现，未来视频由 VideoEngine 实现。
 * PlaybackService 只认识这个接口，不关心是音频还是视频。
 */
interface PlaybackEngine {

    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>

    /** 播放新 URL（会先停止当前的） */
    fun play(url: String)

    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)

    /** 释放资源，Service 销毁时调用 */
    fun release()
}