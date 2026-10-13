package com.grillpoet.lighty.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grillpoet.lighty.data.Engine
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.flow.api.FlowResult
import com.grillpoet.lighty.ui.widget.EmptyState
import com.grillpoet.lighty.ui.widget.LightyTopBar
import com.grillpoet.lighty.ui.widget.search.SearchResultList
import kotlinx.coroutines.launch

@Composable
fun SourcePageScreen(
    source: Source,
    title: String,
    action: String,
    args: Record,
    engine: Engine,
    onBack: () -> Unit,
    onItemClick: (Record) -> Unit,
    modifier: Modifier = Modifier
) {
    var items by remember { mutableStateOf<List<Record>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    suspend fun loadPage(targetPage: Int, replace: Boolean) {
        loading = true
        error = null

        val pageSize = source.pageSize.takeIf { it > 0 } ?: 20
        val pageArgs = args + mapOf(
            "page" to targetPage.toString(),
            "page1" to (targetPage + 1).toString(),
            "offset" to (targetPage * pageSize).toString(),
            "limit" to pageSize.toString()
        )

        val result = engine.invoke(source, action, pageArgs)
        result.fold(
            onSuccess = { flowResult ->
                val list: List<Record> = when (flowResult) {
                    is FlowResult.Records -> flowResult.items
                    else -> emptyList()
                }
                items = if (replace) list else items + list
                page = targetPage
                hasMore = list.size >= pageSize
                loading = false
                searched = true
            },
            onFailure = { e ->
                error = e.message ?: "加载失败"
                loading = false
                hasMore = false
            }
        )
    }

    LaunchedEffect(source.id, action, args) {
        loadPage(0, replace = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LightyTopBar(
            title = title,
            onBack = onBack,
            backIcon = Icons.AutoMirrored.Rounded.ArrowBack
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            when {
                loading && items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                error != null && items.isEmpty() -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Rounded.Search,
                    title = "加载失败",
                    subtitle = error
                )

                searched && items.isEmpty() -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Rounded.Search,
                    title = "暂无内容",
                    subtitle = null
                )

                else -> SearchResultList(
                    items = items,
                    loading = loading,
                    canLoadMore = hasMore,
                    error = error,
                    searched = searched,
                    currentKey = null,
                    selectMode = false,
                    selectedKeys = emptySet(),
                    onPlay = onItemClick,
                    onDownload = { },
                    onLongClick = { },
                    onLoadMore = {
                        if (!loading && hasMore) {
                            scope.launch { loadPage(page + 1, replace = false) }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    resultLayout = source.resultLayout
                )
            }
        }
    }
}