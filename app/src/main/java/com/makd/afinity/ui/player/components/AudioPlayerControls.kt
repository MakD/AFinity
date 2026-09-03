package com.makd.afinity.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Immutable
object AudioPlayerLayout {
    val CoverMaxSize = 320.dp
    val IconSize = 22.dp
    val ActiveDotSize = 4.dp

    val InactiveTint = Color.White.copy(alpha = 0.8f)

    val CoverSizeCap: Modifier = Modifier.sizeIn(maxWidth = CoverMaxSize, maxHeight = CoverMaxSize)
}

@Composable
fun AudioPlayerControlRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun AudioPlayerControlSlot(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = if (active) activeColor else AudioPlayerLayout.InactiveTint,
                modifier = Modifier.size(AudioPlayerLayout.IconSize),
            )
        }
        ActiveDot(active = active, color = activeColor)
    }
}

@Composable
fun AudioPlayerValueSlot(
    value: String,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier.minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClickLabel = contentDescription, onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (active) activeColor else Color.White,
                maxLines = 1,
            )
        }
        ActiveDot(active = active, color = activeColor)
    }
}

@Composable
private fun ActiveDot(active: Boolean, color: Color) {
    Box(
        modifier =
            Modifier.size(AudioPlayerLayout.ActiveDotSize)
                .alpha(if (active) 1f else 0f)
                .background(color, CircleShape)
    )
}
