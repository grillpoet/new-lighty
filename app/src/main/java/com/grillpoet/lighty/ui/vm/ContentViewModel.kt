package com.grillpoet.lighty.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.lighty.data.Engine
import com.grillpoet.lighty.data.FavoriteStore
import com.grillpoet.lighty.data.MediaKind
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.SourceStore
import com.grillpoet.lighty.flow.api.FlowResult
import com.grillpoet.lighty.playback.Episode
import com.grillpoet.lighty.playback.EpisodeParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContentUiState(
    val title: String = "",
    val subtitle: String = "",
    val cover: String = "",
    val episodes: List<Episode> = emptyList(),
    val currentIndex: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val record: Record = emptyMap(),
    val sourceId: String = "",
    val isManga: Boolean = false,
    val isNovel: Boolean = false,
    val content: String = ""
) {
    val currentEpisode: Episode? get() = episodes.getOrNull(currentIndex)
}

class ContentViewModel(
    private val engine: Engine,
    private val sourceStore: SourceStore,
    private val favoriteStore: FavoriteStore
) : ViewModel() {

    private val _state = MutableStateFlow(ContentUiState())
    val state: StateFlow<ContentUiState> = _state.asStateFlow()

    private var currentRecord: Record = emptyMap()

    val isFavorite: StateFlow<Boolean> = combine(
        _state.map { it.record to it.sourceId },
        favoriteStore.state
    ) { pair, _ ->
        val (record, sourceId) = pair
        if (record.isEmpty() || sourceId.isBlank()) false
        else favoriteStore.contains(sourceId, record)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ==================== 打开 ====================

    fun open(sourceId: String, record: Record) {
        viewModelScope.launch {
            _state.value = ContentUiState(
                title = record["title"].orEmpty(),
                subtitle = record["subtitle"].orEmpty(),
                cover = record["cover"].orEmpty(),
                record = record,
                sourceId = sourceId,
                loading = true
            )

            var current = record
            val source = sourceStore.get(sourceId)
            val isManga = record["_type"] == "manga"
            val isNovel = source?.kind == MediaKind.NOVEL
            val hasPlay = !current["playUrl"].isNullOrBlank()

            // 先从 record 自带的 episodes_json 解析
            var episodes: List<Episode> = EpisodeParser.parse(current["episodes_json"])

            // 没有的话，调 detail
            if (episodes.isEmpty() && !hasPlay && source?.hasAction("detail") == true) {
                val detail = engine.invoke(source, "detail", current).getOrNull()
                when (detail) {
                    is FlowResult.Episodes -> {
                        episodes = detail.items.map { Episode(name = it.name, url = it.url) }
                    }
                    is FlowResult.Records -> {
                        val rec = detail.items.firstOrNull()
                        if (rec != null) {
                            current = current + rec
                            episodes = EpisodeParser.parse(rec["episodes_json"])
                        }
                    }
                    else -> {}
                }
            }

            currentRecord = current

            _state.value = ContentUiState(
                title = current["title"].orEmpty(),
                subtitle = current["subtitle"].orEmpty(),
                cover = current["cover"].orEmpty(),
                episodes = episodes,
                currentIndex = 0,
                loading = false,
                record = current,
                sourceId = sourceId,
                isManga = isManga,
                isNovel = isNovel
            )

            when {
                isNovel && episodes.isNotEmpty() -> loadChapterContent(0)
                isManga && episodes.isNotEmpty() && source?.hasAction("chapter") == true ->
                    loadChapterPages(0)
                isManga -> _state.update {
                    it.copy(error = "源未配置 chapter action")
                }
            }
        }
    }

    fun selectEpisode(index: Int) {
        val s = _state.value
        val ep = s.episodes.getOrNull(index) ?: return
        _state.update { it.copy(currentIndex = index) }

        val source = sourceStore.get(s.sourceId) ?: return

        when {
            s.isNovel -> loadChapterContent(index)
            s.isManga && source.hasAction("chapter") -> loadChapterPages(index)
        }
    }

    // ==================== 收藏 ====================

    fun toggleFavorite(): Boolean {
        val record = _state.value.record
        val sourceId = _state.value.sourceId
        if (record.isEmpty() || sourceId.isBlank()) return false
        return favoriteStore.toggle(sourceId, record)
    }

    // ==================== 播放地址解析 ====================

    suspend fun resolveVideoPlayUrl(): String? {
        val s = _state.value
        val ep = s.currentEpisode ?: return null

        // 1. record 自带直链
        s.record["playUrl"]?.takeIf { it.isNotBlank() }?.let { return it }

        val source = sourceStore.get(s.sourceId)
            ?: return ep.url.takeIf { it.isNotBlank() }

        // 2. 源有 play，调一次换真实地址
        if (source.hasAction("play")) {
            val args = s.record + mapOf(
                "url" to ep.url,
                "chapterId" to ep.url,
                "rid" to ep.url,
                "quality" to "default"
            )
            val result = engine.invoke(source, "play", args).getOrNull()
            val url = extractPlayUrl(result).takeIf { it.isNotBlank() }
            return url ?: ep.url.takeIf { it.isNotBlank() }
        }

        // 3. 没有 play，直接把 ep.url 当播放地址
        return ep.url.takeIf { it.isNotBlank() }
    }

    suspend fun resolveEpisodeUrl(): Triple<String, String, String>? {
        val s = _state.value
        val ep = s.currentEpisode
        val url = resolveVideoPlayUrl() ?: return null
        return Triple(s.title, ep?.name ?: "视频", url)
    }

    // ==================== 清理 ====================

    fun clear() {
        _state.value = ContentUiState()
        currentRecord = emptyMap()
    }

    // ==================== 内部：漫画 / 小说 ====================

    private fun loadChapterPages(index: Int) {
        val s = _state.value
        val ep = s.episodes.getOrNull(index) ?: return
        val source = sourceStore.get(s.sourceId) ?: return

        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val args = mapOf("chapterId" to ep.url, "url" to ep.url)
            val result = engine.invoke(source, "chapter", args).getOrNull()
            val pages = when (result) {
                is FlowResult.Pages -> result.urls
                is FlowResult.Episodes -> result.items.map { it.url }
                else -> emptyList()
            }

            _state.update { cur ->
                val newEpisodes = cur.episodes.toMutableList()
                if (index in newEpisodes.indices) {
                    newEpisodes[index] = ep.copy(pages = pages)
                }
                cur.copy(
                    episodes = newEpisodes,
                    loading = false,
                    error = if (pages.isEmpty()) "本章无可读图片" else null
                )
            }
        }
    }

    private fun loadChapterContent(index: Int) {
        val s = _state.value
        val ep = s.episodes.getOrNull(index) ?: return
        val source = sourceStore.get(s.sourceId) ?: return
        if (!source.hasAction("content")) return

        viewModelScope.launch {
            _state.update { it.copy(loading = true, content = "") }
            val args = mapOf("chapterId" to ep.url, "url" to ep.url)
            val result = engine.invoke(source, "content", args).getOrNull()
            val text = when (result) {
                is FlowResult.Text -> result.content
                is FlowResult.Raw -> result.body
                else -> ""
            }
            _state.update {
                it.copy(
                    content = text,
                    loading = false,
                    error = if (text.isBlank()) "正文为空" else null
                )
            }
        }
    }

    // ==================== 工具 ====================

    /** play 动作可能返回 Url（JSON 直链）、Raw（纯文本直链）、Text（字符串） */
    private fun extractPlayUrl(r: FlowResult?): String = when (r) {
        is FlowResult.Url -> r.value
        is FlowResult.Raw -> r.body
        is FlowResult.Text -> r.content
        else -> ""
    }
}