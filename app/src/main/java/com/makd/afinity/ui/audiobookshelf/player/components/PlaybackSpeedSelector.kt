package com.makd.afinity.ui.audiobookshelf.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedSelector(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()

    val presets = listOf(0.5f, 0.75f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val index = presets.indexOfFirst { it == currentSpeed }
        if (index != -1) {
            listState.scrollToItem((index - 1).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "PLAYBACK SPEED",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "${formatSpeed(currentSpeed)}x",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FilledIconButton(
                    onClick = {
                        val prev =
                            presets.lastOrNull { it < currentSpeed - 0.01f } ?: presets.first()
                        onSpeedSelected(prev)
                    },
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_minus),
                        contentDescription = "Previous preset",
                    )
                }

                Slider(
                    value = currentSpeed,
                    onValueChange = { value ->
                        val snapped = (value * 20).roundToInt() / 20f
                        onSpeedSelected(snapped)
                    },
                    valueRange = presets.first()..presets.last(),
                    modifier = Modifier.weight(1f),
                    colors =
                        SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.onSurface,
                            activeTrackColor = MaterialTheme.colorScheme.onSurface,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
                FilledIconButton(
                    onClick = {
                        val next =
                            presets.firstOrNull { it > currentSpeed + 0.01f } ?: presets.last()
                        onSpeedSelected(next)
                    },
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_plus),
                        contentDescription = "Next preset",
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(presets) { speed ->
                    PresetSpeedItem(
                        speed = speed,
                        isSelected = speed == currentSpeed,
                        onClick = { onSpeedSelected(speed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetSpeedItem(speed: Float, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier.width(64.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isSelected) Color.Transparent
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        RoundedCornerShape(8.dp),
                    )
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatSpeed(speed),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color =
                    if (isSelected) MaterialTheme.colorScheme.inverseOnSurface
                    else MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (speed == 1.0f) {
            Text(
                text = "Normal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(text = " ", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toLong().toFloat()) "${speed.toLong()}" else "$speed"
}
