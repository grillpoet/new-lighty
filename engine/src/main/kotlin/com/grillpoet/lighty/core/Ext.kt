package com.grillpoet.lighty.core

/** 毫秒 → "mm:ss"，用于播放器进度显示 */
fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

/** 补全封面 URL 协议头，统一为 https */
fun normalizeCover(url: String): String = when {
    url.isBlank() -> ""
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
    else -> url
}