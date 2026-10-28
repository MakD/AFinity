package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val GroupHeight = 56.dp
private val OuterCorner = 28.dp
private val InnerCorner = 8.dp
private val GroupGap = 2.dp
private val IconButtonWidth = 64.dp

sealed interface GroupButton {
    val icon: Painter
    val onClick: (() -> Unit)?
    val iconTint: Color?

    class Wide(
        val text: String,
        override val icon: Painter,
        override val onClick: (() -> Unit)?,
        val filled: Boolean = false,
        override val iconTint: Color? = null,
    ) : GroupButton

    class IconOnly(
        override val icon: Painter,
        val contentDescription: String,
        override val onClick: (() -> Unit)?,
        override val iconTint: Color? = null,
    ) : GroupButton
}

@Composable
fun ConnectedButtonGroup(buttons: List<GroupButton>, modifier: Modifier = Modifier) {
    if (buttons.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = GroupHeight),
        horizontalArrangement = Arrangement.spacedBy(GroupGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEachIndexed { index, button ->
            val shape =
                RoundedCornerShape(
                    topStart = if (index == 0) OuterCorner else InnerCorner,
                    bottomStart = if (index == 0) OuterCorner else InnerCorner,
                    topEnd = if (index == buttons.lastIndex) OuterCorner else InnerCorner,
                    bottomEnd = if (index == buttons.lastIndex) OuterCorner else InnerCorner,
                )
            val filled = button is GroupButton.Wide && button.filled
            val colors =
                if (filled) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                }
            val sizeModifier =
                when (button) {
                    is GroupButton.Wide -> Modifier.weight(1f)
                    is GroupButton.IconOnly -> Modifier.width(IconButtonWidth)
                }

            Button(
                onClick = { button.onClick?.invoke() },
                enabled = button.onClick != null,
                shape = shape,
                colors = colors,
                contentPadding =
                    if (button is GroupButton.Wide) PaddingValues(horizontal = 12.dp)
                    else PaddingValues(0.dp),
                modifier = sizeModifier.fillMaxHeight().heightIn(min = GroupHeight),
            ) {
                when (button) {
                    is GroupButton.Wide -> {
                        Icon(
                            painter = button.icon,
                            contentDescription = null,
                            tint = button.iconTint ?: LocalContentColor.current,
                        )
                        Text(
                            text = button.text,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(start = 6.dp),
                        )
                    }
                    is GroupButton.IconOnly ->
                        Icon(
                            painter = button.icon,
                            contentDescription = button.contentDescription,
                            tint = button.iconTint ?: LocalContentColor.current,
                            modifier = Modifier.size(24.dp),
                        )
                }
            }
        }
    }
}
