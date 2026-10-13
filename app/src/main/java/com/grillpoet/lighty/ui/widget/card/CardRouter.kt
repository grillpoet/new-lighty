package com.grillpoet.lighty.ui.widget.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.grillpoet.lighty.data.Record
import com.grillpoet.lighty.ui.widget.VideoCard

/**
 * 网格卡片分发。按 record["_type"] 选择卡片样式：
 *  - "novel" → NovelCard（横向）
 *  - 其他（video / manga / 未知）→ VideoCard（竖版海报）
 *
 * 音乐走独立的 SongItem，不经过这里。
 */
@Composable
fun GridCard(
    record: Record,
    isCurrent: Boolean,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverRatio: Float = 0.667f,
    showSubtitle: Boolean = true
) {
    val title = record["title"].orEmpty()
    val subtitle = record["subtitle"].orEmpty()
    val cover = record["cover"].orEmpty()
    val source = record["_source"].orEmpty()
    val type = record["_type"]

    if (type == "novel") {
        NovelCard(
            title = title,
            subtitle = subtitle,
            cover = cover,
            source = source,
            isCurrent = isCurrent,
            selectMode = selectMode,
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
        )
    } else {
        VideoCard(
            title = title,
            subtitle = subtitle,
            cover = cover,
            isCurrent = isCurrent,
            selectMode = selectMode,
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            source = source,
            coverRatio = coverRatio,
            showSubtitle = showSubtitle
        )
    }
}