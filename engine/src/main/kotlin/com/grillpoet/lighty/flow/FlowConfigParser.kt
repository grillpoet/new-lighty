package com.grillpoet.lighty.flow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

object FlowConfigParser {

    fun parse(obj: JsonObject, json: Json): FlowConfig {
        // ==================== 顶层 defaultHeaders ====================
        val defaultHeaders = stringMap(obj["defaultHeaders"])

        // ==================== urls ====================
        val urlsElem = obj["urls"] as? JsonArray
        if (urlsElem != null) {
            val urls = urlsElem.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .filter { it.isNotBlank() }
            require(urls.isNotEmpty()) { "urls 不能为空" }

            val base = JsonObject(obj.toMap() - "urls")

            val branches = urls.map { url ->
                val withUrl = JsonObject(base.toMap() + ("url" to JsonPrimitive(url)))
                parse(withUrl, json)
            }
            return FlowConfig.Parallel(
                defaultHeaders = defaultHeaders,
                branches = branches,
                dedup = obj.str("dedup"),
                sourceTag = obj.str("sourceTag") == "true"
            )
        }

        // ==================== parallel ====================
        val parallelElem = obj["parallel"]
        if (parallelElem is JsonArray) {
            return FlowConfig.Parallel(
                defaultHeaders = defaultHeaders,
                branches = parallelElem.map { parse(it.jsonObject, json) },
                dedup = obj.str("dedup"),
                sourceTag = obj.str("sourceTag") == "true"
            )
        }

        // ==================== steps ====================
        val stepsArr = obj["steps"] as? JsonArray
        if (stepsArr != null) {
            return FlowConfig.Serial(
                defaultHeaders = defaultHeaders,
                steps = stepsArr.map { parseStep(it.jsonObject) },
                output = parseOutput(obj["output"], obj)
            )
        }

        // ==================== 扁平 ====================
        return FlowConfig.Serial(
            defaultHeaders = defaultHeaders,
            steps = listOf(parseStep(obj)),
            output = parseOutput(obj["output"], obj)
        )
    }

    // ==================== Step ====================

    private fun parseStep(obj: JsonObject): Step {
        val request = if (obj["url"] != null) {
            RequestSpec(
                url = obj.str("url").orEmpty(),
                method = obj.str("method")?.uppercase() ?: "GET",
                body = obj.str("body"),
                params = elementMap(obj["params"]),
                headers = stringMap(obj["headers"]),
                followRedirects = obj.str("followRedirects") != "false",
                cookies = obj.str("cookies") == "true"
            )
        } else null

        val paginate = (obj["paginate"] as? JsonObject)?.let { p ->
            val next = p.str("next").orEmpty()
            val pageParam = p.str("pageParam").orEmpty()
            if (next.isBlank() && pageParam.isBlank()) null
            else PaginateSpec(
                next = next,
                base = p.str("base").orEmpty(),
                pageParam = pageParam,
                startPage = p.str("startPage")?.toIntOrNull() ?: 1,
                max = p.str("max")?.toIntOrNull() ?: 50
            )
        }

        return Step(
            request = request,
            parse = parseParse(obj),
            extract = parseExtract(obj["extract"] as? JsonObject),
            saveAs = obj.str("saveAs"),
            output = obj["output"]?.let { parseOutput(it, obj) },
            paginate = paginate
        )
    }

    private fun parseParse(obj: JsonObject): ParseSpec {
        return when (obj.str("parse")) {
            "json" -> ParseSpec.Json(obj.str("arrayPath").orEmpty())
            "html" -> ParseSpec.Html(obj.str("list").orEmpty())
            "js" -> ParseSpec.Js(obj.str("script").orEmpty())
            else -> ParseSpec.Raw
        }
    }

    private fun parseExtract(obj: JsonObject?): Map<String, ExtractSpec> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, ExtractSpec>()
        for ((k, v) in obj) {
            val o = v as? JsonObject ?: continue
            val regex = o.str("regex") ?: continue
            val group = o.str("group")?.toIntOrNull() ?: 1
            result[k] = ExtractSpec(regex, group)
        }
        return result
    }

    // ==================== Output ====================

    private fun parseOutput(elem: JsonElement?, parent: JsonObject): OutputSpec {
        if (elem is JsonPrimitive) {
            val ap = parent.str("arrayPath").orEmpty()
            return when (elem.content) {
                "list" -> OutputSpec.List(
                    ap,
                    parseFields(parent["fields"] as? JsonObject)
                )
                "episodes" -> OutputSpec.Episodes(
                    arrayPath = parent.str("episodesFrom").orEmpty().ifBlank { ap },
                    name = FieldSpec.Path("name"),
                    url = FieldSpec.Path("url")
                )
                "pages" -> OutputSpec.Pages(
                    from = parent.str("pagesFrom").orEmpty(),
                    ops = parent["ops"]
                )
                "text" -> OutputSpec.Text(parent.str("textFrom").orEmpty())
                "url" -> OutputSpec.Single(parseFieldOrEmpty(parent["from"]))
                "raw" -> OutputSpec.Raw
                else -> OutputSpec.Raw
            }
        }

        val obj = elem as? JsonObject
        if (obj == null) {
            val fields = parent["fields"] as? JsonObject
            return if (fields != null) {
                OutputSpec.List(
                    arrayPath = parent.str("arrayPath").orEmpty(),
                    fields = parseFields(fields)
                )
            } else OutputSpec.Raw
        }

        val ap = obj.str("arrayPath").orEmpty()
            .ifBlank { parent.str("arrayPath").orEmpty() }

        return when (obj.str("type")) {
            "list" -> OutputSpec.List(
                ap,
                parseFields(obj["fields"] as? JsonObject)
            )

            "url" -> OutputSpec.Single(parseFieldOrEmpty(obj["from"]))

            "episodes" -> OutputSpec.Episodes(
                arrayPath = obj.str("from").orEmpty().ifBlank { ap },
                name = parseFieldOrEmpty(obj["name"], defaultPath = "name"),
                url = parseFieldOrEmpty(obj["url"], defaultPath = "url")
            )

            "pages" -> OutputSpec.Pages(
                from = obj.str("from").orEmpty(),
                ops = obj["ops"]
            )

            "text" -> OutputSpec.Text(obj.str("from").orEmpty())

            else -> OutputSpec.Raw
        }
    }

    private fun parseFields(obj: JsonObject?): Map<String, FieldSpec> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, FieldSpec>()
        for ((k, v) in obj) {
            result[k] = parseFieldOrEmpty(v)
        }
        return result
    }

    private fun parseFieldOrEmpty(
        elem: JsonElement?,
        defaultPath: String = ""
    ): FieldSpec = parseField(elem) ?: FieldSpec.Path(defaultPath)

    private fun parseField(elem: JsonElement?): FieldSpec? {
        return when (elem) {
            null -> null
            is JsonNull -> null
            is JsonPrimitive -> FieldSpec.Path(elem.content)
            is JsonArray -> {
                val specs = elem.mapNotNull { parseField(it) }
                if (specs.isEmpty()) null else FieldSpec.Fallback(specs)
            }
            is JsonObject -> {
                val const = elem.str("const")
                if (const != null) {
                    FieldSpec.Const(const)
                } else {
                    val from = elem.str("from")
                    if (from != null) FieldSpec.From(from, elem["ops"])
                    else null
                }
            }
        }
    }

    // ==================== 工具 ====================

    private fun stringMap(elem: JsonElement?): Map<String, String> {
        val obj = elem as? JsonObject ?: return emptyMap()
        return obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }
    }

    private fun elementMap(elem: JsonElement?): Map<String, JsonElement> {
        val obj = elem as? JsonObject ?: return emptyMap()
        return obj.toMap()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}