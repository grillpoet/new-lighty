package com.grillpoet.lighty.flow.impl.http

import com.grillpoet.lighty.core.DebugLog
import com.grillpoet.lighty.core.LoggedException
import com.grillpoet.lighty.flow.api.FetchRequest
import com.grillpoet.lighty.flow.api.FetchResponse
import com.grillpoet.lighty.flow.api.Fetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 基于 OkHttp 的 Fetcher。
 *
 * 提供两个 client：
 *  - plainClient：不带 cookie jar（默认）
 *  - cookieClient：带 cookie jar，会话内保持 cookie
 *
 * 请求时按 FetchRequest.cookies 选择。
 */
class OkHttpFetcher(
    private val plainClient: OkHttpClient,
    private val cookieClient: OkHttpClient
) : Fetcher {

    override suspend fun fetch(request: FetchRequest): FetchResponse =
        withContext(Dispatchers.IO) {
            // ==================== 构造 body ====================
            val body = if (request.method == "GET" || request.method == "HEAD") {
                null
            } else {
                val ct = request.headers.entries
                    .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                    ?.value
                    ?: "application/x-www-form-urlencoded"
                (request.body ?: "").toRequestBody(ct.toMediaTypeOrNull())
            }

            // ==================== 构造请求 ====================
            val reqBuilder = Request.Builder()
                .url(request.url)
                .method(request.method, body)

            request.headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            val base = if (request.cookies) cookieClient else plainClient

            val useClient = if (request.followRedirects) {
                base
            } else {
                base.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            }

            val resp = try {
                useClient.newCall(reqBuilder.build()).execute()
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                DebugLog.error("${e.javaClass.simpleName}: $msg\n${request.method} ${request.url}")
                throw LoggedException(msg, e)
            }

            resp.use {
                val bodyStr = it.body?.string().orEmpty()

                if (it.code !in 200..299) {
                    DebugLog.error(
                        "HTTP ${it.code}\n${request.method} ${request.url}\n${bodyStr.take(300)}"
                    )
                }

                FetchResponse(
                    url = it.request.url.toString(),
                    code = it.code,
                    body = bodyStr,
                    location = it.header("Location")
                )
            }
        }
}