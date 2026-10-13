package com.grillpoet.lighty.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.lighty.data.Engine
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.data.SourceStore
import com.grillpoet.lighty.flow.api.FlowResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val sourceId: String = "",
    val action: String = "search",
    val input: String = "",
    val items: List<Record> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false,
    val quality: String = ""
) {
    val canLoadMore: Boolean
        get() = hasMore && !loading && error == null && searched
}

class SearchViewModel(
    private val engine: Engine,
    private val sourceStore: SourceStore
) : ViewModel() {

    private companion object {
        const val MAX_PAGES = 50
    }

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val sources: StateFlow<List<Source>> = sourceStore.sources

    private var job: Job? = null

    init {
        val first = sourceStore.sources.value.firstOrNull()
        if (first != null) selectSource(first.id)
    }

    fun currentSource(): Source? = sourceStore.get(_state.value.sourceId)

    fun selectSource(id: String) {
        if (_state.value.sourceId == id) return
        job?.cancel()
        val source = sourceStore.get(id)
        val defaultQuality = source?.qualities?.firstOrNull()?.get("value").orEmpty()
        val defaultAction = if (source?.hasAction("search") == true) "search" else ""
        _state.value = SearchUiState(
            sourceId = id,
            action = defaultAction,
            quality = defaultQuality
        )
    }

    fun updateInput(text: String) {
        _state.update { it.copy(input = text, error = null) }
    }

    fun setQuality(value: String) {
        val changed = _state.value.quality != value
        _state.update { it.copy(quality = value) }
        if (changed && _state.value.searched && _state.value.input.isNotBlank()) {
            search()
        }
    }

    fun search() {
        val input = _state.value.input.trim()
        if (input.isEmpty()) {
            _state.update { it.copy(error = "请输入关键词") }
            return
        }
        val source = currentSource() ?: run {
            _state.update { it.copy(error = "未选择源") }
            return
        }
        if (!source.hasAction(_state.value.action)) {
            _state.update { it.copy(error = "源不支持搜索") }
            return
        }

        job?.cancel()
        _state.update {
            it.copy(
                items = emptyList(),
                page = 0,
                hasMore = true,
                loading = true,
                error = null,
                searched = true
            )
        }
        job = viewModelScope.launch { loadPage(0, replace = true) }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.canLoadMore) return
        if (s.page + 1 >= MAX_PAGES) {
            _state.update { it.copy(hasMore = false) }
            return
        }
        _state.update { it.copy(loading = true) }
        job = viewModelScope.launch { loadPage(s.page + 1, replace = false) }
    }

    private suspend fun loadPage(page: Int, replace: Boolean) {
        val source = currentSource() ?: return
        val s = _state.value

        val pageSize = source.pageSize.takeIf { it > 0 } ?: 20

        val args = mutableMapOf(
            "keyword" to s.input.trim(),
            "page" to page.toString(),
            "page1" to (page + 1).toString(),
            "offset" to (page * pageSize).toString(),
            "limit" to pageSize.toString(),
            "quality" to s.quality
        )

        val result = engine.invoke(source, s.action, args)

        result
            .onSuccess { flowResult ->
                val list: List<Record> = when (flowResult) {
                    is FlowResult.Records -> flowResult.items
                    else -> emptyList()
                }

                _state.update { cur ->
                    if (replace) {
                        return@update cur.copy(
                            items = list,
                            page = page,
                            hasMore = list.isNotEmpty(),
                            loading = false,
                            error = null
                        )
                    }

                    val seen = HashSet<String>(cur.items.size + list.size)
                    cur.items.forEach { seen.add(stableKey(it)) }
                    val newItems = ArrayList<Record>(list.size)
                    for (rec in list) {
                        val k = stableKey(rec)
                        if (k.isBlank() || seen.add(k)) newItems.add(rec)
                    }

                    val allDuplicates = list.isNotEmpty() && newItems.isEmpty()
                    val hasMore = when {
                        list.isEmpty() -> false
                        allDuplicates -> false
                        else -> true
                    }

                    cur.copy(
                        items = cur.items + newItems,
                        page = page,
                        hasMore = hasMore,
                        loading = false,
                        error = null
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "加载失败",
                        hasMore = false
                    )
                }
            }
    }

    private fun stableKey(r: Record): String =
        r["_key"] ?: r["rid"] ?: r["playUrl"] ?: "${r["title"]}|${r["subtitle"]}"
}