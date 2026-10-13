package com.grillpoet.lighty.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.lighty.core.LyricParser
import com.grillpoet.lighty.core.model.LyricLine
import com.grillpoet.lighty.data.Engine
import com.grillpoet.lighty.data.FavoriteStore
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.flow.api.FlowResult
import com.grillpoet.lighty.player.AndroidPlayer
import com.grillpoet.lighty.player.NowPlaying
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val record: Record? = null,
    val sourceId: String = "",
    val url: String? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
) {
    val title: String get() = record?.get("title").orEmpty().ifBlank { "未播放" }
    val subtitle: String get() = record?.get("subtitle").orEmpty()
    val cover: String get() = record?.get("cover").orEmpty()
    val hasSong: Boolean get() = record != null
}

class PlayerViewModel(
    private val player: AndroidPlayer,
    private val engine: Engine,
    private val favoriteStore: FavoriteStore,
    private val setNowPlaying: (NowPlaying?) -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val isPlaying: StateFlow<Boolean> = player.isPlaying
    val currentPosition: StateFlow<Long> = player.currentPosition
    val duration: StateFlow<Long> = player.duration

    val isFavorite: StateFlow<Boolean> = combine(
        _state.map { it.record to it.sourceId },
        favoriteStore.state
    ) { (record, sourceId), _ ->
        if (record == null) false else favoriteStore.contains(sourceId, record)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var pausedByLifecycle = false
    private var playJob: Job? = null

    fun play(source: Source, record: Record, quality: String) {
        playJob?.cancel()
        _state.update {
            it.copy(
                record = record, sourceId = source.id, url = null,
                lyrics = emptyList(), loading = true, error = null
            )
        }

        playJob = viewModelScope.launch {
            val (url, lyricText) = coroutineScope {
                val u = async {
                    val direct = record["playUrl"]
                    if (!direct.isNullOrBlank() && !source.hasAction("play")) {
                        direct
                    } else {
                        extractUrl(
                            engine.invoke(
                                source, "play",
                                record + mapOf("quality" to quality)
                            ).getOrNull()
                        )
                    }
                }
                val l = async {
                    if (source.hasAction("lyric")) {
                        extractText(engine.invoke(source, "lyric", record).getOrNull())
                    } else ""
                }
                u.await().trim() to l.await()
            }

            if (url.isBlank()) {
                _state.update {
                    it.copy(loading = false, error = "无法获取播放地址")
                }
                return@launch
            }

            val lyrics = LyricParser.parse(lyricText)
            _state.update { it.copy(url = url, lyrics = lyrics, loading = false) }
            setNowPlaying(
                NowPlaying(
                    title = record["title"].orEmpty(),
                    subtitle = record["subtitle"].orEmpty(),
                    cover = record["cover"].orEmpty()
                )
            )
            player.play(url)
        }
    }

    fun toggleFavorite(): Boolean {
        val record = _state.value.record ?: return false
        val sourceId = _state.value.sourceId
        if (sourceId.isBlank()) return false
        return favoriteStore.toggle(sourceId, record)
    }

    fun togglePlayPause() {
        if (player.isPlaying.value) {
            player.pause()
            pausedByLifecycle = false
        } else {
            player.resume()
        }
    }

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun stop() {
        player.stop()
        pausedByLifecycle = false
        _state.value = PlayerUiState()
        setNowPlaying(null)
    }

    fun onPause() {
        if (player.isPlaying.value) {
            player.pause()
            pausedByLifecycle = true
        }
    }

    fun onResume() {
        if (pausedByLifecycle && _state.value.hasSong) {
            player.resume()
            pausedByLifecycle = false
        }
    }

    private fun extractUrl(r: FlowResult?): String = when (r) {
        is FlowResult.Url -> r.value
        is FlowResult.Raw -> r.body
        is FlowResult.Text -> r.content
        else -> ""
    }

    private fun extractText(r: FlowResult?): String = when (r) {
        is FlowResult.Text -> r.content
        is FlowResult.Raw -> r.body
        else -> ""
    }
}