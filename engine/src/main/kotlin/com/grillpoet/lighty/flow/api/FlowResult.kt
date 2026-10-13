package com.grillpoet.lighty.flow.api

/**
 * Flow 引擎的结构化输出。
 * 所有结果都是强类型，不经过 JSON 字符串中转。
 */
sealed interface FlowResult {

    /** 列表型（搜索结果、目录）。每个元素是一组字段。 */
    data class Records(val items: List<Map<String, String>>) : FlowResult

    /** 分集 / 章节列表。 */
    data class Episodes(val items: List<EpisodeItem>) : FlowResult

    /** 图片 URL 列表。 */
    data class Pages(val urls: List<String>) : FlowResult

    /** 正文文本。 */
    data class Text(val content: String) : FlowResult

    /** 单个 URL（如播放直链）。 */
    data class Url(val value: String) : FlowResult

    /** 原始响应体。 */
    data class Raw(val body: String) : FlowResult
}

data class EpisodeItem(val name: String, val url: String)