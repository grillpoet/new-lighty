package com.grillpoet.lighty.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.ui.vm.SearchViewModel
import com.grillpoet.lighty.ui.widget.ActionChipBar
import com.grillpoet.lighty.ui.widget.search.MultiSelectBar
import com.grillpoet.lighty.ui.widget.search.QualitySelector
import com.grillpoet.lighty.ui.widget.search.SearchInputBar
import com.grillpoet.lighty.ui.widget.search.SearchResultList
import com.grillpoet.lighty.ui.widget.search.SearchTopBar
import com.grillpoet.lighty.ui.widget.search.SourceChipRow

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sources: List<Source> by viewModel.sources.collectAsStateWithLifecycle()
    val currentSource = sources.firstOrNull { it.id == state.sourceId }

    var selectMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val exitSelect: () -> Unit = remember {
        { selectMode = false; selectedKeys = emptySet() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        if (selectMode) {
            MultiSelectBar(
                selectedCount = selectedKeys.size,
                totalCount = state.items.size,
                allSelected = selectedKeys.size == state.items.size && state.items.isNotEmpty(),
                onExit = exitSelect,
                onToggleAll = {
                    selectedKeys =
                        if (selectedKeys.size == state.items.size && state.items.isNotEmpty())
                            emptySet()
                        else
                            state.items.mapNotNull { it["_key"] }.toSet()
                },
                onDownload = {
                    onDownloadMultiple(state.items.filter { it["_key"] in selectedKeys })
                    exitSelect()
                }
            )
        } else {
            SearchTopBar(
                onOpenDownloadManager = onOpenDownloadManager,
                onOpenFavorites = onOpenFavorites,
                onOpenConfig = onOpenConfig,
                onClearAll = onClearAll
            )

            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SourceChipRow(
                    sources = sources,
                    currentSourceId = state.sourceId,
                    onSourceSelected = viewModel::selectSource
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (currentSource == null) {
            NoSourceGuide(onOpenConfig)
            return@Column
        }

        if (!selectMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchInputBar(
                    value = state.input,
                    placeholder = "搜索",
                    onValueChange = viewModel::updateInput,
                    onSearch = viewModel::search,
                    modifier = Modifier.weight(1f)
                )

                if (currentSource.showQuality && currentSource.qualities.size > 1) {
                    QualitySelector(
                        choices = currentSource.qualities,
                        current = state.quality,
                        onSelected = viewModel::setQuality
                    )
                }
            }

            if (currentSource.buttonsFor("search").isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ActionChipBar(
                    source = currentSource,
                    scope = "search",
                    onAction = { btn -> onActionButtonClick(btn, emptyMap()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        SearchResultList(
            items = state.items,
            loading = state.loading,
            canLoadMore = state.canLoadMore,
            error = state.error,
            searched = state.searched,
            currentKey = null,
            selectMode = selectMode,
            selectedKeys = selectedKeys,
            onPlay = { r ->
                if (selectMode) {
                    val k = r["_key"] ?: return@SearchResultList
                    selectedKeys =
                        if (k in selectedKeys) selectedKeys - k else selectedKeys + k
                } else onPlay(r)
            },
            onDownload = onDownload,
            onLongClick = { r ->
                if (!selectMode) {
                    val k = r["_key"] ?: return@SearchResultList
                    selectMode = true
                    selectedKeys = setOf(k)
                }
            },
            onLoadMore = viewModel::loadMore,
            modifier = Modifier.fillMaxWidth().weight(1f),
            resultLayout = currentSource.resultLayout
        )
    }
}

@Composable
private fun NoSourceGuide(onOpenConfig: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Rounded.Settings, null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "还没有配置源",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "导入一份 JSON 配置开始使用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onOpenConfig) {
                Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("去配置")
            }
        }
    }
}