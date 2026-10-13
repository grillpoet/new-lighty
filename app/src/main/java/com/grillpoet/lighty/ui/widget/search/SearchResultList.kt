package com.grillpoet.lighty.ui.widget.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.ResultConfig
import com.grillpoet.lighty.ui.UiPrefs
import com.grillpoet.lighty.ui.widget.EmptyState
import com.grillpoet.lighty.ui.widget.LightyIconButton
import com.grillpoet.lighty.ui.widget.LoadingBox
import com.grillpoet.lighty.ui.widget.SongItem
import com.grillpoet.lighty.ui.widget.card.GridCard
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SearchResultList(
    items: List<Record>,
    loading: Boolean,
    canLoadMore: Boolean,
    error: String?,
    searched: Boolean,
    currentKey: String?,
    selectMode: Boolean,
    selectedKeys: Set<String>,
    onPlay: (Record) -> Unit,
    onDownload: (Record) -> Unit,
    onLongClick: (Record) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    resultLayout: ResultConfig = ResultConfig(),
    showModeToggle: Boolean = true
) {
    val displayMode by UiPrefs.displayMode.collectAsStateWithLifecycle()

    when {
        loading && items.isEmpty() -> LoadingBox(modifier)

        error != null && items.isEmpty() -> EmptyState(
            modifier = modifier,
            icon = Icons.Rounded.Search,
            title = "加载失败",
            subtitle = error
        )

        searched && items.isEmpty() -> EmptyState(
            modifier = modifier,
            icon = Icons.Rounded.Search,
            title = "没有结果",
            subtitle = "换个参数试试"
        )

        !searched -> EmptyState(
            modifier = modifier,
            icon = Icons.Rounded.Search,
            title = "开始搜索",
            subtitle = "输入参数搜索"
        )

        else -> {
            // 音乐永远是单列列表（SongItem 不支持网格）
            val type = items.firstOrNull()?.get("_type")
            val isMusic = type == "music"
            val useGrid = !isMusic && displayMode is UiPrefs.DisplayMode.Grid
            // 小说固定单列（横向布局不适合网格）
            val forceSingleColumn = type == "novel"

            Box(modifier) {
                if (useGrid) {
                    val columns = if (forceSingleColumn)
                        1
                    else
                        (displayMode as UiPrefs.DisplayMode.Grid).columns
                    CardGrid(
                        items = items,
                        columns = columns,
                        canLoadMore = canLoadMore,
                        loading = loading,
                        currentKey = currentKey,
                        selectMode = selectMode,
                        selectedKeys = selectedKeys,
                        onPlay = onPlay,
                        onDownload = onDownload,
                        onLongClick = onLongClick,
                        onLoadMore = onLoadMore,
                        config = resultLayout
                    )
                } else {
                    AudioList(
                        items = items,
                        canLoadMore = canLoadMore,
                        loading = loading,
                        currentKey = currentKey,
                        selectMode = selectMode,
                        selectedKeys = selectedKeys,
                        onPlay = onPlay,
                        onDownload = onDownload,
                        onLongClick = onLongClick,
                        onLoadMore = onLoadMore
                    )
                }

                // 显示模式切换按钮，浮在右上角
                if (showModeToggle && !isMusic) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        val isList = displayMode is UiPrefs.DisplayMode.List
                        LightyIconButton(
                            onClick = { UiPrefs.toggleDisplayMode() },
                            icon = if (isList) Icons.Rounded.GridView
                            else Icons.Rounded.ViewList,
                            contentDescription = if (isList) "切换为网格" else "切换为列表",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            size = 36.dp,
                            iconSize = 20.dp
                        )
                    }
                }
            }
        }
    }
}

// ==================== 音乐列表 ====================

@Composable
private fun AudioList(
    items: List<Record>,
    canLoadMore: Boolean,
    loading: Boolean,
    currentKey: String?,
    selectMode: Boolean,
    selectedKeys: Set<String>,
    onPlay: (Record) -> Unit,
    onDownload: (Record) -> Unit,
    onLongClick: (Record) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    val currentCanLoadMore by rememberUpdatedState(canLoadMore)
    val currentLoading by rememberUpdatedState(loading)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { index, r ->
                r["rid"] ?: r["playUrl"] ?: "$index-${r["title"].orEmpty()}"
            },
            contentType = { _, _ -> "song" }
        ) { index, r ->
            val key = r["_key"].orEmpty()
            SongItem(
                index = index,
                title = r["title"].orEmpty(),
                subtitle = r["subtitle"].orEmpty(),
                cover = r["cover"].orEmpty(),
                isCurrent = currentKey != null && key == currentKey,
                selectMode = selectMode,
                selected = key in selectedKeys,
                onPlay = { onPlay(r) },
                onDownload = { onDownload(r) },
                onLongClick = { if (!selectMode) onLongClick(r) }
            )
        }

        if (items.isNotEmpty()) {
            item(key = "__footer__") {
                ListFooter(loading = loading, canLoadMore = canLoadMore)
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (last >= total - 3 && currentCanLoadMore && !currentLoading) {
                    currentOnLoadMore()
                }
            }
    }
}

// ==================== 网格卡片 ====================

@Composable
private fun CardGrid(
    items: List<Record>,
    columns: Int,
    canLoadMore: Boolean,
    loading: Boolean,
    currentKey: String?,
    selectMode: Boolean,
    selectedKeys: Set<String>,
    onPlay: (Record) -> Unit,
    onDownload: (Record) -> Unit,
    onLongClick: (Record) -> Unit,
    onLoadMore: () -> Unit,
    config: ResultConfig
) {
    val gridState = rememberLazyGridState()
    val currentCanLoadMore by rememberUpdatedState(canLoadMore)
    val currentLoading by rememberUpdatedState(loading)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    val coverRatio = config.coverRatio
    val showSubtitle = config.showSubtitle
    val effectiveColumns = columns.coerceIn(1, 6)

    LazyVerticalGrid(
        columns = GridCells.Fixed(effectiveColumns),
        state = gridState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        gridItemsIndexed(
            items = items,
            key = { index, r ->
                r["rid"] ?: r["playUrl"] ?: "$index-${r["title"].orEmpty()}"
            },
            contentType = { _, _ -> "card" }
        ) { index, r ->
            val key = r["_key"].orEmpty()
            GridCard(
                record = r,
                isCurrent = currentKey != null && key == currentKey,
                selectMode = selectMode,
                selected = key in selectedKeys,
                onClick = { onPlay(r) },
                onLongClick = { if (!selectMode) onLongClick(r) },
                coverRatio = coverRatio,
                showSubtitle = showSubtitle
            )
        }

        if (items.isNotEmpty()) {
            item(
                key = "__footer__",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                ListFooter(loading = loading, canLoadMore = canLoadMore)
            }
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (last >= total - 4 && currentCanLoadMore && !currentLoading) {
                    currentOnLoadMore()
                }
            }
    }
}

@Composable
private fun ListFooter(loading: Boolean, canLoadMore: Boolean) {
    val text: String? = when {
        loading -> "加载中…"
        !canLoadMore -> "没有更多了"
        else -> null
    }
    if (text == null) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}