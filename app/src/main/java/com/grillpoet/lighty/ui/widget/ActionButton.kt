package com.grillpoet.lighty.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grillpoet.lighty.data.Source

@Composable
fun ActionChipBar(
    source: Source?,
    scope: String,
    onAction: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val buttons = source?.buttonsFor(scope).orEmpty()
    if (buttons.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(buttons, key = { it["id"].orEmpty() }) { btn ->
            ActionChip(
                icon = iconOf(btn["icon"].orEmpty()),
                label = btn["label"].orEmpty().ifBlank { btn["id"].orEmpty() },
                onClick = { onAction(btn) }
            )
        }
    }
}

@Composable
fun ActionIconBar(
    source: Source?,
    scope: String,
    onAction: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    maxCount: Int = 3
) {
    val buttons = source?.buttonsFor(scope).orEmpty().take(maxCount)
    if (buttons.isEmpty()) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEach { btn ->
            LightyIconButton(
                onClick = { onAction(btn) },
                icon = iconOf(btn["icon"].orEmpty()),
                contentDescription = btn["label"].orEmpty(),
                size = 40.dp,
                iconSize = 22.dp
            )
        }
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun iconOf(name: String): ImageVector = when (name) {
    "person" -> Icons.Rounded.Person
    "new_releases" -> Icons.Rounded.NewReleases
    "whatshot" -> Icons.Rounded.Whatshot
    "shuffle" -> Icons.Rounded.Shuffle
    "auto_awesome" -> Icons.Rounded.AutoAwesome
    "category" -> Icons.Rounded.Category
    "bookmarks" -> Icons.Rounded.Bookmarks
    "search" -> Icons.Rounded.Search
    else -> Icons.Rounded.PlayArrow
}