package com.grillpoet.lighty.ui.widget.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grillpoet.lighty.ui.widget.LightyIconButton

/**
 * 多选模式的操作栏。
 *
 * 布局：[退出] [已选 N / M] [全选] [下载]
 *  - 退出：关闭多选并清空选择
 *  - 全选 / 取消全选：由 [allSelected] 决定文案
 *  - 下载：在未选任何项时禁用
 *
 * 使用场景：搜索结果、配置源列表、漫画章节等需要批量操作的页面。
 */
@Composable
fun MultiSelectBar(
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    onExit: () -> Unit,
    onToggleAll: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 退出多选
        LightyIconButton(
            onExit, Icons.Rounded.Close, "退出多选",
            size = 36.dp, iconSize = 20.dp
        )
        Spacer(Modifier.width(8.dp))

        // 选中计数（占满中间，把操作按钮顶到右侧）
        Text(
            "已选 $selectedCount / $totalCount",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        // 全选 / 取消全选
        TextButton(onClick = onToggleAll) {
            Text(if (allSelected) "取消全选" else "全选")
        }

        // 下载（未选时禁用）
        TextButton(onClick = onDownload, enabled = selectedCount > 0) {
            Text(
                "下载",
                color = if (selectedCount > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )
        }
    }
}