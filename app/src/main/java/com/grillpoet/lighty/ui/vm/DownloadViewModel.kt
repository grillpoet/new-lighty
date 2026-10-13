package com.grillpoet.lighty.ui.vm

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.lighty.MainActivity
import com.grillpoet.lighty.core.LyricParser
import com.grillpoet.lighty.core.model.DownloadTask
import com.grillpoet.lighty.data.AudioTagger
import com.grillpoet.lighty.data.DownloadRequest
import com.grillpoet.lighty.data.Engine
import com.grillpoet.lighty.data.FetchLite
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.flow.api.FlowResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Path.Companion.toPath
import java.io.File
import java.util.UUID

class DownloadViewModel(
    private val app: Application,
    private val downloader: FetchLite,
    private val engine: Engine,
    private val okHttp: OkHttpClient,
    private val downloadDir: File,
    private val appScope: CoroutineScope
) : ViewModel() {

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val CHANNEL_NAME = "下载完成"
    }

    val tasks: StateFlow<Map<String, DownloadTask>> = downloader.tasks

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val notificationManager =
        app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var channelCreated = false

    // ==================== 公共 API ====================

    fun removeTask(id: String) {
        viewModelScope.launch { downloader.remove(id) }
    }

    fun pauseTask(id: String) {
        downloader.pause(id)
    }

    fun resumeTask(id: String) {
        downloader.resume(id)
    }

    fun retryTask(id: String) {
        downloader.retry(id)
    }

    fun consumeMessage() { _message.value = null }

    fun downloadSingle(source: Source, record: Record, quality: Record?) {
        viewModelScope.launch {
            val title = record["title"].orEmpty()
            _message.value = when (prepareAndDownload(source, record, quality)) {
                PrepareResult.Ok -> "已加入下载：$title"
                PrepareResult.Skip -> "已在下装中：$title"
                PrepareResult.Fail -> "下载失败：$title"
            }
        }
    }

    fun downloadMultiple(source: Source, records: List<Record>, quality: Record?) {
        if (records.isEmpty()) return
        viewModelScope.launch {
            val results = coroutineScope {
                records.map { async { prepareAndDownload(source, it, quality) } }.awaitAll()
            }
            val ok = results.count { it == PrepareResult.Ok }
            val skip = results.count { it == PrepareResult.Skip }
            val fail = results.count { it == PrepareResult.Fail }
            _message.value = buildString {
                if (ok > 0) append("已加入 $ok 首")
                if (skip > 0) { if (isNotEmpty()) append("；"); append("跳过 $skip 首") }
                if (fail > 0) { if (isNotEmpty()) append("；"); append("失败 $fail 首") }
            }.ifEmpty { "无可用任务" }
        }
    }

    // ==================== 视频 ====================

    fun downloadVideoEpisode(title: String, episodeName: String, url: String) {
        if (url.isBlank() || title.isBlank() || episodeName.isBlank()) return
        viewModelScope.launch {
            val ext = extFromUrl(url)
            val fileName = buildVideoFileName(title, episodeName, ext)
            val key = "video:${title}:${episodeName}"

            val existing = downloader.tasks.value[key]
            if (existing != null && (
                        existing.status == DownloadTask.Status.QUEUED ||
                                existing.status == DownloadTask.Status.DOWNLOADING)
            ) {
                _message.value = "已在下装中：$episodeName"
                return@launch
            }
            if (existing != null) downloader.remove(key)

            downloadDir.mkdirs()
            val tmpFile = File(downloadDir, "tmp_${UUID.randomUUID()}.$ext")

            downloader.enqueue(
                DownloadRequest(
                    url = url,
                    fileName = fileName,
                    path = tmpFile.absolutePath.toPath(),
                    id = key
                )
            )

            appScope.launch {
                awaitCompletion(key)
                val task = downloader.tasks.value[key]
                if (task?.status == DownloadTask.Status.COMPLETED && tmpFile.exists()) {
                    publishVideoToMediaStore(tmpFile, fileName, title, ext)
                    tmpFile.delete()
                    notifyCompleted(fileName)
                }
            }

            _message.value = "已加入下载：$episodeName"
        }
    }

    // ==================== 图片 ====================

    fun downloadImages(albumName: String, pages: List<String>) {
        if (pages.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            var skip = 0
            var fail = 0

            pages.forEachIndexed { index, url ->
                if (url.isBlank()) {
                    fail++
                    return@forEachIndexed
                }

                val ext = imageExtFromUrl(url)
                val pageName = "%03d".format(index + 1)
                val fileName = buildImageFileName(albumName, pageName, ext)
                val key = "image:$albumName:$pageName"

                val existing = downloader.tasks.value[key]
                if (existing != null && (
                            existing.status == DownloadTask.Status.QUEUED ||
                                    existing.status == DownloadTask.Status.DOWNLOADING)
                ) {
                    skip++
                    return@forEachIndexed
                }
                if (existing != null) downloader.remove(key)

                downloadDir.mkdirs()
                val tmpFile = File(downloadDir, "tmp_${UUID.randomUUID()}.$ext")

                downloader.enqueue(
                    DownloadRequest(
                        url = url,
                        fileName = fileName,
                        path = tmpFile.absolutePath.toPath(),
                        id = key
                    )
                )

                appScope.launch {
                    awaitCompletion(key)
                    val task = downloader.tasks.value[key]
                    if (task?.status == DownloadTask.Status.COMPLETED && tmpFile.exists()) {
                        publishImageToMediaStore(tmpFile, fileName, albumName)
                        tmpFile.delete()
                    }
                }

                ok++
            }

            _message.value = buildString {
                append("已加入 $ok 张")
                if (skip > 0) append("，跳过 $skip 张")
                if (fail > 0) append("，失败 $fail 张")
            }
        }
    }

    // ==================== 内部：音频 ====================

    private sealed interface PrepareResult {
        data object Ok : PrepareResult
        data object Skip : PrepareResult
        data object Fail : PrepareResult
    }

    private suspend fun prepareAndDownload(
        source: Source,
        record: Record,
        quality: Record?
    ): PrepareResult {
        val title = record["title"].orEmpty().ifBlank { return PrepareResult.Fail }
        val key = "${source.id}:${record["_key"] ?: record["rid"] ?: title}"
        val ext = record["ext"] ?: quality?.get("ext") ?: "mp3"
        val qualityValue = quality?.get("value").orEmpty()

        val existing = downloader.tasks.value[key]
        if (existing != null) {
            when (existing.status) {
                DownloadTask.Status.QUEUED,
                DownloadTask.Status.DOWNLOADING -> return PrepareResult.Skip
                DownloadTask.Status.COMPLETED -> downloader.remove(key)
                else -> {}
            }
        }

        val (url, lyricText) = coroutineScope {
            val u = async {
                val direct = record["playUrl"]
                if (!direct.isNullOrBlank() && !source.hasAction("play")) {
                    direct
                } else {
                    extractUrl(
                        engine.invoke(
                            source, "play",
                            record + mapOf("quality" to qualityValue)
                        ).getOrNull()
                    )
                }
            }
            val l = async {
                if (source.hasAction("lyric")) {
                    extractText(engine.invoke(source, "lyric", record).getOrNull())
                } else ""
            }
            u.await().trim() to l.await()
        }

        if (url.isBlank()) return PrepareResult.Fail

        val finalFileName = buildFileName(title, record["subtitle"].orEmpty(), ext)
        downloadDir.mkdirs()

        val tmpFile = File(downloadDir, "tmp_${UUID.randomUUID()}.$ext")

        downloader.enqueue(
            DownloadRequest(
                url = url,
                fileName = finalFileName,
                path = tmpFile.absolutePath.toPath(),
                id = key
            )
        )

        val lyricLines = LyricParser.parse(lyricText)
        val lyricFormatted = lyricLines.joinToString("\n") { line ->
            val t = line.timestamp
            "[%02d:%02d.%03d]%s".format(
                t / 60000, (t % 60000) / 1000, t % 1000, line.text
            )
        }
        val coverFile = record["cover"]?.takeIf { it.isNotBlank() }?.let { downloadCover(it) }

        appScope.launch {
            awaitCompletion(key)
            val task = downloader.tasks.value[key]
            if (task?.status == DownloadTask.Status.COMPLETED && tmpFile.exists()) {
                AudioTagger.write(
                    file = tmpFile,
                    title = title,
                    artist = record["subtitle"].orEmpty(),
                    lyric = lyricFormatted,
                    coverUrl = coverFile
                )
                publishToMediaStore(tmpFile, finalFileName, title, record["subtitle"].orEmpty(), ext)
                tmpFile.delete()
                notifyCompleted(finalFileName)
            }
            coverFile?.delete()
        }

        return PrepareResult.Ok
    }

    private suspend fun awaitCompletion(id: String) {
        downloader.tasks.first { map ->
            val t = map[id]
            t == null || t.status == DownloadTask.Status.COMPLETED ||
                    t.status == DownloadTask.Status.FAILED ||
                    t.status == DownloadTask.Status.REMOVED
        }
    }

    private suspend fun downloadCover(url: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = when {
                url.startsWith("//") -> "https:$url"
                url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
                else -> url
            }
            val req = Request.Builder().url(normalized).build()
            val resp = okHttp.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching null
            val f = File(app.cacheDir, "cover_${UUID.randomUUID()}.jpg")
            resp.body?.byteStream()?.use { input -> f.outputStream().use { input.copyTo(it) } }
            f
        }.getOrNull()
    }

    private suspend fun publishToMediaStore(
        srcFile: File, displayName: String,
        title: String, artist: String, ext: String
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeFor(ext))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MUSIC + "/Lighty")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            val uri = app.contentResolver.insert(collection, values) ?: return@runCatching
            app.contentResolver.openOutputStream(uri)?.use { out ->
                srcFile.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                app.contentResolver.update(uri, values, null, null)
            }
        }
    }

    private suspend fun publishVideoToMediaStore(
        srcFile: File, displayName: String, title: String, ext: String
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.TITLE, title)
                put(MediaStore.Video.Media.MIME_TYPE, mimeFromExt(ext))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/Lighty")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val uri = app.contentResolver.insert(collection, values) ?: return@runCatching
            app.contentResolver.openOutputStream(uri)?.use { out ->
                srcFile.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                app.contentResolver.update(uri, values, null, null)
            }
        }
    }

    private suspend fun publishImageToMediaStore(
        srcFile: File, displayName: String, albumName: String
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val ext = displayName.substringAfterLast('.', "jpg")
            val safeAlbum = albumName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(50)

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeForImage(ext))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Lighty/$safeAlbum")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = app.contentResolver.insert(collection, values) ?: return@runCatching
            app.contentResolver.openOutputStream(uri)?.use { out ->
                srcFile.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                app.contentResolver.update(uri, values, null, null)
            }
        }
    }

    // ==================== 文件名 / MIME ====================

    private fun buildFileName(title: String, artist: String, ext: String): String {
        val t = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
        val a = artist.replace(Regex("[\\\\:*?\"<>|]"), "_").take(40)
        return if (a.isBlank()) "$t.$ext" else "$t - $a.$ext"
    }

    private fun buildVideoFileName(title: String, episodeName: String, ext: String): String {
        val t = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(50)
        val e = episodeName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(30)
        return if (e.isBlank()) "$t.$ext" else "$t - $e.$ext"
    }

    private fun buildImageFileName(albumName: String, pageName: String, ext: String): String {
        val t = albumName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(50)
        return "${t}_$pageName.$ext"
    }

    private fun extFromUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "m3u8" -> "ts"
            "mp4", "flv", "mkv", "avi", "mov", "m4v", "ts" -> ext
            else -> "mp4"
        }
    }

    private fun imageExtFromUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> ext
            else -> "jpg"
        }
    }

    private fun mimeFor(ext: String) = when (ext.lowercase()) {
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        else -> "audio/mpeg"
    }

    private fun mimeFromExt(ext: String) = when (ext.lowercase()) {
        "ts" -> "video/mp2t"
        "flv" -> "video/x-flv"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        else -> "video/mp4"
    }

    private fun mimeForImage(ext: String) = when (ext.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }

    // ==================== 通知 ====================

    private fun ensureChannel() {
        if (channelCreated) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "下载完成的提示"
        }
        notificationManager.createNotificationChannel(channel)
        channelCreated = true
    }

    private fun notifyCompleted(fileName: String) {
        val isForeground = ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
        if (isForeground) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        ensureChannel()

        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            app,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText(fileName)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(fileName.hashCode(), notif)
    }

    // ==================== 工具 ====================

    private fun extractUrl(r: FlowResult?): String = when (r) {
        is FlowResult.Url -> r.value
        is FlowResult.Raw -> r.body
        is FlowResult.Text -> r.content
        else -> ""
    }

    private fun extractText(r: FlowResult?): String = when (r) {
        is FlowResult.Text -> r.content
        is FlowResult.Raw -> r.body
        else -> ""
    }
}