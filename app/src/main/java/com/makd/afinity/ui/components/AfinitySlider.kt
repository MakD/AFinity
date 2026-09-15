package com.makd.afinity.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

object AfinitySliderDefaults {

    @Composable
    fun colors(
        thumb: Color = MaterialTheme.colorScheme.primary,
        activeTrack: Color = MaterialTheme.colorScheme.primary,
        inactiveTrack: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
        activeTick: Color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
        inactiveTick: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
    ): SliderColors =
        SliderDefaults.colors(
            thumbColor = thumb,
            activeTrackColor = activeTrack,
            inactiveTrackColor = inactiveTrack,
            activeTickColor = activeTick,
            inactiveTickColor = inactiveTick,
        )
}

@Composable
fun AfinitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    colors: SliderColors = AfinitySliderDefaults.colors(),
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        colors = colors,
    )
}

@Composable
fun AfinityRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    colors: SliderColors = AfinitySliderDefaults.colors(),
) {
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        steps = steps,
        colors = colors,
    )
}
