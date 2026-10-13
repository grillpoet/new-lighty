package com.grillpoet.lighty.data

import com.grillpoet.lighty.core.model.DownloadTask
import com.grillpoet.lighty.data.download.Downloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class DownloadRequest(
    val url: String,
    val fileName: String,
    val path: Path,
    val groupId: String = "default",
    val id: String? = null
)

/**
 * 下载队列调度。实际传输委托给 [Downloader]。
 *
 * 职责：
 *   - 任务队列（等待 + 活跃）
 *   - 并发上限
 *   - 暂停 / 恢复 / 重试 / 取消 / 清空
 *   - 任务状态 StateFlow
 *
 * 文件操作**全部委托**给 [Downloader]，本类不碰 java.io.File 的 exists/delete。
 */
@OptIn(ExperimentalUuidApi::class)
class FetchLite(
    client: OkHttpClient,
    cacheDir: File,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    private val maxConcurrent: Int = 3,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) {
    private val downloader = Downloader(client, fileSystem, cacheDir)

    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private val semaphore = Semaphore(maxConcurrent)
    private val mutex = Mutex()

    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks: StateFlow<Map<String, DownloadTask>> = _tasks.asStateFlow()

    private val waitingQueue = ArrayDeque<DownloadTask>()
    private val activeJobs = mutableMapOf<String, Job>()
    private val removingIds = mutableSetOf<String>()

    // ==================== 公共 API ====================

    fun enqueue(request: DownloadRequest): String {
        val task = DownloadTask(
            id = request.id ?: Uuid.random().toString(),
            url = request.url,
            fileName = request.fileName,
            path = request.path,
            groupId = request.groupId
        )
        scope.launch {
            mutex.withLock {
                _tasks.value += (task.id to task)
                waitingQueue.addLast(task)
                dispatchUnsafe()
            }
        }
        return task.id
    }

    /** 挂起直到任务完成 / 失败 / 移除 */
    suspend fun awaitCompletion(id: String) {
        tasks.first { map ->
            val t = map[id]
            t != null && (t.status == DownloadTask.Status.COMPLETED ||
                    t.status == DownloadTask.Status.FAILED ||
                    t.status == DownloadTask.Status.REMOVED)
        }
    }

    fun pause(id: String) {
        scope.launch {
            mutex.withLock {
                val removed = waitingQueue.removeAll { it.id == id }
                if (removed) {
                    updateTask(id) { it.copy(status = DownloadTask.Status.PAUSED) }
                    return@withLock
                }
                activeJobs[id]?.cancel()
            }
        }
    }

    fun resume(id: String) {
        scope.launch {
            mutex.withLock {
                val task = _tasks.value[id]
                    ?.takeIf { it.status == DownloadTask.Status.PAUSED }
                    ?: return@launch
                val resumed = task.copy(status = DownloadTask.Status.QUEUED)
                _tasks.value += (id to resumed)
                waitingQueue.addLast(resumed)
                dispatchUnsafe()
            }
        }
    }

    fun retry(id: String) {
        scope.launch {
            mutex.withLock {
                val task = _tasks.value[id]
                    ?.takeIf { it.status == DownloadTask.Status.FAILED }
                    ?: return@launch
                val retried = task.copy(
                    status = DownloadTask.Status.QUEUED,
                    downloaded = 0L,
                    total = -1L,
                    error = null
                )
                _tasks.value += (id to retried)
                waitingQueue.addLast(retried)
                dispatchUnsafe()
            }
        }
    }

    suspend fun remove(id: String) {
        // 1. 标记移除 + 从队列删 + 取消活跃协程
        mutex.withLock {
            removingIds.add(id)
            waitingQueue.removeAll { it.id == id }
            activeJobs[id]?.cancel()
        }

        // 2. 等活跃协程真正退出（cancel 是异步的）
        activeJobs[id]?.join()

        // 3. 清理移除标记
        mutex.withLock {
            activeJobs.remove(id)
            removingIds.remove(id)
        }

        // 4. 委托 Downloader 删除文件，然后从状态里删
        _tasks.value[id]?.let { task ->
            withContext(Dispatchers.IO) { downloader.delete(task) }
            _tasks.value -= id
        }
    }

    suspend fun cancelAll() {
        mutex.withLock {
            waitingQueue.clear()
            activeJobs.keys.forEach { removingIds.add(it) }
            activeJobs.values.toList().forEach { it.cancel() }
        }
        activeJobs.values.toList().joinAll()
        mutex.withLock {
            activeJobs.clear()
            removingIds.clear()
            _tasks.value = emptyMap()
        }
    }

    // ==================== 调度 ====================

    private fun dispatchUnsafe() {
        while (activeJobs.size < maxConcurrent && waitingQueue.isNotEmpty()) {
            val task = waitingQueue.removeFirst()
            if (_tasks.value[task.id]?.status == DownloadTask.Status.REMOVED) continue

            val job = scope.launch {
                try {
                    semaphore.withPermit {
                        val shouldRun = mutex.withLock {
                            val t = _tasks.value[task.id]
                            t != null &&
                                    t.status != DownloadTask.Status.REMOVED &&
                                    t.status != DownloadTask.Status.PAUSED
                        }
                        if (!shouldRun) return@withPermit

                        updateTask(task.id) {
                            it.copy(status = DownloadTask.Status.DOWNLOADING)
                        }

                        // 委托给 Downloader
                        downloader.download(
                            task,
                            onProgress = { d, t ->
                                updateTask(task.id) {
                                    it.copy(downloaded = d, total = t)
                                }
                            }
                        )

                        // 成功
                        updateTask(task.id) {
                            it.copy(status = DownloadTask.Status.COMPLETED)
                        }
                    }
                } catch (e: CancellationException) {
                    val isRemoving = mutex.withLock { task.id in removingIds }
                    if (!isRemoving) {
                        updateTask(task.id) {
                            it.copy(status = DownloadTask.Status.PAUSED)
                        }
                    }
                    throw e
                } catch (e: Exception) {
                    updateTask(task.id) {
                        it.copy(status = DownloadTask.Status.FAILED, error = e.message)
                    }
                } finally {
                    mutex.withLock {
                        activeJobs.remove(task.id)
                        removingIds.remove(task.id)
                        dispatchUnsafe()
                    }
                }
            }
            activeJobs[task.id] = job
        }
    }

    // ==================== 工具 ====================

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { map ->
            val current = map[id] ?: return@update map
            map + (id to transform(current))
        }
    }
}