package com.grillpoet.lighty.core.model

import okio.Path
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class DownloadTask(
    val id: String = Uuid.random().toString(),
    val url: String,
    val fileName: String,
    val path: Path,
    val groupId: String = "default",
    val status: Status = Status.QUEUED,
    val downloaded: Long = 0L,
    val total: Long = -1L,
    val error: String? = null
) {
    val progress: Int
        get() = if (total > 0L) (downloaded * 100 / total).toInt() else 0

    enum class Status {
        QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, REMOVED
    }
}