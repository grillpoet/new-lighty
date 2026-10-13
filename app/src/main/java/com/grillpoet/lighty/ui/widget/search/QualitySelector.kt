package com.grillpoet.lighty.ui.widget.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.grillpoet.lighty.data.Record

/**
 * 音质选择器。
 *
 * 形态：一个 chip 按钮（primaryContainer 背景），点击弹 DropdownMenu。
 *  - [choices] 是源提供的音质列表（Record 形式，字段为 value / label）
 *  - 当前 label 找得到就显示，找不到退化为第一项，兜底显示「音质」
 *
 * 与 SearchInputBar 并排放在同一行，只在源声明了多种音质时显示。
 */
@Composable
fun QualitySelector(
    choices: List<Record>,
    current: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // 当前音质的显示文案（带兜底）
    val currentLabel = choices.firstOrNull { it["value"] == current }?.get("label")
        ?: choices.firstOrNull()?.get("label") ?: "音质"

    Box {
        // 触发按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(
                    // 去掉水波纹，保持紧凑 chip 的干净感
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = true }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                currentLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
            Icon(
                Icons.Rounded.ArrowDropDown, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }

        // 下拉菜单
        DropdownMenu(expanded, { expanded = false }) {
            choices.forEach { q ->
                DropdownMenuItem(
                    text = { Text(q["label"].orEmpty()) },
                    onClick = { onSelected(q["value"].orEmpty()); expanded = false }
                )
            }
        }
    }
}