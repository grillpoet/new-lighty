package com.grillpoet.lighty.flow.impl.ops

import com.grillpoet.lighty.core.DebugLog
import com.grillpoet.lighty.core.expr.ExpressionEngine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Base64

/**
 * 文本处理 op 集合。
 *
 * 从 data.action 迁来，另加以下 op：
 *   base64      —— 标准 Base64 解码
 *   jsArray     —— ['a','b'] → ["a","b"]
 *   base64Each  —— 对 JSON 数组里每个元素做 Base64 解码
 *   print       —— 打印到 DebugLog，不改变值
 *   eval        —— 用表达式引擎处理 value
 */
object TextOps {

    fun apply(value: String, ops: JsonArray?): String {
        if (ops == null || ops.isEmpty()) return value
        var result = value
        for (elem in ops) {
            val op = elem as? JsonObject ?: continue
            result = applyOne(result, op)
        }
        return result
    }

    private fun applyOne(value: String, op: JsonObject): String =
        when (op.str("op")) {
            "trim" -> value.trim()
            "trimPrefix" -> value.removePrefix(op.str("value").orEmpty())
            "trimSuffix" -> value.removeSuffix(op.str("value").orEmpty())
            "replace" -> value.replace(
                op.str("from").orEmpty(),
                op.str("to").orEmpty()
            )
            "regex" -> {
                val pattern = op.str("pattern").orEmpty()
                val to = op.str("to").orEmpty()
                runCatching { value.replace(Regex(pattern), to) }.getOrDefault(value)
            }
            "substring" -> {
                val start = op.str("start")?.toIntOrNull() ?: 0
                val end = op.str("end")?.toIntOrNull()
                val s = start.coerceIn(0, value.length)
                val e = (end ?: value.length).coerceIn(s, value.length)
                value.substring(s, e)
            }
            "split" -> {
                val sep = op.str("separator").orEmpty()
                val idx = op.str("index")?.toIntOrNull() ?: 0
                value.split(sep).getOrNull(idx) ?: value
            }
            "template" -> op.str("value").orEmpty().replace("{value}", value)
            "print" -> {
                val template = op.str("value") ?: "{value}"
                val msg = template.replace("{value}", value)
                DebugLog.print(msg)
                value
            }
            "eval" -> {
                val expr = op.str("expr").orEmpty()
                if (expr.isBlank()) value
                else ExpressionEngine.eval(expr) { name ->
                    if (name == "value") value else null
                } ?: value
            }
            "cmsEpisodes" -> parseCmsEpisodes(value)

            // ============ 替代 Rhino 的 3 个 op ============
            "base64" -> base64Decode(value)
            "jsArray" -> parseJsArrayLiteral(value)
            "base64Each" -> base64DecodeEach(value)

            else -> value
        }

    // ==================== cmsEpisodes ====================

    private fun parseCmsEpisodes(raw: String): String {
        if (raw.isBlank()) return "[]"
        val best = raw.split("$$$")
            .map { parseCmsLine(it) }
            .maxByOrNull { it.size }
            ?: emptyList()
        return best.joinToString(",", "[", "]") { (name, url) ->
            """{"name":${jsonStr(name)},"url":${jsonStr(url)}}"""
        }
    }

    private fun parseCmsLine(line: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (seg in line.split('#')) {
            val s = seg.trim()
            if (s.isEmpty()) continue
            val parts = s.split('$', limit = 2)
            if (parts.size != 2) continue
            val name = parts[0].trim()
            val url = parts[1].trim()
            if (url.contains('$')) continue
            if (url.isNotEmpty() && isDirectVideoUrl(url)) {
                result.add(name to url)
            }
        }
        return result
    }

    private fun isDirectVideoUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".mp4") ||
                path.endsWith(".flv") || path.endsWith(".mkv") ||
                path.endsWith(".ts") || path.endsWith(".mov") ||
                path.endsWith(".m4v")
    }

    // ==================== base64 ====================

    private fun base64Decode(s: String): String = runCatching {
        val clean = s.replace(Regex("[^A-Za-z0-9+/=]"), "")
        if (clean.isEmpty()) return s
        String(Base64.getDecoder().decode(clean), Charsets.UTF_8)
    }.getOrDefault(s)

    private fun parseJsArrayLiteral(s: String): String {
        val trimmed = s.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return s
        val inner = trimmed.substring(1, trimmed.length - 1)
        val items = Regex("['\"]([^'\"]*)['\"]").findAll(inner)
            .map { it.groupValues[1] }
            .toList()
        return items.joinToString(",", "[", "]") { jsonStr(it) }
    }

    private fun base64DecodeEach(jsonArray: String): String {
        val items = runCatching {
            Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(jsonArray)
                .map { it.groupValues[1] }
                .toList()
        }.getOrDefault(emptyList())
        return items.joinToString(",", "[", "]") { jsonStr(base64Decode(it)) }
    }

    // ==================== 工具 ====================

    fun jsonStr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20)
                    sb.append(String.format("\\u%04x", c.code))
                else sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}