package com.grillpoet.lighty.ui.screen

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.ui.vm.MainViewModel
import com.grillpoet.lighty.ui.widget.EmptyState
import com.grillpoet.lighty.ui.widget.LightyIconButton
import com.grillpoet.lighty.ui.widget.LightyTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConfigScreen(
    mainVm: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = mainVm.sourceStore
    val sources by store.sources.collectAsStateWithLifecycle()

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }

    // ==================== 导入（多选文件）====================

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var totalImported = 0
            var failCount = 0
            var firstError: String? = null

            uris.forEach { uri ->
                val text = readTextFromUri(context, uri)
                if (text.isNullOrBlank()) {
                    failCount++
                    if (firstError == null) firstError = "读取失败"
                    return@forEach
                }
                store.importJson(text)
                    .onSuccess { totalImported += it }
                    .onFailure {
                        failCount++
                        if (firstError == null) firstError = it.message
                    }
            }

            val msg = buildString {
                append("已导入 $totalImported 个源")
                if (failCount > 0) append("，$failCount 个文件失败")
                firstError?.let { append("（$it）") }
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            selectedIds = emptySet()
        }
    }

    // ==================== 导出（选目录 + 逐文件）====================

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val idsToExport = if (selectedIds.isEmpty())
            sources.map { it.id }
        else
            selectedIds.toList()

        if (idsToExport.isEmpty()) {
            Toast.makeText(context, "没有可导出的源", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val pickedDir = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(context, uri)
            }
            if (pickedDir == null) {
                Toast.makeText(context, "无法访问所选目录", Toast.LENGTH_SHORT).show()
                return@launch
            }

            var ok = 0
            var fail = 0

            withContext(Dispatchers.IO) {
                idsToExport.forEach { id ->
                    val text = store.exportJsonOne(id)
                    if (text == null) {
                        fail++
                        return@forEach
                    }
                    val written = writeOneJsonFile(context, pickedDir, id, text)
                    if (written) ok++ else fail++
                }
            }

            val msg = buildString {
                append("已导出 $ok 个源")
                if (fail > 0) append("，$fail 个失败")
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LightyTopBar(
            title = "配置管理",
            onBack = onBack,
            backIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            actions = {
                LightyIconButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    icon = Icons.Rounded.FileUpload,
                    contentDescription = "导入"
                )
                LightyIconButton(
                    onClick = {
                        if (sources.isEmpty()) {
                            Toast.makeText(context, "暂无源", Toast.LENGTH_SHORT).show()
                        } else {
                            exportLauncher.launch(null)
                        }
                    },
                    icon = Icons.Rounded.FileDownload,
                    contentDescription = "导出"
                )
            }
        )

        // 多选操作栏
        if (sources.isNotEmpty()) {
            MultiSelectToolbar(
                selectedCount = selectedIds.size,
                totalCount = sources.size,
                onToggleAll = {
                    selectedIds =
                        if (selectedIds.size == sources.size) emptySet()
                        else sources.map { it.id }.toSet()
                },
                onDelete = {
                    if (selectedIds.isNotEmpty()) confirmDelete = true
                }
            )
        }

        // 列表
        if (sources.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Rounded.Settings,
                title = "还没有配置源",
                subtitle = "点右上角导入 JSON 文件"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sources, key = { it.id }) { source ->
                    ConfigItem(
                        source = source,
                        selected = source.id in selectedIds,
                        onToggle = {
                            selectedIds = if (source.id in selectedIds)
                                selectedIds - source.id
                            else
                                selectedIds + source.id
                        }
                    )
                }
            }
        }
    }

    // 删除确认
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 ${selectedIds.size} 个配置？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val n = selectedIds.size
                    store.deleteIds(selectedIds)
                    Toast.makeText(
                        context,
                        "已删除 $n 个",
                        Toast.LENGTH_SHORT
                    ).show()
                    selectedIds = emptySet()
                    confirmDelete = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

// ==================== 多选工具栏 ====================

@Composable
private fun MultiSelectToolbar(
    selectedCount: Int,
    totalCount: Int,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selectedCount == 0) "共 $totalCount 个"
            else "已选 $selectedCount / $totalCount",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onToggleAll) {
            Text(
                if (selectedCount == totalCount && totalCount > 0) "取消全选"
                else "全选"
            )
        }
        TextButton(
            onClick = onDelete,
            enabled = selectedCount > 0
        ) {
            Text(
                "删除",
                color = if (selectedCount > 0)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

// ==================== 列表项 ====================

@Composable
private fun ConfigItem(
    source: Source,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name.ifBlank { source.id },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = source.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==================== 文件读写 ====================

private suspend fun readTextFromUri(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

/**
 * 往选定目录写一个 <id>.json。已存在则覆盖。
 * 返回 true 表示成功。
 */
private fun writeOneJsonFile(
    context: Context,
    dir: DocumentFile,
    id: String,
    text: String
): Boolean {
    // 文件名：把 id 里的非法字符替换掉，避免建文件失败
    val safeName = id.replace("/", "_").replace("\\", "_")
    val fileName = "$safeName.json"

    return runCatching {
        // 已存在先删掉，保证是覆盖而不是加 (1) 后缀
        dir.findFile(fileName)?.delete()

        val file = dir.createFile("application/json", fileName)
            ?: return false

        context.contentResolver.openOutputStream(file.uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        true
    }.getOrDefault(false)
}