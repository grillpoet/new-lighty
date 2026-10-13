package com.grillpoet.lighty

import android.app.Application
import android.os.Environment
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.grillpoet.lighty.di.AppContainer
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class LightyApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    fun downloadDir(): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: filesDir
        return File(base, "Lighty").apply { mkdirs() }
    }

    /**
     * Coil 全局 ImageLoader。
     *
     * Referer 从各源的 imageReferer 字段动态查询：
     *   - 源声明 { "hanman18.com": "https://hanman18.com/" }
     *   - 加载图片 URL 里含 "hanman18.com" 就用它
     *   - 没命中就用兜底值
     *
     * 新增源只需在 JSON 加 imageReferer，不用改这里。
     */
    override fun newImageLoader(): ImageLoader {
        val imageClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.toString()
                val referer = resolveReferer(url)

                val req = original.newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/120.0.0.0 Safari/537.36"
                    )
                    .header("Referer", referer)
                    .build()
                chain.proceed(req)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient { imageClient }
            .build()
    }

    /** 从所有源声明的 imageReferer 里找匹配项 */
    private fun resolveReferer(url: String): String {
        if (!::container.isInitialized) return DEFAULT_REFERER
        val sources = container.sourceStore.sources.value
        for (s in sources) {
            for ((domain, ref) in s.imageReferer) {
                if (domain.isNotBlank() && url.contains(domain)) {
                    return ref
                }
            }
        }
        return DEFAULT_REFERER
    }

    private companion object {
        const val DEFAULT_REFERER = "https://www.google.com/"
    }
}