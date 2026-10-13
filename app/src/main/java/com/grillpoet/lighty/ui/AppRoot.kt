package com.grillpoet.lighty.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.LightyApp
import com.grillpoet.lighty.data.MediaKind
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.flow.api.FlowResult
import com.grillpoet.lighty.nav.Navigator
import com.grillpoet.lighty.nav.Screen
import com.grillpoet.lighty.ui.screen.ConfigScreen
import com.grillpoet.lighty.ui.screen.DetailScreen
import com.grillpoet.lighty.ui.screen.DownloadsScreen
import com.grillpoet.lighty.ui.screen.FavoritesScreen
import com.grillpoet.lighty.ui.screen.HomeScreen
import com.grillpoet.lighty.ui.screen.MangaReaderScreen
import com.grillpoet.lighty.ui.screen.NovelReaderScreen
import com.grillpoet.lighty.ui.screen.PlayerScreen
import com.grillpoet.lighty.ui.screen.SourcePageScreen
import com.grillpoet.lighty.ui.screen.VideoPlayerScreen
import com.grillpoet.lighty.ui.vm.MainViewModel
import com.grillpoet.lighty.ui.widget.MiniPlayer
import com.grillpoet.lighty.ui.widget.debug.DebugPanel
import kotlinx.coroutines.launch

@Composable
fun AppRoot(
    mainVm: MainViewModel,
    modifier: Modifier = Modifier
) {
    val nav = remember { Navigator() }
    val context = LocalContext.current
    val container = remember { (context.applicationContext as LightyApp).container }
    val uiScope = rememberCoroutineScope()

    val playerState by mainVm.player.state.collectAsStateWithLifecycle()
    val isPlaying by mainVm.player.isPlaying.collectAsStateWithLifecycle()
    val downloadMessage by mainVm.download.message.collectAsStateWithLifecycle()

    val sources: List<Source> by mainVm.search.sources.collectAsStateWithLifecycle()

    LaunchedEffect(downloadMessage) {
        downloadMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            mainVm.download.consumeMessage()
        }
    }

    BackHandler(enabled = true) {
        if (!nav.pop()) (context as? Activity)?.finishAndRemoveTask()
    }

    val bottomInset = if (playerState.hasSong) 60.dp else 0.dp

    // ==================== 点击内容 ====================
    val onPlayResult: (Record) -> Unit = { record ->
        val sourceId = mainVm.search.state.value.sourceId
        val source = sources.firstOrNull { it.id == sourceId }

        val type = record["_type"] ?: when (source?.kind) {
            MediaKind.MUSIC -> "music"
            MediaKind.VIDEO -> "video"
            MediaKind.NOVEL -> "novel"
            MediaKind.IMAGE -> "manga"
            null -> null
        }

        when (type) {
            "video", "manga", "novel" -> {
                mainVm.openVideo(sourceId, record)
                nav.push(Screen.Detail(sourceId, record))
            }
            else -> mainVm.playFromSearch(record)
        }
    }

    // ==================== 动作按钮 ====================
    val onActionButtonClick: (Map<String, String>, Record) -> Unit = { btn, record ->
        val action = btn["action"].orEmpty()
        val label = btn["label"].orEmpty().ifBlank { btn["id"].orEmpty() }

        btn["toast"]?.takeIf { it.isNotBlank() }?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }

        val currentScreen = nav.current
        val sourceId = when (currentScreen) {
            is Screen.Detail -> currentScreen.sourceId
            is Screen.Video -> currentScreen.sourceId
            is Screen.Manga -> currentScreen.sourceId
            is Screen.Novel -> currentScreen.sourceId
            else -> mainVm.search.state.value.sourceId
        }
        val source = sources.firstOrNull { it.id == sourceId }

        if (source == null || !source.hasAction(action)) {
            Toast.makeText(context, "源不支持：$action", Toast.LENGTH_SHORT).show()
        } else {
            uiScope.launch {
                val result = container.engine.invoke(source, action, record)
                result.fold(
                    onSuccess = { data ->
                        when (data) {
                            is FlowResult.Records -> {
                                if (data.items.isEmpty()) {
                                    Toast.makeText(context, "无结果", Toast.LENGTH_SHORT).show()
                                } else {
                                    nav.push(
                                        Screen.Page(
                                            sourceId = source.id,
                                            title = label,
                                            action = action,
                                            args = record
                                        )
                                    )
                                }
                            }

                            is FlowResult.Url -> {
                                val url = data.value
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, url.toUri())
                                        )
                                    }.onFailure {
                                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                                    }
                                } else if (url.isNotBlank()) {
                                    Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
                                }
                            }

                            is FlowResult.Text -> {
                                if (data.content.isNotBlank()) {
                                    Toast.makeText(context, data.content, Toast.LENGTH_SHORT).show()
                                }
                            }

                            is FlowResult.Episodes -> Unit
                            is FlowResult.Pages -> Unit
                            is FlowResult.Raw -> Unit
                        }
                    },
                    onFailure = { e ->
                        Toast.makeText(
                            context,
                            e.message ?: "操作失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val screen = nav.current) {
            is Screen.Main -> Box(modifier = modifier.fillMaxSize()) {
                HomeScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(bottom = bottomInset),
                    searchVm = mainVm.search,
                    homeTabVm = mainVm.homeTab,
                    onOpenDownloadManager = { nav.push(Screen.Downloads) },
                    onOpenFavorites = { nav.push(Screen.Favorites) },
                    onOpenConfig = { nav.push(Screen.Config) },
                    onClearAll = { container.sourceStore.deleteAll() },
                    onPlay = onPlayResult,
                    onDownload = { mainVm.downloadFromSearch(it) },
                    onDownloadMultiple = { mainVm.downloadMultipleFromSearch(it) },
                    onActionButtonClick = onActionButtonClick
                )

                if (playerState.hasSong) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        MiniPlayer(
                            title = playerState.title,
                            subtitle = playerState.subtitle,
                            cover = playerState.cover,
                            isPlaying = isPlaying,
                            onPlayPauseClick = mainVm.player::togglePlayPause,
                            onClick = { nav.push(Screen.Player) }
                        )
                    }
                }
            }

            is Screen.Player -> key(screen) {
                PlayerScreen(
                    viewModel = mainVm.player,
                    onBack = { nav.pop() },
                    modifier = modifier
                )
            }

            is Screen.Detail -> key(screen) {
                val source = sources.firstOrNull { it.id == screen.sourceId }
                DetailScreen(
                    source = source,
                    contentVm = mainVm.content,
                    onBack = { nav.pop() },
                    onPlayEpisode = { index ->
                        mainVm.content.selectEpisode(index)
                        val record = screen.record
                        val type = record["_type"] ?: when (source?.kind) {
                            MediaKind.MUSIC -> "music"
                            MediaKind.VIDEO -> "video"
                            MediaKind.NOVEL -> "novel"
                            MediaKind.IMAGE -> "manga"
                            null -> null
                        }
                        when (type) {
                            "manga" -> nav.push(Screen.Manga(screen.sourceId, record))
                            "novel" -> nav.push(Screen.Novel(screen.sourceId, record))
                            else -> nav.push(Screen.Video(screen.sourceId, record))
                        }
                    },
                    onDownloadEpisode = {
                        uiScope.launch {
                            mainVm.content.resolveEpisodeUrl()?.let { (t, e, u) ->
                                mainVm.download.downloadVideoEpisode(t, e, u)
                            }
                        }
                    },
                    onActionButtonClick = onActionButtonClick,
                    modifier = modifier
                )
            }

            is Screen.Video -> key(screen) {
                VideoPlayerScreen(
                    contentVm = mainVm.content,
                    playerVm = mainVm.videoPlayer,
                    source = sources.firstOrNull { it.id == screen.sourceId },
                    onBack = { nav.pop() },
                    onDownloadEpisode = { title: String, epName: String, url: String ->
                        mainVm.download.downloadVideoEpisode(title, epName, url)
                    },
                    onActionButtonClick = onActionButtonClick,
                    modifier = modifier
                )
            }

            is Screen.Manga -> key(screen) {
                MangaReaderScreen(
                    viewModel = mainVm.content,
                    source = sources.firstOrNull { it.id == screen.sourceId },
                    onBack = { nav.pop() },
                    onDownloadImages = { albumName, pages ->
                        mainVm.download.downloadImages(albumName, pages)
                    },
                    onActionButtonClick = onActionButtonClick,
                    modifier = modifier
                )
            }

            is Screen.Novel -> key(screen) {
                NovelReaderScreen(
                    viewModel = mainVm.content,
                    source = sources.firstOrNull { it.id == screen.sourceId },
                    onBack = { nav.pop() },
                    onActionButtonClick = onActionButtonClick,
                    modifier = modifier
                )
            }

            is Screen.Page -> key(screen) {
                val source = sources.firstOrNull { it.id == screen.sourceId }
                if (source == null) {
                    LaunchedEffect(Unit) { nav.pop() }
                } else {
                    SourcePageScreen(
                        source = source,
                        title = screen.title,
                        action = screen.action,
                        args = screen.args,
                        engine = container.engine,
                        onBack = { nav.pop() },
                        onItemClick = onPlayResult,
                        modifier = modifier
                    )
                }
            }

            is Screen.Favorites -> key(screen) {
                FavoritesScreen(
                    mainVm = mainVm,
                    onBack = { nav.pop() },
                    onPlayVideo = { key ->
                        mainVm.openVideoFromFavorite(key)?.let { (sourceId, record) ->
                            nav.pop()
                            val type = record["_type"]
                            when (type) {
                                "manga" -> nav.push(Screen.Manga(sourceId, record))
                                "novel" -> nav.push(Screen.Novel(sourceId, record))
                                else -> nav.push(Screen.Video(sourceId, record))
                            }
                        }
                    },
                    modifier = modifier
                )
            }

            is Screen.Downloads -> key(screen) {
                DownloadsScreen(
                    viewModel = mainVm.download,
                    onBack = { nav.pop() },
                    modifier = modifier
                )
            }

            is Screen.Config -> key(screen) {
                ConfigScreen(
                    mainVm = mainVm,
                    onBack = { nav.pop() },
                    modifier = modifier
                )
            }
        }

        DebugPanel(Modifier.fillMaxSize())
    }
}