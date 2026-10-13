package com.grillpoet.lighty.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.core.formatTime
import com.grillpoet.lighty.core.model.LyricLine
import com.grillpoet.lighty.ui.vm.PlayerViewModel
import com.grillpoet.lighty.ui.widget.CoverImage
import com.grillpoet.lighty.ui.widget.LightyControlButton
import com.grillpoet.lighty.ui.widget.LightyIconButton
import com.grillpoet.lighty.ui.widget.LightyProgressSlider
import com.grillpoet.lighty.ui.widget.LightyTopBar
import com.grillpoet.lighty.ui.widget.LyricView

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // false = 显示歌词（默认，和 Mixy 一致）
    // true  = 临时看封面
    var showCover by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.record) { showCover = false }

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.background
                )
            )
        )
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            LightyTopBar(
                title = if (uiState.hasSong)
                    "${uiState.title} - ${uiState.subtitle}"
                else "未播放",
                onBack = onBack,
                backIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                actions = {
                    if (uiState.hasSong) {
                        // 封面 / 歌词 切换
                        LightyIconButton(
                            onClick = { showCover = !showCover },
                            icon = if (showCover) Icons.Rounded.Lyrics
                            else Icons.Rounded.MusicNote,
                            contentDescription = if (showCover) "显示歌词" else "显示封面",
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LightyIconButton(
                            onClick = {
                                val now = viewModel.toggleFavorite()
                                Toast.makeText(
                                    context,
                                    if (now) "已收藏" else "已取消收藏",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            icon = if (isFavorite) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            contentDescription = "收藏",
                            contentColor = if (isFavorite)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            // ★ 关键：这里不再包 clickable，事件直达 LyricView 的 LazyColumn
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (showCover) {
                    AlbumArtwork(coverUrl = uiState.cover)
                } else {
                    LyricSection(
                        viewModel = viewModel,
                        lyrics = uiState.lyrics,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            ControlPanel(
                viewModel = viewModel,
                isPlaying = isPlaying,
                duration = duration,
                hasSong = uiState.hasSong,
                onPlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LyricSection(
    viewModel: PlayerViewModel,
    lyrics: List<LyricLine>,
    modifier: Modifier = Modifier
) {
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    LyricView(
        lyrics = lyrics,
        currentTimestamp = currentPosition,
        modifier = modifier,
        onSeek = viewModel::seekTo
    )
}

@Composable
private fun AlbumArtwork(coverUrl: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CoverImage(
            url = coverUrl,
            modifier = Modifier.size(240.dp),
            shape = RoundedCornerShape(20.dp),
            contentScale = ContentScale.Crop,
            emptyIcon = Icons.Rounded.MusicNote,
            emptyIconSize = 96.dp,
            failureIcon = Icons.Rounded.BrokenImage,
            failureIconSize = 72.dp,
        )
    }
}

@Composable
private fun ControlPanel(
    viewModel: PlayerViewModel,
    isPlaying: Boolean,
    duration: Long,
    hasSong: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        ProgressSection(
            viewModel = viewModel,
            duration = duration,
            onSeek = onSeek
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LightyControlButton(
                onClick = onPlayPause,
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                enabled = hasSong, size = 56.dp, iconSize = 28.dp
            )
        }
    }
}

@Composable
private fun ProgressSection(
    viewModel: PlayerViewModel,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f

    Column(Modifier.fillMaxWidth()) {
        LightyProgressSlider(
            progress = progress,
            onValueChange = { isDragging = true; dragProgress = it },
            onValueChangeFinished = {
                isDragging = false
                if (duration > 0) onSeek((dragProgress * duration).toLong())
            },
            enabled = duration > 0,
            dragProgress = dragProgress,
            isDragging = isDragging
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(if (isDragging) (dragProgress * duration).toLong() else currentPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}