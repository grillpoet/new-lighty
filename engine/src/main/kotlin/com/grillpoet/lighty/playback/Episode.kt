package com.grillpoet.lighty.playback

import kotlinx.serialization.Serializable

/**
 * 分集 / 章节。
 *
 * 视频 / 听书：用 url 字段（单个 mp3 / m3u8）。
 * 漫画：用 pages 字段（多个图片 URL）。
 */
@Serializable
data class Episode(
    val name: String,
    val url: String = "",
    val cid: Long = 0,
    val pages: List<String> = emptyList()
) {
    val isManga: Boolean get() = pages.isNotEmpty()
}