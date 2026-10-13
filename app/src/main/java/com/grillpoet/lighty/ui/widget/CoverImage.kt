package com.grillpoet.lighty.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.grillpoet.lighty.core.normalizeCover

/**
 * 统一封面展示组件。
 *
 * 三种状态视觉区分：
 *   - 无 URL：灰底 + 音符图标（淡）
 *   - 加载中：灰底（无图标）
 *   - 加载失败：灰底 + 破图图标（略深，可加淡红调）
 */
@Composable
fun CoverImage(
    url: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    contentScale: ContentScale = ContentScale.Crop,
    emptyIcon: ImageVector = Icons.Rounded.MusicNote,
    emptyIconSize: Dp = 22.dp,
    failureIcon: ImageVector = Icons.Rounded.BrokenImage,
    failureIconSize: Dp = 22.dp,
) {
    val bgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    if (url.isBlank()) {
        // 明确无封面
        Box(
            modifier = modifier
                .clip(shape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = emptyIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(emptyIconSize)
            )
        }
        return
    }

    // 有 URL：加载中、成功、失败三态
    SubcomposeAsyncImage(
        model = normalizeCover(url),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
            .clip(shape)
            .background(bgColor),
        loading = {
            // 加载中：保持灰底
            Box(Modifier.fillMaxSize())
        },
        error = {
            // 加载失败：灰底 + 破图图标（比无封面稍深）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = failureIcon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(failureIconSize)
                )
            }
        }
    )
}