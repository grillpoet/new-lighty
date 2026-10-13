package com.grillpoet.lighty.flow

import com.grillpoet.lighty.flow.api.EpisodeItem
import com.grillpoet.lighty.flow.api.FlowResult
import com.grillpoet.lighty.flow.impl.ops.TextOps
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.nodes.Element
import java.util.UUID

object Shaper {

    private val DEFAULT_IMG_ATTRS = listOf(
        "src", "data-src", "data-original", "data-lazy-src",
        "data-lazy", "data-echo", "data-url"
    )

    fun shape(
        result: ParseResult,
        spec: OutputSpec,
        scope: VariableScope,
        json: Json,
        typeTag: String? = null
    ): FlowResult = when (spec) {
        is OutputSpec.List -> shapeList(result, spec, scope, json, typeTag)
        is OutputSpec.Single -> shapeSingle(result, spec, scope)
        is OutputSpec.Episodes -> shapeEpisodes(result, spec, scope, json)
        is OutputSpec.Pages -> shapePages(result, spec, scope)
        is OutputSpec.Text -> shapeText(result, spec, scope)
        OutputSpec.Raw -> FlowResult.Raw(shapeRaw(result))
    }

    // ==================== 归一化 ====================

    private fun toItems(result: ParseResult, path: String): List<Any> {
        return when (result) {
            is ParseResult.Json -> {
                val target = if (path.isBlank()) result.element
                else jsonPath(result.element, path) ?: return emptyList()
                when (target) {
                    is JsonArray -> target.toList()
                    is JsonElement -> listOf(target)
                    else -> emptyList()
                }
            }
            is ParseResult.HtmlList -> {
                if (path.isBlank() || path == ":self") {
                    result.items
                } else {
                    result.items.flatMap { el -> el.select(path).toList() }
                }
            }
            is ParseResult.Text -> {
                if (result.text.isBlank()) emptyList()
                else listOf(result.text)
            }
            ParseResult.Empty -> emptyList()
        }
    }

    // ==================== 输出形状 ====================

    private fun shapeRaw(result: ParseResult): String = when (result) {
        is ParseResult.Text -> result.text
        is ParseResult.Json -> result.element.toString()
        is ParseResult.HtmlList -> result.items.joinToString("\n") { it.outerHtml() }
        ParseResult.Empty -> ""
    }

    private fun shapeList(
        result: ParseResult,
        spec: OutputSpec.List,
        scope: VariableScope,
        json: Json,
        typeTag: String?
    ): FlowResult.Records {
        val items = toItems(result, spec.arrayPath)
        if (items.isEmpty()) return FlowResult.Records(emptyList())

        val records = items.mapNotNull { item ->
            val rec = mutableMapOf<String, String>()
            for ((key, fspec) in spec.fields) {
                val v = evalField(item, fspec, scope, json) ?: continue
                rec[key] = v
            }
            if (rec.isEmpty()) return@mapNotNull null
            if (typeTag != null) rec["_type"] = typeTag
            rec["_key"] = rec["rid"]?.takeIf { it.isNotBlank() }
                ?: rec["playUrl"]?.takeIf { it.isNotBlank() }
                        ?: UUID.randomUUID().toString()
            rec
        }
        return FlowResult.Records(records)
    }

    private fun shapeSingle(
        result: ParseResult,
        spec: OutputSpec.Single,
        scope: VariableScope
    ): FlowResult.Url {
        val direct = resolveFromScope(spec.from, scope)
        if (!direct.isNullOrBlank()) return FlowResult.Url(direct)

        val items = toItems(result, "")
        val first = items.firstOrNull() ?: return FlowResult.Url("")
        val v = evalField(first, spec.from, scope, null).orEmpty().trim()
        return FlowResult.Url(v)
    }

    private fun shapeEpisodes(
        result: ParseResult,
        spec: OutputSpec.Episodes,
        scope: VariableScope,
        json: Json
    ): FlowResult.Episodes {
        val items = toItems(result, spec.arrayPath)
        val list = items.mapNotNull { item ->
            val name = evalField(item, spec.name, scope, json) ?: return@mapNotNull null
            val url = evalField(item, spec.url, scope, json) ?: return@mapNotNull null
            if (name.isBlank() || url.isBlank()) return@mapNotNull null
            EpisodeItem(name = name, url = url)
        }
        return FlowResult.Episodes(list)
    }

    private fun shapePages(
        result: ParseResult,
        spec: OutputSpec.Pages,
        scope: VariableScope
    ): FlowResult.Pages {
        val fromRaw = spec.from.trim()

        // 变量来源：from 被 {xxx} 完全包裹
        if (fromRaw.startsWith("{") && fromRaw.endsWith("}") && fromRaw.length > 2) {
            val varName = fromRaw.substring(1, fromRaw.length - 1)
            val rawValue = scope.get(varName).orEmpty()
            val processed = TextOps.apply(rawValue, spec.ops as? JsonArray)
            return FlowResult.Pages(parseUrlList(processed))
        }

        val forcedAttrs: List<String>? = if (fromRaw.startsWith("@")) {
            fromRaw.substring(1).split(',').map { it.trim() }
                .filter { it.isNotBlank() }.ifEmpty { null }
        } else null

        val items = toItems(result, if (forcedAttrs != null) "" else fromRaw)

        val list = items.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.takeIf { it.isNotBlank() }
                is Element -> {
                    val attrs = forcedAttrs ?: DEFAULT_IMG_ATTRS
                    attrs.firstNotNullOfOrNull { a ->
                        item.attr(a).takeIf { it.isNotBlank() }
                    }
                }
                is String -> item.takeIf { it.isNotBlank() }
                else -> null
            }
        }
        return FlowResult.Pages(list)
    }

    private fun shapeText(
        result: ParseResult,
        spec: OutputSpec.Text,
        scope: VariableScope
    ): FlowResult.Text {
        val items = toItems(result, spec.from)
        val text = items.joinToString("\n") { item ->
            when (item) {
                is Element -> item.wholeText().trim()
                is JsonPrimitive -> item.contentOrNull.orEmpty().trim()
                is JsonElement -> item.toString()
                is String -> item.trim()
                else -> item.toString()
            }
        }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return FlowResult.Text(text)
    }

    // ==================== 字段求值 ====================

    private fun evalField(
        item: Any,
        spec: FieldSpec,
        scope: VariableScope,
        json: Json?
    ): String? = when (spec) {
        is FieldSpec.Path -> pathFromItem(item, spec.path)
        is FieldSpec.Const -> scope.interpolate(spec.value)
        is FieldSpec.From -> {
            var raw = pathFromItem(item, spec.from)
            // 路径找不到时回退到 scope 插值
            // 支持 from = "{m3u8Path}?token={token}" 这类变量拼接
            if (raw == null) {
                val interpolated = scope.interpolate(spec.from)
                if (interpolated != spec.from) raw = interpolated
            }
            raw?.let { TextOps.apply(it, spec.ops as? JsonArray) }
        }
        is FieldSpec.Fallback -> {
            var found: String? = null
            for (s in spec.specs) {
                val v = evalField(item, s, scope, json)
                if (!v.isNullOrBlank()) { found = v; break }
            }
            found
        }
    }

    private fun pathFromItem(item: Any, path: String): String? = when (item) {
        is Element -> evalHtmlSelector(item, path)
        is JsonElement -> (jsonPath(item, path) as? JsonPrimitive)?.contentOrNull
        is String -> if (path.isBlank()) item else null
        else -> null
    }

    private fun evalHtmlSelector(item: Element, spec: String): String? {
        var sel: String
        var attr: String? = null
        var regex: String? = null

        val parts = spec.split("@")
        if (parts.size >= 2) {
            sel = parts[0]
            attr = parts[1]
            if (parts.size >= 3) regex = parts[2]
        } else {
            sel = spec
        }

        val elem = if (sel.isBlank() || sel == ":self") item
        else item.selectFirst(sel) ?: return null

        var raw = if (attr == null) elem.text() else elem.attr(attr)
        if (regex != null) {
            val m = Regex(regex).find(raw) ?: return null
            raw = m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: m.value
        }
        return raw.trim().ifBlank { null }
    }

    private fun jsonPath(root: JsonElement, path: String): JsonElement? {
        if (path.isBlank()) return root
        var cur: JsonElement? = root
        for (seg in path.split('.')) {
            cur = cur ?: return null
            val idx = seg.toIntOrNull()
            cur = if (idx != null) (cur as? JsonArray)?.getOrNull(idx)
            else (cur as? JsonObject)?.get(seg)
        }
        return cur
    }

    private fun resolveFromScope(spec: FieldSpec, scope: VariableScope): String? {
        val path = when (spec) {
            is FieldSpec.Path -> spec.path
            is FieldSpec.From -> spec.from
            is FieldSpec.Const -> return scope.interpolate(spec.value)
            is FieldSpec.Fallback -> {
                for (s in spec.specs) resolveFromScope(s, scope)?.let { return it }
                return null
            }
        }
        if (path.startsWith("{") && path.endsWith("}")) {
            return scope.get(path.substring(1, path.length - 1))
        }
        return null
    }

    // ==================== 工具 ====================

    private fun parseUrlList(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return runCatching {
                Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(trimmed)
                    .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
                    .toList()
            }.getOrDefault(emptyList())
        }

        return listOf(trimmed)
    }
}