package com.grillpoet.lighty.flow

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ==================== 配置 ====================

sealed interface FlowConfig {
    /** 所有 step 共享的默认请求头，step 里的同名 header 优先 */
    val defaultHeaders: Map<String, String>

    data class Serial(
        override val defaultHeaders: Map<String, String> = emptyMap(),
        val steps: List<Step>,
        val output: OutputSpec
    ) : FlowConfig

    data class Parallel(
        override val defaultHeaders: Map<String, String> = emptyMap(),
        val branches: List<FlowConfig>,
        val dedup: String? = null,
        val sourceTag: Boolean = false
    ) : FlowConfig
}

data class Step(
    val request: RequestSpec?,
    val parse: ParseSpec = ParseSpec.Raw,
    val extract: Map<String, ExtractSpec> = emptyMap(),
    val saveAs: String? = null,
    val output: OutputSpec? = null,
    val paginate: PaginateSpec? = null
)

data class PaginateSpec(
    val next: String = "",
    val base: String = "",
    val pageParam: String = "",
    val startPage: Int = 1,
    val max: Int = 50
)

data class RequestSpec(
    val url: String,
    val method: String = "GET",
    val body: String? = null,
    val params: Map<String, JsonElement> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val followRedirects: Boolean = true,

    /** 声明 true 时，该 step 的请求使用带 CookieJar 的 client */
    val cookies: Boolean = false
)

data class ExtractSpec(
    val regex: String,
    val group: Int = 1
)

// ==================== 解析 ====================

sealed interface ParseSpec {
    data object Raw : ParseSpec
    data class Json(val arrayPath: String = "") : ParseSpec
    data class Html(val list: String = "") : ParseSpec
    data class Js(val script: String) : ParseSpec
}

// ==================== 输出 ====================

sealed interface OutputSpec {
    data class List(
        val arrayPath: String,
        val fields: Map<String, FieldSpec>
    ) : OutputSpec

    data class Single(val from: FieldSpec) : OutputSpec

    data class Episodes(
        val arrayPath: String,
        val name: FieldSpec,
        val url: FieldSpec
    ) : OutputSpec

    data class Pages(
        val from: String,
        val ops: JsonElement? = null
    ) : OutputSpec

    data class Text(val from: String) : OutputSpec
    data object Raw : OutputSpec
}

// ==================== 字段提取 ====================

sealed interface FieldSpec {
    data class Path(val path: String) : FieldSpec
    data class From(val from: String, val ops: JsonElement? = null) : FieldSpec
    data class Const(val value: String) : FieldSpec
    data class Fallback(val specs: List<FieldSpec>) : FieldSpec
}