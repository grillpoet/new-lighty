package com.grillpoet.lighty.playback

import kotlinx.serialization.json.Json

/**
 * 从 record["episodes_json"] 解析分集列表。
 * 返回空列表表示解析失败或没有分集。
 */
object EpisodeParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(jsonText: String?): List<Episode> {
        if (jsonText.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<Episode>>(jsonText)
        }.getOrDefault(emptyList())
    }
}