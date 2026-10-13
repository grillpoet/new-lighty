package com.grillpoet.lighty.data

/**
 * 媒体类型。属于业务概念，不属于 flow 层。
 * flow 层只认字符串 tag（"music" / "video" / "novel" / "manga"）。
 */
enum class MediaKind {
    MUSIC, VIDEO, NOVEL, IMAGE;

    /** 传给 flow 的类型标签 */
    val typeTag: String
        get() = when (this) {
            MUSIC -> "music"
            VIDEO -> "video"
            NOVEL -> "novel"
            IMAGE -> "manga"
        }

    companion object {
        fun parse(s: String?): MediaKind =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: MUSIC
    }
}