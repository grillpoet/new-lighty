package com.grillpoet.lighty.ui.screen

import android.widget.Toast
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.ui.vm.ContentViewModel
import com.grillpoet.lighty.ui.widget.ActionIconBar
import com.grillpoet.lighty.ui.widget.CoverImage
import com.grillpoet.lighty.ui.widget.LightyControlButton
import com.grillpoet.lighty.ui.widget.LightyIconButton
import com.grillpoet.lighty.ui.widget.LightyTopBar
import com.grillpoet.lighty.ui.widget.LoadingBox

/**
 * 统一详情页。
 *
 * - 视频 / 音频：展示封面、元信息、分集网格，点击分集进播放页
 * - 漫画 / 小说：展示封面、元信息、章节网格，点击章节进阅读页
 *
 * 数据来源为 ContentViewModel（由 AppRoot 在 push 本页前 open 加载）。
 */
@Composable
fun DetailScreen(
    source: Source?,
    contentVm: ContentViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (Int) -> Unit,
    onDownloadEpisode: () -> Unit,
    onActionButtonClick: (Map<String, String>, Record) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by contentVm.state.collectAsStateWithLifecycle()
    val isFav by contentVm.isFavorite.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val scope = when {
        state.isManga -> "manga"
        state.isNovel -> "novel"
        else -> "video"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ==================== 顶部栏 ====================
        LightyTopBar(
            title = state.title.ifBlank { "详情" },
            onBack = onBack,
            backIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            actions = {
                ActionIconBar(
                    source = source,
                    scope = scope,
                    onAction = { btn -> onActionButtonClick(btn, state.record) }
                )
            }
        )

        // ==================== 封面 + 元信息 ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            CoverImage(
                url = state.cover,
                modifier = Modifier
                    .size(width = 100.dp, height = 140.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = state.title.ifBlank { "（无标题）" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.subtitle.isNotBlank()) {
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.episodes.isNotEmpty()) {
                    Text(
                        text = "共 ${state.episodes.size} 集",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ==================== 操作栏 ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 播放主按钮
            val canPlay = state.episodes.isNotEmpty() && !state.loading
            LightyControlButton(
                onClick = {
                    if (canPlay) onPlayEpisode(state.currentIndex)
                },
                icon = Icons.Rounded.PlayArrow,
                contentDescription = "播放",
                enabled = canPlay,
                size = 48.dp,
                iconSize = 26.dp
            )

            // 下载
            LightyIconButton(
                onClick = { if (canPlay) onDownloadEpisode() },
                icon = Icons.Rounded.Download,
                contentDescription = "下载",
                enabled = canPlay,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                size = 40.dp,
                iconSize = 20.dp
            )

            // 收藏
            LightyIconButton(
                onClick = {
                    val now = contentVm.toggleFavorite()
                    Toast.makeText(
                        context,
                        if (now) "已收藏" else "已取消收藏",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                icon = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "收藏",
                contentColor = if (isFav) Color(0xFFFF4D6D)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                size = 40.dp,
                iconSize = 20.dp
            )

            Spacer(Modifier.weight(1f))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ==================== 分集/章节列表 ====================
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading && state.episodes.isEmpty() -> LoadingBox(Modifier.fillMaxSize())

                state.error != null && state.episodes.isEmpty() -> CenteredText(
                    text = state.error ?: "加载失败"
                )

                state.episodes.isEmpty() -> CenteredText(text = "暂无内容")

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = state.episodes,
                        key = { index, _ -> index }
                    ) { index, ep ->
                        EpisodeTile(
                            name = ep.name.ifBlank { "第 ${index + 1} 集" },
                            selected = index == state.currentIndex,
                            onClick = { onPlayEpisode(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EpisodeTile(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerLow
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}