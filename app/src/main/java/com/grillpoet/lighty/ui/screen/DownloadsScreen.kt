package com.grillpoet.lighty.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.core.model.DownloadTask
import com.grillpoet.lighty.ui.vm.DownloadViewModel
import com.grillpoet.lighty.ui.widget.EmptyState
import com.grillpoet.lighty.ui.widget.LightyIconButton
import com.grillpoet.lighty.ui.widget.LightyTopBar

/**
 * 下载管理页。分组显示：
 *  - 进行中（排队 + 下载中 + 暂停）
 *  - 已完成
 *  - 失败
 */
@Composable
fun DownloadsScreen(
    viewModel: DownloadViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tasksMap by viewModel.tasks.collectAsStateWithLifecycle()
    val all = tasksMap.values.toList()

    val active = all.filter {
        it.status == DownloadTask.Status.QUEUED ||
                it.status == DownloadTask.Status.DOWNLOADING ||
                it.status == DownloadTask.Status.PAUSED
    }
    val completed = all.filter { it.status == DownloadTask.Status.COMPLETED }
    val failed = all.filter { it.status == DownloadTask.Status.FAILED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LightyTopBar(
            title = "下载管理",
            onBack = onBack,
            backIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            actions = {
                if (completed.isNotEmpty()) {
                    TextButton(onClick = {
                        completed.forEach { viewModel.removeTask(it.id) }
                    }) {
                        Text("清空已完成", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        )

        if (all.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Rounded.Download,
                title = "暂无下载",
                subtitle = "在内容页点击下载"
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (active.isNotEmpty()) {
                item(key = "h-active") { SectionHeader("进行中 (${active.size})") }
                items(active, key = { it.id }) { t ->
                    DownloadTaskCard(
                        task = t,
                        onPause = { viewModel.pauseTask(t.id) },
                        onResume = { viewModel.resumeTask(t.id) },
                        onRetry = { viewModel.retryTask(t.id) },
                        onCancel = { viewModel.removeTask(t.id) }
                    )
                }
            }

            if (failed.isNotEmpty()) {
                item(key = "h-failed") { SectionHeader("失败 (${failed.size})") }
                items(failed, key = { it.id }) { t ->
                    DownloadTaskCard(
                        task = t,
                        onPause = { },
                        onResume = { },
                        onRetry = { viewModel.retryTask(t.id) },
                        onCancel = { viewModel.removeTask(t.id) }
                    )
                }
            }

            if (completed.isNotEmpty()) {
                item(key = "h-done") { SectionHeader("已完成 (${completed.size})") }
                items(completed, key = { it.id }) { t ->
                    DownloadTaskCard(
                        task = t,
                        onPause = { },
                        onResume = { },
                        onRetry = { },
                        onCancel = { viewModel.removeTask(t.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.medium
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = task.fileName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(6.dp))

        // 进度条
        if (task.status == DownloadTask.Status.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { task.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(Modifier.size(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText(task),
                style = MaterialTheme.typography.labelMedium,
                color = when (task.status) {
                    DownloadTask.Status.FAILED -> MaterialTheme.colorScheme.error
                    DownloadTask.Status.COMPLETED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))

            when (task.status) {
                DownloadTask.Status.DOWNLOADING,
                DownloadTask.Status.QUEUED -> LightyIconButton(
                    onClick = onPause,
                    icon = Icons.Rounded.Pause,
                    contentDescription = "暂停",
                    size = 32.dp,
                    iconSize = 18.dp
                )

                DownloadTask.Status.PAUSED -> LightyIconButton(
                    onClick = onResume,
                    icon = Icons.Rounded.PlayArrow,
                    contentDescription = "继续",
                    size = 32.dp,
                    iconSize = 18.dp
                )

                DownloadTask.Status.FAILED -> LightyIconButton(
                    onClick = onRetry,
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "重试",
                    size = 32.dp,
                    iconSize = 18.dp
                )

                else -> {}
            }

            LightyIconButton(
                onClick = onCancel,
                icon = Icons.Rounded.Delete,
                contentDescription = "删除",
                contentColor = MaterialTheme.colorScheme.error,
                size = 32.dp,
                iconSize = 18.dp
            )
        }
    }
}

private fun statusText(t: DownloadTask): String = when (t.status) {
    DownloadTask.Status.QUEUED -> "等待中"
    DownloadTask.Status.DOWNLOADING -> "下载中 ${t.progress}%"
    DownloadTask.Status.PAUSED -> "已暂停 ${t.progress}%"
    DownloadTask.Status.COMPLETED -> "已完成"
    DownloadTask.Status.FAILED -> "失败：${t.error ?: "未知"}"
    DownloadTask.Status.REMOVED -> "已取消"
}