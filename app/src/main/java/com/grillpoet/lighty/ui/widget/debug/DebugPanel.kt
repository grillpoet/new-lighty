package com.grillpoet.lighty.ui.widget.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grillpoet.lighty.core.DebugLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugPanel(modifier: Modifier = Modifier) {
    val visible by DebugLog.visible.collectAsStateWithLifecycle()
    if (!visible) return

    val all by DebugLog.entries.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<DebugLog.Kind?>(null) }
    var expandedId by remember { mutableLongStateOf(-1L) }

    val list = remember(all, filter) {
        if (filter == null) all else all.filter { it.kind == filter }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(list.size) {
        if (list.isNotEmpty()) listState.animateScrollToItem(list.size - 1)
    }

    val clipboard = LocalClipboardManager.current
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 半透明遮罩，点击关闭
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { DebugLog.hide() }
        )

        // 底部面板
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 560.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = scheme.surfaceContainerLow,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                // 顶部把手 + 标题 + 操作
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "调试日志",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${list.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    PanelIconButton(Icons.Rounded.ContentCopy, "复制") {
                        clipboard.setText(AnnotatedString(formatAll(list, timeFmt)))
                    }
                    PanelIconButton(Icons.Rounded.Delete, "清空") {
                        DebugLog.clear()
                    }
                    PanelIconButton(Icons.Rounded.Close, "关闭") {
                        DebugLog.hide()
                    }
                }

                // 过滤 chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip("全部", filter == null) { filter = null }
                    FilterChip("报错", filter == DebugLog.Kind.ERROR) {
                        filter = DebugLog.Kind.ERROR
                    }
                    FilterChip("日志", filter == DebugLog.Kind.PRINT) {
                        filter = DebugLog.Kind.PRINT
                    }
                }

                Spacer(Modifier.height(6.dp))

                if (list.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(list, key = { it.id }) { e ->
                            DebugItem(
                                entry = e,
                                expanded = expandedId == e.id,
                                timeFmt = timeFmt,
                                onToggle = {
                                    expandedId = if (expandedId == e.id) -1L else e.id
                                },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(formatOne(e, timeFmt)))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Icon(
        icon, label,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
    )
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh
    val fg = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}

@Composable
private fun DebugItem(
    entry: DebugLog.Entry,
    expanded: Boolean,
    timeFmt: SimpleDateFormat,
    onToggle: () -> Unit,
    onCopy: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = when (entry.kind) {
        DebugLog.Kind.ERROR -> scheme.error
        DebugLog.Kind.PRINT -> scheme.primary
    }
    val bg = when (entry.kind) {
        DebugLog.Kind.ERROR -> scheme.errorContainer.copy(alpha = 0.4f)
        DebugLog.Kind.PRINT -> scheme.surfaceContainerHigh
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                timeFmt.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (entry.kind == DebugLog.Kind.ERROR) "报错" else "日志",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            entry.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = scheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ContentCopy, "复制",
                    tint = accent,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCopy
                        )
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "复制此条",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCopy
                    )
                )
            }
        }
    }
}

private fun formatOne(e: DebugLog.Entry, fmt: SimpleDateFormat): String {
    val kind = if (e.kind == DebugLog.Kind.ERROR) "报错" else "日志"
    return "[${fmt.format(Date(e.timestamp))}][$kind]\n${e.text}"
}

private fun formatAll(list: List<DebugLog.Entry>, fmt: SimpleDateFormat): String {
    val sb = StringBuilder()
    sb.append("=== Lighty Debug Log ===\n")
    sb.append("条目: ${list.size}\n\n")
    list.forEach { e ->
        sb.append(formatOne(e, fmt)).append("\n\n")
    }
    return sb.toString()
}