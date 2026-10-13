package com.grillpoet.lighty.ui.screen

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import com.grillpoet.lighty.core.formatTime
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.data.Source
import com.grillpoet.lighty.ui.vm.ContentViewModel
import com.grillpoet.lighty.ui.vm.VideoPlayerViewModel
import com.grillpoet.lighty.ui.widget.ActionIconBar
import com.grillpoet.lighty.ui.widget.EpisodeSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val PLAYER_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val CONTROLS_HIDE_DELAY_MS = 3500L
private const val DRAG_THRESHOLD_PX = 30f
private const val SEEK_MS_PER_SCREEN = 90_000L

/**
 * 视频播放页。
 *
 * 布局：
 *  - 播放器占满全屏
 *  - 顶部控制栏：返回、标题、源按钮、倍速、循环、旋转、收藏
 *  - 底部控制栏：播放/暂停、下载、选集、全屏
 *  - 选集点击弹出底部 EpisodeSheet
 */
@Composable
fun VideoPlayerScreen(
    contentVm: ContentViewModel,
    playerVm: VideoPlayerViewModel,
    source: Source?,
    onBack: () -> Unit,
    onDownloadEpisode: (title: String, episodeName: String, url: String) -> Unit,
    onActionButtonClick: (Map<String, String>, Record) -> Unit,
    modifier: Modifier = Modifier
) {
    val contentState by contentVm.state.collectAsStateWithLifecycle()
    val playerState by playerVm.state.collectAsStateWithLifecycle()
    val isPlaying by playerVm.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by playerVm.isBuffering.collectAsStateWithLifecycle()
    val duration by playerVm.duration.collectAsStateWithLifecycle()
    val position by playerVm.currentPosition.collectAsStateWithLifecycle()
    val isFav by contentVm.isFavorite.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }

    // ============ 控制栏可见性 ============
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
    val touch: () -> Unit = { lastInteraction = System.currentTimeMillis() }

    // ============ 倍速 / 循环 / 旋转 ============
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var orientationManuallySet by remember { mutableStateOf(false) }
    var autoOrientationApplied by remember(contentState.currentIndex) { mutableStateOf(false) }

    // ============ 选集面板 ============
    var showEpisodeSheet by remember { mutableStateOf(false) }

    // ============ 滑动 seek 预览 ============
    var dragSeekPreview by remember { mutableStateOf<Long?>(null) }
    var dragStartPosition by remember { mutableLongStateOf(0L) }

    // ============ 播放地址解析 ============
    LaunchedEffect(contentState.currentIndex, contentState.episodes.size) {
        if (contentState.isManga || contentState.isNovel) return@LaunchedEffect
        val url = contentVm.resolveVideoPlayUrl() ?: return@LaunchedEffect
        if (url.isNotBlank()) {
            playerVm.play(url)
            touch()
            controlsVisible = true
        }
    }

    LaunchedEffect(speed) {
        runCatching { playerVm.exoPlayer.setPlaybackSpeed(speed) }
    }
    LaunchedEffect(repeatMode) {
        runCatching { playerVm.exoPlayer.repeatMode = repeatMode }
    }

    // ============ 视频尺寸 / 自动方向 ============
    DisposableEffect(playerVm.exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width == 0 || videoSize.height == 0) return
                val ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) /
                        videoSize.height
                videoAspect = ratio

                if (playerState.isFullscreen &&
                    !orientationManuallySet && !autoOrientationApplied
                ) {
                    val act = activity ?: return
                    act.requestedOrientation = if (ratio > 1.05f) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    autoOrientationApplied = true
                }
            }
        }
        playerVm.exoPlayer.addListener(listener)
        onDispose { playerVm.exoPlayer.removeListener(listener) }
    }

    // ============ 全屏：系统栏 + 方向 ============
    LaunchedEffect(playerState.isFullscreen, videoAspect) {
        val act = activity ?: return@LaunchedEffect
        val window = act.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (playerState.isFullscreen) {
            if (!orientationManuallySet) {
                act.requestedOrientation = if (videoAspect > 1f) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ============ 控制栏自动隐藏 ============
    LaunchedEffect(controlsVisible, lastInteraction, isPlaying) {
        if (!controlsVisible || !isPlaying) return@LaunchedEffect
        delay(CONTROLS_HIDE_DELAY_MS)
        if (System.currentTimeMillis() - lastInteraction >= CONTROLS_HIDE_DELAY_MS) {
            controlsVisible = false
        }
    }

    BackHandler {
        if (playerState.isFullscreen) {
            playerVm.setFullscreen(false)
        } else {
            playerVm.stop()
            onBack()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // ==================== 播放器（占满全屏）====================
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerVm.exoPlayer
                    useController = false
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = playerVm.exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        // ==================== 手势层 ====================
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var totalDx = 0f
                        var lastX = down.position.x

                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) break
                            val change = event.changes.firstOrNull() ?: continue
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            totalDx += dx

                            if (!dragging && abs(totalDx) > DRAG_THRESHOLD_PX) {
                                dragging = true
                                dragStartPosition = playerVm.currentPosition.value
                                dragSeekPreview = dragStartPosition
                                lastInteraction = System.currentTimeMillis()
                            }

                            if (dragging) {
                                change.consume()
                                val deltaMs = (dx / size.width * SEEK_MS_PER_SCREEN).toLong()
                                val preview = ((dragSeekPreview ?: dragStartPosition) + deltaMs)
                                    .coerceIn(0L, duration.coerceAtLeast(0L))
                                dragSeekPreview = preview
                                lastInteraction = System.currentTimeMillis()
                            }
                        }

                        if (dragging) {
                            dragSeekPreview?.let { playerVm.seekTo(it) }
                            dragSeekPreview = null
                        } else {
                            controlsVisible = !controlsVisible
                            lastInteraction = System.currentTimeMillis()
                        }
                    }
                }
        )

        // ==================== 拖动 seek 预览浮层 ====================
        dragSeekPreview?.let { preview ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${formatTime(preview)} / ${formatTime(duration)}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val delta = preview - dragStartPosition
                    val sign = if (delta >= 0) "+" else "-"
                    Text(
                        text = "$sign${abs(delta) / 1000} 秒",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // ==================== 控制层 ====================
        if (controlsVisible) {
            Box(Modifier.fillMaxSize()) {
                // 顶部栏
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        touch()
                        playerVm.stop()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White)
                    }
                    Text(
                        text = contentState.title.ifBlank { "视频" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    ActionIconBar(
                        source = source,
                        scope = "video",
                        onAction = { btn ->
                            touch()
                            onActionButtonClick(btn, contentState.record)
                        }
                    )

                    // 倍速
                    Box {
                        IconButton(onClick = { speedMenuOpen = true; touch() }) {
                            Icon(Icons.Rounded.Speed, "倍速", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = speedMenuOpen,
                            onDismissRequest = { speedMenuOpen = false }
                        ) {
                            PLAYER_SPEEDS.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s}x") },
                                    onClick = {
                                        speed = s
                                        speedMenuOpen = false
                                        touch()
                                    }
                                )
                            }
                        }
                    }

                    // 循环
                    IconButton(onClick = {
                        touch()
                        repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }) {
                        Icon(
                            imageVector = if (repeatMode == Player.REPEAT_MODE_ONE)
                                Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "循环",
                            tint = if (repeatMode == Player.REPEAT_MODE_OFF)
                                Color.White.copy(alpha = 0.5f) else Color.White
                        )
                    }

                    // 旋转
                    IconButton(onClick = {
                        touch()
                        orientationManuallySet = true
                        val cur = activity?.requestedOrientation
                            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        activity?.requestedOrientation = when (cur) {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ->
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ->
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    }) {
                        Icon(Icons.Rounded.ScreenRotation, "旋转", tint = Color.White)
                    }

                    // 收藏
                    IconButton(onClick = {
                        touch()
                        val now = contentVm.toggleFavorite()
                        Toast.makeText(
                            context,
                            if (now) "已收藏" else "已取消收藏",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (isFav) Color(0xFFFF4D6D) else Color.White
                        )
                    }
                }

                // 中央状态
                when {
                    isBuffering -> CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                    )

                    !isPlaying -> Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    touch()
                                    playerVm.togglePlayPause()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            "播放",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // 底部进度 + 控件
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    VideoProgressBar(
                        viewModel = playerVm,
                        duration = duration,
                        position = position,
                        onTouch = touch
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            touch()
                            playerVm.togglePlayPause()
                        }) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause
                                else Icons.Rounded.PlayArrow,
                                "播放/暂停",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            touch()
                            scope.launch {
                                contentVm.resolveEpisodeUrl()?.let { (t, e, u) ->
                                    onDownloadEpisode(t, e, u)
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.Download, "下载", tint = Color.White)
                        }

                        // 选集
                        if (contentState.episodes.isNotEmpty()) {
                            IconButton(onClick = {
                                touch()
                                showEpisodeSheet = true
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.List,
                                    "选集",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        IconButton(onClick = {
                            touch()
                            playerVm.setFullscreen(!playerState.isFullscreen)
                        }) {
                            Icon(
                                if (playerState.isFullscreen) Icons.Rounded.FullscreenExit
                                else Icons.Rounded.Fullscreen,
                                "全屏",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // ==================== 选集面板 ====================
        if (showEpisodeSheet) {
            EpisodeSheet(
                episodes = contentState.episodes,
                currentIndex = contentState.currentIndex,
                onSelect = { index -> contentVm.selectEpisode(index) },
                onDismiss = { showEpisodeSheet = false }
            )
        }
    }
}

// ==================== 进度条 ====================

@Composable
private fun VideoProgressBar(
    viewModel: VideoPlayerViewModel,
    duration: Long,
    position: Long,
    onTouch: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val displayPosition = if (isDragging) (dragProgress * duration).toLong() else position
    val displayProgress = when {
        isDragging -> dragProgress
        duration > 0 -> position.toFloat() / duration
        else -> 0f
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatTime(displayPosition),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
        Slider(
            value = displayProgress.coerceIn(0f, 1f),
            onValueChange = {
                isDragging = true
                dragProgress = it
                onTouch()
            },
            onValueChangeFinished = {
                isDragging = false
                if (duration > 0) {
                    viewModel.seekTo((dragProgress * duration).toLong())
                }
                onTouch()
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
        Text(
            formatTime(duration),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}