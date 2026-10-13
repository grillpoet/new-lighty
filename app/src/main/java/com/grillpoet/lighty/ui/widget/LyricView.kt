package com.grillpoet.lighty.ui.widget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grillpoet.lighty.core.model.LyricLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LyricView(
    lyrics: List<LyricLine>,
    currentTimestamp: Long,
    modifier: Modifier = Modifier,
    autoScroll: Boolean = true,
    onSeek: ((Long) -> Unit)? = null
) {
    if (lyrics.isEmpty()) {
        EmptyState(
            modifier = modifier,
            icon = Icons.Rounded.MusicNote,
            title = "暂无歌词",
            subtitle = "享受音乐的美好时刻"
        )
        return
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val currentIndex = remember(currentTimestamp, lyrics) {
        lyrics.indexOfLast { it.timestamp <= currentTimestamp }.coerceAtLeast(0)
    }

    var isDragging by remember { mutableStateOf(false) }
    var lastUserSeekAt by remember { mutableLongStateOf(0L) }

    // ============ Mixy 原版：拖动监听 ============
    LaunchedEffect(listState) {
        var userInitiated = false

        launch {
            listState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) {
                    userInitiated = true
                    isDragging = true
                }
            }
        }

        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && isDragging && userInitiated) {
                    delay(200)
                    val info = listState.layoutInfo
                    val centerY = info.viewportSize.height / 2
                    val centerItem = info.visibleItemsInfo.minByOrNull {
                        kotlin.math.abs(it.offset + it.size / 2 - centerY)
                    }
                    val lyricIndex = ((centerItem?.index ?: 1) - 1)
                        .coerceIn(0, lyrics.lastIndex)
                    onSeek?.invoke(lyrics[lyricIndex].timestamp)
                    lastUserSeekAt = System.currentTimeMillis()
                    isDragging = false
                    userInitiated = false
                }
            }
    }

    // ============ Mixy 原版：布局 ============
    BoxWithConstraints(modifier = modifier) {
        val halfHeight = maxHeight / 2
        val viewportPx = with(density) { maxHeight.toPx() }

        val currentItemHeightPx = remember { mutableIntStateOf(0) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "top_spacer") {
                Spacer(Modifier.height(halfHeight))
            }

            itemsIndexed(
                lyrics,
                key = { i, _ -> "lyric_$i" }
            ) { index, line ->
                LyricLineItem(
                    text = line.text,
                    isCurrent = index == currentIndex && !isDragging,
                    itemHeightPx = currentItemHeightPx
                )
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(halfHeight))
            }
        }

        // ============ Mixy 原版：自动滚动 ============
        LaunchedEffect(currentIndex, currentItemHeightPx.intValue, isDragging) {
            if (isDragging) return@LaunchedEffect
            if (currentIndex !in lyrics.indices) return@LaunchedEffect
            if (System.currentTimeMillis() - lastUserSeekAt < 3000) return@LaunchedEffect

            val itemH = currentItemHeightPx.intValue
            val offset = -((viewportPx - itemH) / 2).toInt()

            listState.animateScrollToItem(currentIndex + 1, offset)
        }
    }
}

@Composable
private fun LyricLineItem(
    text: String,
    isCurrent: Boolean,
    itemHeightPx: androidx.compose.runtime.MutableIntState
) {
    val fontSize by animateFloatAsState(
        targetValue = if (isCurrent) 22f else 15f,
        animationSpec = tween(durationMillis = 250),
        label = "lyricFontSize"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.5f,
        animationSpec = tween(durationMillis = 250),
        label = "lyricAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged {
                if (isCurrent) itemHeightPx.intValue = it.height
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSize.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha)
        )
    }
}