package com.grillpoet.lighty.core

import com.grillpoet.lighty.core.model.LyricLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 多格式歌词解析。
 * 依次尝试：
 *   1. LRC 纯文本
 *   2. 酷我 JSON（data.lrclist）
 *   3. 网易云 JSON（lyric）
 * 全部失败返回空列表。
 */
object LyricParser {

    private val lrcRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()

        // 1. LRC 纯文本
        val lrc = parseLrc(raw)
        if (lrc.isNotEmpty()) return lrc

        // 2. JSON 格式
        return runCatching { parseJson(raw) }.getOrDefault(emptyList())
    }

    // ---------- LRC ----------

    private fun parseLrc(text: String): List<LyricLine> {
        if (!text.contains('[')) return emptyList()
        val result = ArrayList<LyricLine>(64)
        for (line in text.lineSequence()) {
            val match = lrcRegex.find(line) ?: continue
            val minutes = match.groupValues[1].toIntOrNull() ?: continue
            val seconds = match.groupValues[2].toIntOrNull() ?: continue
            val millis = parseMillis(match.groupValues.getOrNull(3).orEmpty())
            val lyric = line.replace(lrcRegex, "").trim()
            if (lyric.isEmpty()) continue
            result += LyricLine(
                timestamp = (minutes * 60L + seconds) * 1000L + millis,
                text = lyric
            )
        }
        return result.sortedBy { it.timestamp }
    }

    private fun parseMillis(s: String): Long = when (s.length) {
        0 -> 0L
        1 -> s.toLong() * 100
        2 -> s.toLong() * 10
        else -> s.take(3).toLong()
    }

    // ---------- JSON ----------

    private fun parseJson(raw: String): List<LyricLine> {
        val root = json.parseToJsonElement(raw).jsonObject

        // 酷我：data.lrclist
        val lrcList = root["data"]
            ?.jsonObject
            ?.get("lrclist")
            ?.jsonArray
        if (lrcList != null) {
            return lrcList.mapNotNull { elem ->
                val obj = elem.jsonObject
                val text = obj["lineLyric"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val sec = obj["time"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                LyricLine((sec * 1000).toLong(), text)
            }.sortedBy { it.timestamp }
        }

        // 网易云：lyric
        val lyricText = root["lyric"]?.jsonPrimitive?.content
        if (!lyricText.isNullOrBlank()) return parseLrc(lyricText)

        return emptyList()
    }
}