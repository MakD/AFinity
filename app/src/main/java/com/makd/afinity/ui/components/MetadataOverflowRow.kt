package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val SEPARATOR = "•"
private const val ELLIPSIS = "…"

@Composable
fun MetadataOverflowRow(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
    showSeparators: Boolean = true,
) {
    if (items.isEmpty()) return

    val itemCount = items.size
    val step = if (showSeparators) 2 else 1

    Layout(
        modifier = modifier,
        content = {
            items.forEachIndexed { index, item ->
                if (showSeparators && index > 0) MetadataRowSymbol(SEPARATOR)
                Box { item() }
            }
            MetadataRowSymbol(ELLIPSIS)
        },
    ) { measurables, constraints ->
        val placeables = measurables.map {
            it.measure(Constraints(maxHeight = constraints.maxHeight))
        }
        val ellipsis = placeables.last()
        val spacingPx = spacing.roundToPx()

        val itemEnds = IntArray(itemCount)
        var runningWidth = 0
        for (index in 0 until itemCount) {
            if (index > 0) {
                runningWidth += spacingPx
                if (showSeparators) {
                    runningWidth += placeables[index * step - 1].width + spacingPx
                }
            }
            runningWidth += placeables[index * step].width
            itemEnds[index] = runningWidth
        }

        val maxWidth = constraints.maxWidth
        val ellipsisWidth = spacingPx + ellipsis.width
        val fitsAll = itemEnds[itemCount - 1] <= maxWidth
        val visibleCount =
            if (fitsAll) {
                itemCount
            } else {
                (itemEnds.indexOfLast { it + ellipsisWidth <= maxWidth } + 1)
                    .coerceAtMost(itemCount - 1)
                    .coerceAtLeast(1)
            }
        val showEllipsis = !fitsAll && itemEnds[visibleCount - 1] + ellipsisWidth <= maxWidth

        val contentWidth = itemEnds[visibleCount - 1] + if (showEllipsis) ellipsisWidth else 0
        var contentHeight = if (showEllipsis) ellipsis.height else 0
        for (index in 0 until visibleCount) {
            contentHeight = maxOf(contentHeight, placeables[index * step].height)
        }
        val width = contentWidth.coerceIn(constraints.minWidth, maxWidth)
        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            var x = 0
            for (index in 0 until visibleCount) {
                if (index > 0) {
                    x += spacingPx
                    if (showSeparators) {
                        val separator = placeables[index * step - 1]
                        separator.placeRelative(x, (height - separator.height) / 2)
                        x += separator.width + spacingPx
                    }
                }
                val item = placeables[index * step]
                item.placeRelative(x, (height - item.height) / 2)
                x += item.width
            }
            if (showEllipsis) {
                ellipsis.placeRelative(x + spacingPx, (height - ellipsis.height) / 2)
            }
        }
    }
}

@Composable
private fun MetadataRowSymbol(symbol: String) {
    Text(
        text = symbol,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
