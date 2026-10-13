package com.grillpoet.lighty.data.download

import com.grillpoet.lighty.core.model.DownloadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * 实际下载逻辑，与队列调度解耦。
 *
 * 职责：
 *   - 单文件 HTTP 下载（支持 Range 断点续传）
 *   - m3u8 三层解析 + 并发切片下载 + 拼接
 *   - 原子移动到目标路径
 *   - 删除已下载文件
 *
 * 全用 Okio FileSystem，不碰 java.io.File 的 exists/delete，
 * 因此可以用 FakeFileSystem 做单测。
 *
 * 通过 onProgress 回调上报进度，不直接操作 StateFlow。
 * 失败抛异常，由调用方决定状态。
 */
class Downloader(
    private val client: OkHttpClient,
    private val fileSystem: FileSystem,
    private val cacheDir: File,
    private val bufferSize: Int = 8 * 1024,
    private val progressInterval: Duration = 200.milliseconds,
) {
    private companion object {
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        const val SEGMENT_CONCURRENCY = 4
    }

    // ==================== 对外 API ====================

    /**
     * 下载入口。自动识别 m3u8 / 单文件。
     * 成功返回（文件已在 task.path）；失败抛异常。
     */
    suspend fun download(
        task: DownloadTask,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val tmpPath = tmpPathFor(task)
        if (isM3u8(task.url)) {
            downloadM3u8(task, tmpPath, onProgress)
        } else {
            downloadSingle(task, tmpPath, onProgress)
        }
        moveAtomically(tmpPath, task.path)
    }

    /**
     * 删除任务对应的目标文件和临时文件。
     * 全用 okio FileSystem，不依赖 java.io.File。
     */
    fun delete(task: DownloadTask) {
        runCatching {
            if (fileSystem.exists(task.path)) fileSystem.delete(task.path)
        }
        runCatching {
            val tmp = tmpPathFor(task)
            if (fileSystem.exists(tmp)) fileSystem.delete(tmp)
        }
    }

    // ==================== 单文件 ====================

    private suspend fun downloadSingle(
        task: DownloadTask,
        tmpPath: Path,
        onProgress: (Long, Long) -> Unit,
    ) {
        var downloaded = if (fileSystem.exists(tmpPath)) {
            fileSystem.metadata(tmpPath).size ?: 0L
        } else 0L

        val reqBuilder = Request.Builder()
            .url(task.url)
            .header("User-Agent", UA)
        if (downloaded > 0L) reqBuilder.header("Range", "bytes=$downloaded-")

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")

            val isPartial = resp.code == 206
            val body = resp.body ?: error("空响应体")
            val contentLength = body.contentLength()

            val totalSize: Long = when {
                isPartial ->
                    resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                        ?: (if (contentLength > 0) contentLength + downloaded else -1L)
                downloaded > 0L -> {
                    fileSystem.delete(tmpPath)
                    downloaded = 0L
                    contentLength
                }
                else -> contentLength
            }

            onProgress(downloaded, totalSize)

            fileSystem.appendingSink(tmpPath).buffer().use { sink ->
                val source = body.source()
                val buf = Buffer()
                var current = downloaded
                var lastMark = TimeSource.Monotonic.markNow()

                while (currentCoroutineContext().isActive) {
                    val read = source.read(buf, bufferSize.toLong())
                    if (read == -1L) break
                    sink.write(buf, read)
                    current += read
                    if (lastMark.elapsedNow() >= progressInterval) {
                        onProgress(current, totalSize)
                        lastMark = TimeSource.Monotonic.markNow()
                    }
                }

                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("Download cancelled")
                }
                onProgress(current, totalSize)
            }

            val actual = fileSystem.metadata(tmpPath).size ?: 0L
            if (totalSize > 0L && actual < totalSize) {
                error("Size mismatch: $actual / $totalSize")
            }
        }
    }

    // ==================== m3u8 ====================

    private suspend fun downloadM3u8(
        task: DownloadTask,
        tmpPath: Path,
        onProgress: (Long, Long) -> Unit,
    ) {
        if (fileSystem.exists(tmpPath)) runCatching { fileSystem.delete(tmpPath) }

        // 1. 请求 m3u8，拿切片列表（支持主/子播放列表）
        val playlist = httpGetString(task.url)
        val segments = if (playlist.contains("#EXT-X-STREAM-INF")) {
            val subUrl = parseMasterPlaylist(playlist, task.url).firstOrNull()
                ?: error("主播放列表为空")
            val subText = httpGetString(subUrl)
            if (subText.contains("#EXT-X-KEY")) error("暂不支持加密 m3u8")
            parseMediaPlaylist(subText, subUrl)
        } else {
            if (playlist.contains("#EXT-X-KEY")) error("暂不支持加密 m3u8")
            parseMediaPlaylist(playlist, task.url)
        }
        if (segments.isEmpty()) error("m3u8 没有切片")

        val total = segments.size.toLong()
        onProgress(0L, total)

        // 2. 临时目录
        val tempDir = cacheDir.absolutePath.toPath() / "${safeId(task)}_segments"
        if (fileSystem.exists(tempDir)) runCatching { fileSystem.deleteRecursively(tempDir) }
        fileSystem.createDirectories(tempDir)

        val counter = AtomicInteger(0)
        val sem = Semaphore(SEGMENT_CONCURRENCY)

        try {
            // 3. 并发下切片
            coroutineScope {
                segments.forEachIndexed { index, segUrl ->
                    launch {
                        sem.withPermit {
                            downloadSegment(segUrl, tempDir / "$index.ts")
                            onProgress(counter.incrementAndGet().toLong(), total)
                        }
                    }
                }
            }

            // 4. 按顺序拼接
            fileSystem.sink(tmpPath).buffer().use { sink ->
                for (i in segments.indices) {
                    fileSystem.source(tempDir / "$i.ts").buffer().use { src ->
                        val buf = Buffer()
                        while (true) {
                            val n = src.read(buf, 8 * 1024L)
                            if (n == -1L) break
                            sink.write(buf, n)
                        }
                    }
                }
            }
        } finally {
            // 5. 清理临时目录
            runCatching { fileSystem.deleteRecursively(tempDir) }
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 把 task.id 转成安全的文件名片段。
     *
     * task.id 是业务 key（如 "nete-meting:https://api.qijieya.cn/meting/?..."），
     * 里面可能含 `/`、`?`、`:` 等字符，直接当路径拼接会被文件系统误解为目录分隔符，
     * 导致创建文件失败（如截图里的 FAILED）。
     *
     * 只保留 [A-Za-z0-9._-]，其余一律换成 `_`，再截断到 80 字符。
     */
    private fun safeId(task: DownloadTask): String =
        task.id
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { "task" }

    private fun tmpPathFor(task: DownloadTask): Path =
        cacheDir.absolutePath.toPath() / "${safeId(task)}.tmp"

    private fun isM3u8(url: String): Boolean =
        url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    private fun httpGetString(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} @ $url")
            resp.body?.string().orEmpty()
        }
    }

    private fun downloadSegment(url: String, target: Path) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("切片失败: HTTP ${resp.code}")
            val body = resp.body ?: error("切片空响应")
            fileSystem.sink(target).buffer().use { sink ->
                val src = body.source()
                val buf = Buffer()
                while (true) {
                    val n = src.read(buf, 8 * 1024L)
                    if (n == -1L) break
                    sink.write(buf, n)
                }
            }
        }
    }

    private fun parseMasterPlaylist(text: String, base: String): List<String> {
        val urls = mutableListOf<String>()
        var nextIsUrl = false
        for (raw in text.lineSequence()) {
            val t = raw.trim()
            when {
                t.isEmpty() -> continue
                t.startsWith("#EXT-X-STREAM-INF") -> nextIsUrl = true
                t.startsWith("#") -> continue
                nextIsUrl -> {
                    urls += resolveUrl(base, t)
                    nextIsUrl = false
                }
            }
        }
        return urls
    }

    private fun parseMediaPlaylist(text: String, base: String): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolveUrl(base, it) }
            .toList()

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://"))
            return relative
        return base.toHttpUrlOrNull()?.resolve(relative)?.toString() ?: relative
    }

    private fun moveAtomically(source: Path, target: Path) {
        target.parent?.let { fileSystem.createDirectories(it) }
        try {
            fileSystem.atomicMove(source, target)
        } catch (_: Exception) {
            runCatching { fileSystem.delete(target) }
            try {
                fileSystem.atomicMove(source, target)
            } catch (_: Exception) {
                fileSystem.source(source).buffer().use { src ->
                    fileSystem.sink(target).buffer().use { dst ->
                        val buf = Buffer()
                        while (true) {
                            val n = src.read(buf, 8192L)
                            if (n == -1L) break
                            dst.write(buf, n)
                        }
                        dst.flush()
                    }
                }
                fileSystem.delete(source)
            }
        }
    }
}