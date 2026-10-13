package com.grillpoet.lighty.flow

import com.grillpoet.lighty.flow.api.JsRuntime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

sealed interface ParseResult {
    data class Json(val element: JsonElement) : ParseResult
    data class HtmlList(val items: List<Element>) : ParseResult
    data class Text(val text: String) : ParseResult
    data object Empty : ParseResult
}

object Parsers {

    fun parse(
        body: String,
        spec: ParseSpec,
        json: Json,
        jsRuntime: JsRuntime? = null
    ): ParseResult = when (spec) {
        is ParseSpec.Raw -> ParseResult.Text(body)
        is ParseSpec.Json -> parseJson(body, json)
        is ParseSpec.Html -> parseHtml(body, spec.list)
        is ParseSpec.Js -> parseJs(body, spec.script, json, jsRuntime)
    }

    private fun parseJson(body: String, json: Json): ParseResult {
        val root: JsonElement = try {
            json.parseToJsonElement(body)
        } catch (_: Exception) {
            json.parseToJsonElement(body.replace('\'', '"'))
        }
        return ParseResult.Json(root)
    }

    private fun parseHtml(body: String, listSel: String): ParseResult {
        val doc = Jsoup.parse(body)
        if (listSel.isBlank()) return ParseResult.Text(doc.text())
        val items = doc.select(listSel).toList()
        return ParseResult.HtmlList(items)
    }

    private fun parseJs(
        body: String,
        script: String,
        json: Json,
        jsRuntime: JsRuntime?
    ): ParseResult {
        // ★ 改动：无 JS 引擎时明确报错，不再静默失败
        if (jsRuntime == null) {
            error("配置使用了 JS 解析（parse: \"js\"），但未启用 JS 引擎。" +
                    "请改用 ops（base64 / jsArray / base64Each 等），或注入 JsRuntime 实现")
        }

        val bindings = mutableMapOf<String, String>()
        val trimmed = body.trimStart()
        if (trimmed.startsWith("eval(function")) {
            val unpacked = runCatching {
                jsRuntime.eval(body, emptyMap())
            }.getOrDefault("")
            bindings["__unpacked__"] = unpacked
        } else if (body.isNotBlank()) {
            runCatching { jsRuntime.eval(body, emptyMap()) }
        }

        val result = runCatching {
            jsRuntime.eval(script, bindings)
        }.getOrDefault("")

        val t = result.trim()
        if (t.startsWith("[") || t.startsWith("{")) {
            val parsed = runCatching { json.parseToJsonElement(t) }.getOrNull()
            if (parsed != null) return ParseResult.Json(parsed)
        }
        return ParseResult.Text(result)
    }
}