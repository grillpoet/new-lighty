package com.grillpoet.lighty.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.ui.vm.MainViewModel
import com.grillpoet.lighty.ui.widget.CoverImage
import com.grillpoet.lighty.ui.widget.EmptyState
import com.grillpoet.lighty.ui.widget.LightyIconButton
import com.grillpoet.lighty.ui.widget.LightyTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun FavoritesScreen(
    mainVm: MainViewModel,
    onBack: () -> Unit,
    onPlayVideo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = mainVm.favoriteStore
    val items by store.state.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val text = store.exportText()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "已导出 ${items.size} 条收藏", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }.orEmpty()
                }.getOrDefault("")
            }
            if (text.isBlank()) {
                Toast.makeText(context, "读取失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            store.importText(text)
                .onSuccess { count ->
                    Toast.makeText(context, "已导入 $count 条", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LightyTopBar(
            title = "我的收藏",
            onBack = onBack,
            backIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            actions = {
                LightyIconButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    icon = Icons.Rounded.FileUpload,
                    contentDescription = "导入"
                )
                LightyIconButton(
                    onClick = {
                        if (items.isEmpty()) {
                            Toast.makeText(context, "暂无收藏", Toast.LENGTH_SHORT).show()
                        } else {
                            exportLauncher.launch("lighty-favorites.json")
                        }
                    },
                    icon = Icons.Rounded.FileDownload,
                    contentDescription = "导出"
                )
            }
        )

        if (items.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Rounded.FavoriteBorder,
                title = "还没有收藏",
                subtitle = "在播放页或视频页点爱心收藏"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = items, key = { it.first }) { (key, item) ->
                    val isVideo = item["_type"]?.jsonPrimitive?.content == "video"
                    FavoriteItem(
                        title = item["title"]?.jsonPrimitive?.content.orEmpty(),
                        subtitle = item["subtitle"]?.jsonPrimitive?.content.orEmpty(),
                        cover = item["cover"]?.jsonPrimitive?.content.orEmpty(),
                        onClick = {
                            if (isVideo) {
                                onPlayVideo(key)
                            } else {
                                mainVm.playFromFavorites(key)
                                onBack()
                            }
                        },
                        onDelete = { store.remove(key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteItem(
    title: String,
    subtitle: String,
    cover: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.medium
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = cover,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title.ifBlank { "（无标题）" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LightyIconButton(
            onClick = onDelete,
            icon = Icons.Rounded.Delete,
            contentDescription = "删除",
            contentColor = MaterialTheme.colorScheme.error,
            size = 36.dp,
            iconSize = 18.dp
        )
    }
}