package com.grillpoet.lighty.flow.api

/**
 * HTTP 抓取抽象。
 */
interface Fetcher {
    suspend fun fetch(request: FetchRequest): FetchResponse
}

data class FetchRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val followRedirects: Boolean = true,

    /** true 时使用带 CookieJar 的 client，会话内保持 cookie */
    val cookies: Boolean = false
)

data class FetchResponse(
    val url: String,
    val code: Int,
    val body: String,
    val location: String? = null
)