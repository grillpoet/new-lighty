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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.ui.vm.HomeTabViewModel
import com.grillpoet.lighty.ui.vm.SearchViewModel
import com.grillpoet.lighty.ui.widget.EmptyState
import com.grillpoet.lighty.ui.widget.LightyIconButton
import com.grillpoet.lighty.ui.widget.LoadingBox
import com.grillpoet.lighty.ui.widget.search.SearchResultList

@Composable
fun HomeScreen(
    searchVm: SearchViewModel,
    homeTabVm: HomeTabViewModel,
    onOpenDownloadManager: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenConfig: () -> Unit,
    onClearAll: () -> Unit,
    onPlay: (Record) -> Unit,
    onDownload: (Record) -> Unit,
    onDownloadMultiple: (List<Record>) -> Unit,
    onActionButtonClick: (Map<String, String>, Record) -> Unit,
    modifier: Modifier = Modifier
) {
    val sources by searchVm.sources.collectAsStateWithLifecycle()
    val searchState by searchVm.state.collectAsStateWithLifecycle()
    val currentSource = sources.firstOrNull { it.id == searchState.sourceId }

    val hasCategory = currentSource?.hasAction("categories") == true

    val tabs = remember(hasCategory) {
        buildList {
            add(Tab.SEARCH)
            if (hasCategory) add(Tab.CATEGORY)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val safeIndex = selectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val activeTab = tabs.getOrNull(safeIndex) ?: Tab.SEARCH

    LaunchedEffect(activeTab, currentSource?.id) {
        val src = currentSource ?: return@LaunchedEffect
        when (activeTab) {
            Tab.CATEGORY -> homeTabVm.loadCategoriesIfNeeded(src)
            Tab.SEARCH -> {}
        }
    }

    Column(modifier.fillMaxSize()) {
        TabBar(
            tabs = tabs,
            selectedIndex = safeIndex,
            onSelect = { selectedTab = it }
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                Tab.SEARCH -> SearchScreen(
                    viewModel = searchVm,
                    onOpenDownloadManager = onOpenDownloadManager,
                    onOpenFavorites = onOpenFavorites,
                    onOpenConfig = onOpenConfig,
                    onClearAll = onClearAll,
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onDownloadMultiple = onDownloadMultiple,
                    onActionButtonClick = onActionButtonClick,
                    modifier = Modifier.fillMaxSize()
                )

                Tab.CATEGORY -> CategoryTab(
                    source = currentSource,
                    vm = homeTabVm,
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onLongClick = {}
                )
            }
        }
    }
}

private enum class Tab(val label: String) {
    SEARCH("搜索"),
    CATEGORY("分类")
}

@Composable
private fun TabBar(
    tabs: List<Tab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    if (tabs.size <= 1) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        tabs.forEachIndexed { i, tab ->
            TabItem(
                label = tab.label,
                selected = i == selectedIndex,
                onClick = { onSelect(i) }
            )
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.onBackground
    else MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = weight,
            color = color
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(20.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.background
                )
                .padding(vertical = 1.dp)
        )
    }
}

// ==================== 分类 tab ====================

@Composable
private fun CategoryTab(
    source: Source?,
    vm: HomeTabViewModel,
    onPlay: (Record) -> Unit,
    onDownload: (Record) -> Unit,
    onLongClick: (Record) -> Unit
) {
    if (source == null || !source.hasAction("categories")) {
        EmptyState(
            modifier = Modifier.fillMaxSize(),
            icon = Icons.Rounded.Search,
            title = "本源无分类",
            subtitle = null
        )
        return
    }

    val listState by vm.categories.collectAsStateWithLifecycle()
    val contentState by vm.categoryContent.collectAsStateWithLifecycle()

    if (contentState.visible) {
        CategoryContent(
            state = contentState,
            source = source,
            onBack = { vm.exitCategoryContent() },
            onPlay = onPlay,
            onDownload = onDownload,
            onLongClick = onLongClick
        )
        return
    }

    when {
        listState.loading && listState.items.isEmpty() -> LoadingBox(Modifier.fillMaxSize())

        listState.error != null && listState.items.isEmpty() -> EmptyState(
            modifier = Modifier.fillMaxSize(),
            icon = Icons.Rounded.Search,
            title = "加载失败",
            subtitle = listState.error
        )

        listState.loaded && listState.items.isEmpty() -> EmptyState(
            modifier = Modifier.fillMaxSize(),
            icon = Icons.Rounded.Search,
            title = "暂无分类",
            subtitle = null
        )

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(listState.items, key = { it["cid"] ?: it["title"] ?: it.hashCode() }) { cat ->
                CategoryTile(
                    name = cat["title"].orEmpty().ifBlank { cat["name"].orEmpty() },
                    onClick = {
                        val cid = cat["cid"] ?: cat["id"] ?: cat["url"].orEmpty()
                        val title = cat["title"].orEmpty().ifBlank { cat["name"].orEmpty() }
                        vm.loadCategoryContent(source, cid, title)
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryTile(name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CategoryContent(
    state: com.grillpoet.lighty.ui.vm.CategoryContentState,
    source: Source,
    onBack: () -> Unit,
    onPlay: (Record) -> Unit,
    onDownload: (Record) -> Unit,
    onLongClick: (Record) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LightyIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回分类"
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = state.cidTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        when {
            state.loading && state.items.isEmpty() -> LoadingBox(Modifier.fillMaxSize())

            state.error != null && state.items.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Rounded.Search,
                title = "加载失败",
                subtitle = state.error
            )

            !state.loading && state.items.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Rounded.Search,
                title = "暂无内容",
                subtitle = null
            )

            else -> SearchResultList(
                items = state.items,
                loading = state.loading,
                canLoadMore = false,
                error = state.error,
                searched = true,
                currentKey = null,
                selectMode = false,
                selectedKeys = emptySet(),
                onPlay = onPlay,
                onDownload = onDownload,
                onLongClick = onLongClick,
                onLoadMore = {},
                modifier = Modifier.fillMaxSize(),
                resultLayout = source.resultLayout,
                showModeToggle = true
            )
        }
    }
}