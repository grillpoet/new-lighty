package com.grillpoet.lighty.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grillpoet.lighty.data.FavoriteStore
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.SourceStore
import com.grillpoet.lighty.di.AppContainer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class MainViewModel(
    private val container: AppContainer,
    downloadDir: File
) : ViewModel() {

    val search = SearchViewModel(container.engine, container.sourceStore)

    val homeTab = HomeTabViewModel(container.engine, container.sourceStore)

    val player = PlayerViewModel(
        container.player,
        container.engine,
        container.favoriteStore,
        container::setNowPlaying
    )

    val content = ContentViewModel(
        container.engine,
        container.sourceStore,
        container.favoriteStore
    )

    val videoPlayer = VideoPlayerViewModel(container.videoEngine)

    val download = DownloadViewModel(
        app = container.appContext,
        downloader = container.downloader,
        engine = container.engine,
        okHttp = container.okHttp,
        downloadDir = downloadDir,
        appScope = container.appScope
    )

    val sourceStore: SourceStore = container.sourceStore
    val favoriteStore: FavoriteStore = container.favoriteStore

    fun playFromSearch(record: Record) {
        val source = search.currentSource() ?: return
        player.play(source, record, search.state.value.quality)
    }

    fun downloadFromSearch(record: Record) {
        val source = search.currentSource() ?: return
        val quality = source.qualities.firstOrNull { it["value"] == search.state.value.quality }
        download.downloadSingle(source, record, quality)
    }

    fun downloadMultipleFromSearch(records: List<Record>) {
        val source = search.currentSource() ?: return
        val quality = source.qualities.firstOrNull { it["value"] == search.state.value.quality }
        download.downloadMultiple(source, records, quality)
    }

    fun openVideo(sourceId: String, record: Record) {
        content.open(sourceId, record)
    }

    fun openVideoFromFavorite(key: String): Pair<String, Record>? {
        val item = favoriteStore.state.value.firstOrNull { it.first == key }?.second
            ?: return null
        val sourceId = item["sourceId"]?.jsonPrimitive?.contentOrNull ?: return null
        val record = itemToRecord(item)
        content.open(sourceId, record)
        return sourceId to record
    }

    fun playFromFavorites(key: String) {
        val item = favoriteStore.state.value.firstOrNull { it.first == key }?.second ?: return
        val sourceId = item["sourceId"]?.jsonPrimitive?.contentOrNull ?: return
        val source = container.sourceStore.get(sourceId) ?: return
        val record = itemToRecord(item)
        val quality = source.qualities.firstOrNull()?.get("value").orEmpty()
        player.play(source, record, quality)
    }

    private fun itemToRecord(item: JsonObject): Record =
        item.entries
            .filter { it.value is JsonPrimitive }
            .associate { it.key to (it.value as JsonPrimitive).content }

    fun onPause() = player.onPause()
    fun onResume() = player.onResume()

    override fun onCleared() {
        // 允许后台播放；用户划掉 App 时 Service 一起停
    }

    companion object {
        fun factory(c: AppContainer, downloadDir: File): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { MainViewModel(c, downloadDir) }
            }
    }
}