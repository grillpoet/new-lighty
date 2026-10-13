package com.grillpoet.lighty.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.lighty.data.Engine
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.data.SourceStore
import com.grillpoet.lighty.flow.api.FlowResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TabState(
    val items: List<Record> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val lastSourceId: String = ""
) {
    fun isEmpty() = items.isEmpty() && !loading
}

data class CategoryContentState(
    val cid: String? = null,
    val cidTitle: String = "",
    val items: List<Record> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
) {
    val visible: Boolean get() = cid != null
}

/**
 * 主页分类/推荐 tab 的数据。独立于 SearchViewModel。
 *
 * 加载规则：同一源只加载一次。切换源时重新加载。
 */
class HomeTabViewModel(
    private val engine: Engine,
    private val sourceStore: SourceStore
) : ViewModel() {

    private val _recommend = MutableStateFlow(TabState())
    val recommend: StateFlow<TabState> = _recommend.asStateFlow()

    private val _categories = MutableStateFlow(TabState())
    val categories: StateFlow<TabState> = _categories.asStateFlow()

    private val _categoryContent = MutableStateFlow(CategoryContentState())
    val categoryContent: StateFlow<CategoryContentState> = _categoryContent.asStateFlow()

    // ==================== 推荐 ====================

    fun loadRecommendIfNeeded(source: Source) {
        if (!source.hasAction("recommend")) return
        val s = _recommend.value
        if (s.loading) return
        if (s.loaded && s.lastSourceId == source.id) return
        loadRecommend(source)
    }

    fun refreshRecommend(source: Source) {
        loadRecommend(source)
    }

    private fun loadRecommend(source: Source) {
        _recommend.update {
            it.copy(loading = true, error = null, lastSourceId = source.id)
        }
        viewModelScope.launch {
            val result = engine.invoke(source, "recommend", emptyMap())
            result.fold(
                onSuccess = { data ->
                    val list = (data as? FlowResult.Records)?.items ?: emptyList()
                    _recommend.update {
                        it.copy(
                            items = list,
                            loading = false,
                            error = null,
                            loaded = true
                        )
                    }
                },
                onFailure = { e ->
                    _recommend.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "加载失败",
                            loaded = true
                        )
                    }
                }
            )
        }
    }

    // ==================== 分类列表 ====================

    fun loadCategoriesIfNeeded(source: Source) {
        if (!source.hasAction("categories")) return
        val s = _categories.value
        if (s.loading) return
        if (s.loaded && s.lastSourceId == source.id) return
        loadCategories(source)
    }

    private fun loadCategories(source: Source) {
        _categories.update {
            it.copy(loading = true, error = null, lastSourceId = source.id)
        }
        // 切换源时清掉旧的分类内容
        _categoryContent.value = CategoryContentState()

        viewModelScope.launch {
            val result = engine.invoke(source, "categories", emptyMap())
            result.fold(
                onSuccess = { data ->
                    val list = (data as? FlowResult.Records)?.items ?: emptyList()
                    _categories.update {
                        it.copy(
                            items = list,
                            loading = false,
                            error = null,
                            loaded = true
                        )
                    }
                },
                onFailure = { e ->
                    _categories.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "加载失败",
                            loaded = true
                        )
                    }
                }
            )
        }
    }

    // ==================== 分类内容 ====================

    fun loadCategoryContent(source: Source, cid: String, title: String) {
        if (!source.hasAction("categoryContent")) return
        _categoryContent.value = CategoryContentState(
            cid = cid,
            cidTitle = title,
            loading = true
        )
        viewModelScope.launch {
            val result = engine.invoke(
                source,
                "categoryContent",
                mapOf("cid" to cid)
            )
            result.fold(
                onSuccess = { data ->
                    val list = (data as? FlowResult.Records)?.items ?: emptyList()
                    _categoryContent.update {
                        it.copy(items = list, loading = false, error = null)
                    }
                },
                onFailure = { e ->
                    _categoryContent.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "加载失败"
                        )
                    }
                }
            )
        }
    }

    fun exitCategoryContent() {
        _categoryContent.value = CategoryContentState()
    }
}