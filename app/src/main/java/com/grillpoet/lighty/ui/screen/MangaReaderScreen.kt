package com.grillpoet.lighty.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.ui.vm.ContentViewModel
import com.grillpoet.lighty.ui.widget.ActionIconBar
import com.grillpoet.lighty.ui.widget.LightyIconButton

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaReaderScreen(
    viewModel: ContentViewModel,
    source: Source?,
    onBack: () -> Unit,
    onDownloadImages: (albumName: String, pages: List<String>) -> Unit,
    onActionButtonClick: (Map<String, String>, Record) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val episode = state.currentEpisode

    var showCatalog by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf(false) }
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(state.currentIndex) {
        selectMode = false
        selectedIndices = emptySet()
    }

    LaunchedEffect(state.currentIndex, state.episodes.size) {
        val ep = state.episodes.getOrNull(state.currentIndex)
        if (ep != null && ep.pages.isEmpty() && !state.loading) {
            viewModel.selectEpisode(state.currentIndex)
        }
    }

    val exitSelect = {
        selectMode = false
        selectedIndices = emptySet()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (selectMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1C))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LightyIconButton(
                    onClick = exitSelect,
                    icon = Icons.Rounded.Close,
                    contentDescription = "退出多选",
                    contentColor = Color.White
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "已选 ${selectedIndices.size} / ${episode?.pages?.size ?: 0}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                LightyIconButton(
                    onClick = {
                        val total = episode?.pages?.size ?: 0
                        selectedIndices = if (selectedIndices.size == total) {
                            emptySet()
                        } else {
                            (0 until total).toSet()
                        }
                    },
                    icon = Icons.Rounded.SelectAll,
                    contentDescription = "全选",
                    contentColor = Color.White
                )
                LightyIconButton(
                    onClick = {
                        val pages = episode?.pages.orEmpty()
                        val picked = selectedIndices
                            .sorted()
                            .mapNotNull { pages.getOrNull(it) }
                        if (picked.isNotEmpty()) {
                            onDownloadImages(state.title, picked)
                            exitSelect()
                        }
                    },
                    icon = Icons.Rounded.Download,
                    contentDescription = "下载",
                    contentColor = if (selectedIndices.isNotEmpty())
                        MaterialTheme.colorScheme.primary
                    else
                        Color.White.copy(alpha = 0.3f)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1C))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LightyIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    contentColor = Color.White
                )
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifBlank { "漫画" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (episode != null) {
                        Text(
                            text = episode.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                ActionIconBar(
                    source = source,
                    scope = "manga",
                    onAction = { btn -> onActionButtonClick(btn, state.record) }
                )

                LightyIconButton(
                    onClick = { showCatalog = !showCatalog },
                    icon = Icons.AutoMirrored.Rounded.List,
                    contentDescription = "目录",
                    contentColor = Color.White
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                showCatalog -> CatalogGrid(
                    episodes = state.episodes,
                    currentIndex = state.currentIndex,
                    onSelect = {
                        viewModel.selectEpisode(it)
                        showCatalog = false
                    }
                )

                state.loading -> LoadingIndicator()

                episode == null || episode.pages.isEmpty() -> EmptyChapter(
                    message = state.error ?: "本章暂无可读图片",
                    onRetry = { viewModel.selectEpisode(state.currentIndex) }
                )

                else -> PagesList(
                    pages = episode.pages,
                    selectMode = selectMode,
                    selectedIndices = selectedIndices,
                    onToggle = { idx ->
                        if (selectMode) {
                            selectedIndices = if (idx in selectedIndices)
                                selectedIndices - idx
                            else
                                selectedIndices + idx
                        }
                    },
                    onLongClick = { idx ->
                        if (!selectMode) {
                            selectMode = true
                            selectedIndices = setOf(idx)
                        }
                    }
                )
            }
        }

        if (!selectMode && state.episodes.size > 1) {
            BottomNav(
                currentIndex = state.currentIndex,
                total = state.episodes.size,
                onPrev = { viewModel.selectEpisode(state.currentIndex - 1) },
                onNext = { viewModel.selectEpisode(state.currentIndex + 1) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagesList(
    pages: List<String>,
    selectMode: Boolean,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    onLongClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            items = pages,
            key = { index, url -> "$index-$url" }
        ) { index, url ->
            val selected = index in selectedIndices
            val borderColor = if (selectMode && selected)
                MaterialTheme.colorScheme.primary
            else Color.Transparent

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (selectMode) onToggle(index) },
                        onLongClick = { onLongClick(index) }
                    )
                    .border(
                        width = if (selectMode && selected) 4.dp else 0.dp,
                        color = borderColor
                    )
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0F10))
                )

                if (selectMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(32.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check, null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogGrid(
    episodes: List<com.grillpoet.lighty.playback.Episode>,
    currentIndex: Int,
    onSelect: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItemsIndexed(
            items = episodes,
            key = { index, _ -> index }
        ) { index, ep ->
            CatalogItem(
                name = ep.name,
                selected = index == currentIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun CatalogItem(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else Color(0xFF2C2C30)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(bg, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoadingIndicator() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun EmptyChapter(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.size(12.dp))
        LightyIconButton(
            onClick = onRetry,
            icon = Icons.Rounded.Refresh,
            contentDescription = "重试",
            contentColor = Color.White
        )
    }
}

@Composable
private fun BottomNav(
    currentIndex: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1C))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NavButton(
            text = "上一话",
            icon = Icons.Rounded.ChevronLeft,
            enabled = currentIndex > 0,
            onClick = onPrev
        )

        Text(
            text = "${currentIndex + 1} / $total",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        NavButton(
            text = "下一话",
            icon = Icons.Rounded.ChevronRight,
            enabled = currentIndex < total - 1,
            onClick = onNext
        )
    }
}

@Composable
private fun NavButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f)
    Row(
        modifier = Modifier
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon == Icons.Rounded.ChevronLeft) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, color = tint, style = MaterialTheme.typography.labelMedium)
        } else {
            Text(text, color = tint, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}