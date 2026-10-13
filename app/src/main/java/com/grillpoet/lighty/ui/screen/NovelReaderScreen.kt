package com.grillpoet.lighty.ui.screen

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.ui.vm.ContentViewModel
import com.grillpoet.lighty.ui.widget.ActionIconBar
import com.grillpoet.lighty.ui.widget.LightyIconButton

@Composable
fun NovelReaderScreen(
    viewModel: ContentViewModel,       // 原 VideoViewModel
    source: Source?,
    onBack: () -> Unit,
    onActionButtonClick: (Map<String, String>, Record) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCatalog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LightyIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回"
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.title.ifBlank { "小说" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                state.currentEpisode?.let { ep ->
                    Text(
                        text = ep.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ActionIconBar(
                source = source,
                scope = "novel",
                onAction = { btn -> onActionButtonClick(btn, state.record) }
            )

            LightyIconButton(
                onClick = { showCatalog = !showCatalog },
                icon = Icons.AutoMirrored.Rounded.List,
                contentDescription = "目录"
            )
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

                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp)
                    )
                }

                state.content.isBlank() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error ?: "暂无内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> ContentView(text = state.content)
            }
        }

        if (!showCatalog && state.episodes.size > 1) {
            BottomNav(
                currentIndex = state.currentIndex,
                total = state.episodes.size,
                onPrev = { viewModel.selectEpisode(state.currentIndex - 1) },
                onNext = { viewModel.selectEpisode(state.currentIndex + 1) }
            )
        }
    }
}

@Composable
private fun ContentView(text: String) {
    val paragraphs = remember(text) {
        text.split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = paragraphs,
            key = { index, _ -> index }
        ) { _, para ->
            Text(
                text = "　　$para",
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 28.sp,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
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
            val selected = index == currentIndex
            val bg = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerLow
            val fg = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) }
                    )
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ep.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NavButton(
            text = "上一章",
            icon = Icons.Rounded.ChevronLeft,
            enabled = currentIndex > 0,
            onClick = onPrev
        )

        Text(
            text = "${currentIndex + 1} / $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        NavButton(
            text = "下一章",
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
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (enabled) {
            TextButton(onClick = onClick) {
                NavButtonContent(text, icon, tint)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                NavButtonContent(text, icon, tint)
            }
        }
    }
}

@Composable
private fun NavButtonContent(
    text: String,
    icon: ImageVector,
    tint: Color
) {
    val left = icon == Icons.Rounded.ChevronLeft
    if (left) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
    }
    Text(text, color = tint, style = MaterialTheme.typography.labelMedium)
    if (!left) {
        Spacer(Modifier.width(4.dp))
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}