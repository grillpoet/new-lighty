package com.grillpoet.lighty.flow

import com.grillpoet.lighty.flow.api.FetchRequest
import com.grillpoet.lighty.flow.api.Fetcher
import com.grillpoet.lighty.flow.api.FlowResult
import com.grillpoet.lighty.flow.api.JsRuntime
import com.grillpoet.lighty.flow.impl.ops.TextOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class FlowRunner(
    private val fetcher: Fetcher,
    private val jsRuntime: JsRuntime?,
    private val json: Json
) {
    suspend fun run(config: FlowConfig, args: Map<String, String>): FlowResult =
        when (config) {
            is FlowConfig.Serial -> runSerial(config, args)
            is FlowConfig.Parallel -> runParallel(config, args)
        }

    // ==================== Serial ====================

    private suspend fun runSerial(
        config: FlowConfig.Serial,
        args: Map<String, String>
    ): FlowResult = withContext(Dispatchers.IO) {
        val scope = VariableScope(args)
        val typeTag = args["_type"]
        var lastResult: ParseResult = ParseResult.Empty

        for (step in config.steps) {
            if (step.request == null) {
                val parseSpec = step.parse
                if (parseSpec is ParseSpec.Js) {
                    val resolved = scope.interpolate(parseSpec.script)
                    lastResult = Parsers.parse("", ParseSpec.Js(resolved), json, jsRuntime)
                }
                step.output?.let { out ->
                    return@withContext Shaper.shape(lastResult, out, scope, json, typeTag)
                }
                continue
            }

            if (step.paginate != null) {
                lastResult = paginateFetch(step, scope, config.defaultHeaders)
                step.output?.let { out ->
                    return@withContext Shaper.shape(lastResult, out, scope, json, typeTag)
                }
                continue
            }

            // 普通单步
            val url = buildUrl(step.request, scope)
            val headers = mergeHeaders(config.defaultHeaders, step.request.headers, scope)
            val resp = fetcher.fetch(
                FetchRequest(
                    url = url,
                    method = step.request.method,
                    body = step.request.body?.let { scope.interpolate(it) },
                    headers = headers,
                    followRedirects = step.request.followRedirects,
                    cookies = step.request.cookies
                )
            )
            val body = resp.body

            step.saveAs?.let { scope.put(it, body) }

            for ((k, ex) in step.extract) {
                val m = Regex(ex.regex).find(body)
                val v = m?.groupValues?.getOrNull(ex.group) ?: m?.value ?: ""
                scope.put(k, v)
            }

            lastResult = Parsers.parse(body, step.parse, json, jsRuntime)

            step.output?.let { out ->
                return@withContext Shaper.shape(lastResult, out, scope, json, typeTag)
            }
        }

        Shaper.shape(lastResult, config.output, scope, json, typeTag)
    }

    // ==================== Paginate ====================

    private suspend fun paginateFetch(
        step: Step,
        scope: VariableScope,
        defaultHeaders: Map<String, String>
    ): ParseResult {
        val spec = step.paginate!!
        return if (spec.pageParam.isNotBlank()) {
            paginateFetchJson(step, scope, spec, defaultHeaders)
        } else {
            paginateFetchHtml(step, scope, spec, defaultHeaders)
        }
    }

    private suspend fun paginateFetchHtml(
        step: Step,
        scope: VariableScope,
        spec: PaginateSpec,
        defaultHeaders: Map<String, String>
    ): ParseResult {
        val request = step.request!!

        val htmlAcc = mutableListOf<Element>()
        val jsonAcc = mutableListOf<JsonElement>()

        val seen = mutableSetOf<String>()
        var currentUrl: String? = buildUrl(request, scope)
        var pageCount = 0

        while (pageCount < spec.max) {
            val url = currentUrl ?: break
            if (!seen.add(url)) break
            pageCount++

            val headers = mergeHeaders(defaultHeaders, request.headers, scope)
            val body = try {
                fetcher.fetch(
                    FetchRequest(
                        url = url,
                        method = request.method,
                        body = request.body?.let { scope.interpolate(it) },
                        headers = headers,
                        followRedirects = request.followRedirects,
                        cookies = request.cookies
                    )
                ).body
            } catch (_: Exception) {
                break
            }

            val result = Parsers.parse(body, step.parse, json, jsRuntime)
            var doc: org.jsoup.nodes.Document? = null

            when (result) {
                is ParseResult.HtmlList -> {
                    if (result.items.isEmpty()) break
                    htmlAcc.addAll(result.items)
                    doc = result.items.first().ownerDocument()
                }
                is ParseResult.Json -> {
                    val arr = result.element as? JsonArray ?: break
                    jsonAcc.addAll(arr)
                }
                else -> break
            }

            currentUrl = if (doc != null) findNextUrl(doc, spec, url) else null
        }

        return when {
            htmlAcc.isNotEmpty() -> ParseResult.HtmlList(htmlAcc)
            jsonAcc.isNotEmpty() -> ParseResult.Json(JsonArray(jsonAcc))
            else -> ParseResult.Empty
        }
    }

    private suspend fun paginateFetchJson(
        step: Step,
        scope: VariableScope,
        spec: PaginateSpec,
        defaultHeaders: Map<String, String>
    ): ParseResult {
        val request = step.request!!
        val jsonAcc = mutableListOf<JsonElement>()
        val htmlAcc = mutableListOf<Element>()

        var page = spec.startPage
        var pageCount = 0

        while (pageCount < spec.max) {
            scope.put(spec.pageParam, page.toString())

            val url = buildUrl(request, scope)
            val headers = mergeHeaders(defaultHeaders, request.headers, scope)
            val body = try {
                fetcher.fetch(
                    FetchRequest(
                        url = url,
                        method = request.method,
                        body = request.body?.let { scope.interpolate(it) },
                        headers = headers,
                        followRedirects = request.followRedirects,
                        cookies = request.cookies
                    )
                ).body
            } catch (_: Exception) {
                break
            }

            val result = Parsers.parse(body, step.parse, json, jsRuntime)
            var added = false

            when (result) {
                is ParseResult.Json -> {
                    val arr = result.element as? JsonArray
                    if (arr != null && arr.isNotEmpty()) {
                        jsonAcc.addAll(arr)
                        added = true
                    }
                }
                is ParseResult.HtmlList -> {
                    if (result.items.isNotEmpty()) {
                        htmlAcc.addAll(result.items)
                        added = true
                    }
                }
                else -> {}
            }

            if (!added) break
            page++
            pageCount++
        }

        return when {
            jsonAcc.isNotEmpty() -> ParseResult.Json(JsonArray(jsonAcc))
            htmlAcc.isNotEmpty() -> ParseResult.HtmlList(htmlAcc)
            else -> ParseResult.Empty
        }
    }

    private fun findNextUrl(
        doc: org.jsoup.nodes.Document,
        spec: PaginateSpec,
        currentUrl: String
    ): String? {
        val parts = spec.next.split("@")
        val sel = parts[0].trim()
        val attr = parts.getOrNull(1)?.trim()?.ifBlank { null } ?: "href"
        if (sel.isBlank()) return null

        val el = doc.selectFirst(sel) ?: return null
        val href = el.attr(attr).takeIf { it.isNotBlank() } ?: return null

        if (href.startsWith("http://") || href.startsWith("https://")) return href
        if (href.startsWith("//")) {
            val scheme = runCatching { URI(currentUrl).scheme }.getOrNull() ?: "https"
            return "$scheme:$href"
        }

        runCatching { URI(currentUrl).resolve(href).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return if (spec.base.isNotBlank()) {
            spec.base.trimEnd('/') + "/" + href.trimStart('/')
        } else href
    }

    // ==================== Parallel ====================

    private suspend fun runParallel(
        config: FlowConfig.Parallel,
        args: Map<String, String>
    ): FlowResult = withContext(Dispatchers.IO) {
        coroutineScope {
            val tasks = config.branches.map { branch ->
                async(Dispatchers.IO) {
                    runCatching {
                        (run(branch, args) as? FlowResult.Records)?.items ?: emptyList()
                    }.getOrDefault(emptyList())
                }
            }
            val all = tasks.awaitAll().flatten()

            val dedupKey = config.dedup
            val deduped: List<Map<String, String>> = if (dedupKey.isNullOrBlank()) {
                all
            } else {
                val seen = mutableSetOf<String>()
                all.filter { rec ->
                    val k = rec[dedupKey]
                    k.isNullOrBlank() || seen.add(k)
                }
            }

            val typeTag = args["_type"]
            val tagged = if (config.sourceTag) {
                deduped.map { rec -> rec + ("_source" to extractShortHost(rec)) }
            } else deduped

            val final = if (typeTag != null) {
                tagged.map { rec ->
                    if (rec.containsKey("_type")) rec else rec + ("_type" to typeTag)
                }
            } else tagged

            FlowResult.Records(final)
        }
    }

    private fun extractShortHost(rec: Map<String, String>): String {
        val raw = rec["cover"].orEmpty().ifBlank { rec["playUrl"].orEmpty() }
        if (raw.isBlank()) return ""
        return runCatching {
            val u = if (raw.startsWith("//")) "https:$raw" else raw
            val host = URI(u).host ?: return@runCatching ""
            val parts = host.split('.').filter { it.isNotBlank() }
            when {
                parts.size >= 2 -> parts.takeLast(2).joinToString(".")
                parts.size == 1 -> parts[0]
                else -> ""
            }
        }.getOrDefault("")
    }

    // ==================== HTTP ====================

    private fun mergeHeaders(
        defaultHeaders: Map<String, String>,
        stepHeaders: Map<String, String>,
        scope: VariableScope
    ): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for ((k, v) in defaultHeaders) {
            result[k] = scope.interpolate(v)
        }
        for ((k, v) in stepHeaders) {
            result[k] = scope.interpolate(v)
        }
        return result
    }

    private fun buildUrl(spec: RequestSpec, scope: VariableScope): String {
        val base = scope.interpolate(spec.url)
        if (spec.params.isEmpty()) return base
        val q = spec.params.entries.joinToString("&") { (k, v) ->
            "${enc(scope.interpolate(k))}=${enc(evalParam(v, scope))}"
        }
        return if (base.contains('?')) "$base&$q" else "$base?$q"
    }

    private fun evalParam(elem: JsonElement, scope: VariableScope): String =
        when (elem) {
            is JsonPrimitive -> scope.interpolate(elem.content)
            is JsonObject -> {
                val raw = (elem["value"] as? JsonPrimitive)?.contentOrNull
                    ?: (elem["from"] as? JsonPrimitive)?.contentOrNull
                    ?: ""
                val resolved = scope.interpolate(raw)
                TextOps.apply(resolved, elem["ops"] as? JsonArray)
            }
            else -> ""
        }

    private fun enc(s: String): String =
        runCatching { URLEncoder.encode(s, "UTF-8") }.getOrDefault(s)
}