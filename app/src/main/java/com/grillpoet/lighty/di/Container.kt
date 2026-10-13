package com.grillpoet.lighty.di

import android.app.Application
import com.grillpoet.lighty.AndroidSourceDirProvider
import com.grillpoet.lighty.data.Engine
import com.grillpoet.lighty.data.FavoriteStore
import com.grillpoet.lighty.data.FetchLite
import com.grillpoet.lighty.data.SourceStore
import com.grillpoet.lighty.flow.impl.DefaultFlowEngine
import com.grillpoet.lighty.flow.impl.http.OkHttpFetcher
import com.grillpoet.lighty.player.AndroidPlayer
import com.grillpoet.lighty.player.NowPlaying
import com.grillpoet.lighty.player.VideoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AppContainer(app: Application) {

    val appContext: Application = app

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // ==================== CookieJar（内存版） ====================

    private val memoryCookieJar = object : CookieJar {
        private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val key = url.host
            val list = store.getOrPut(key) { mutableListOf() }
            synchronized(list) {
                for (c in cookies) {
                    list.removeAll { it.name == c.name && it.path == c.path }
                    list.add(c)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = store[url.host] ?: return emptyList()
            val now = System.currentTimeMillis()
            synchronized(list) {
                list.removeAll { it.expiresAt < now }
                return list.filter { it.matches(url) }
            }
        }
    }

    // ==================== HTTP clients ====================

    private fun baseClientBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

    /** 不带 cookie（默认） */
    val okHttp: OkHttpClient = baseClientBuilder().build()

    /** 带 cookie，会话内保持 */
    private val okHttpWithCookies: OkHttpClient = baseClientBuilder()
        .cookieJar(memoryCookieJar)
        .build()

    // ==================== 播放 / 下载 ====================

    val player = AndroidPlayer(app)

    val downloader = FetchLite(
        client = okHttp,
        cacheDir = app.cacheDir,
        maxConcurrent = 3
    )

    private val sourceDirProvider = AndroidSourceDirProvider(app)

    val videoEngine = VideoEngine(app)

    val sourceStore = SourceStore(sourceDirProvider, json)

    val favoriteStore = FavoriteStore(sourceDirProvider, json)

    // ==================== Flow 引擎 ====================

    private val fetcher = OkHttpFetcher(
        plainClient = okHttp,
        cookieClient = okHttpWithCookies
    )

    private val flowEngine = DefaultFlowEngine(
        fetcher = fetcher,
        jsRuntime = null,
        json = json
    )

    val engine = Engine(
        sourceStore = sourceStore,
        flowEngine = flowEngine
    )

    // ==================== 全局播放状态 ====================

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    fun setNowPlaying(np: NowPlaying?) {
        _nowPlaying.value = np
    }
}