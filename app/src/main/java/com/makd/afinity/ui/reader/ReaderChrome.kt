package com.makd.afinity.ui.reader

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.theme.LocalDarkColorScheme
import kotlin.math.roundToInt

private val PanelMaxWidth = 640.dp
private val ThumbIdle = 16.dp
private val ThumbDragging = 22.dp
private val TrackHeight = 4.dp
private val SegmentGap = 3.dp
private const val MAX_SEGMENTS = 60

@Composable
internal fun ReaderChromeTheme(content: @Composable () -> Unit) {
    val dark = LocalDarkColorScheme.current
    MaterialTheme(colorScheme = dark, typography = MaterialTheme.typography) {
        CompositionLocalProvider(LocalContentColor provides dark.onSurface, content = content)
    }
}

@Composable
internal fun BoxScope.ReaderTopBar(
    visible: Boolean,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 3 },
        exit = fadeOut() + slideOutVertically { -it / 3 },
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)
                        )
                    )
                    .pointerInput(Unit) { detectTapGestures {} }
                    .windowInsetsPadding(ReaderTopInsets)
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
    }
}

@Composable
internal fun ReaderBookmarkButton(bookmarked: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter =
                painterResource(
                    if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
                ),
            contentDescription =
                stringResource(
                    if (bookmarked) R.string.cd_reader_bookmark_remove
                    else R.string.cd_reader_bookmark_add
                ),
            tint =
                if (bookmarked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun BoxScope.ReaderBottomPanel(
    visible: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 4 },
        exit = fadeOut() + slideOutVertically { it / 4 },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to Color.Black.copy(alpha = 0.8f),
                            1f to Color.Black.copy(alpha = 0.95f),
                        )
                    )
                    .pointerInput(Unit) { detectTapGestures {} }
                    .windowInsetsPadding(ReaderBottomInsets)
                    .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = PanelMaxWidth).fillMaxWidth(),
                content = content,
            )
        }
    }
}

@Composable
internal fun ReaderToolPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun RowScope.ReaderToolButton(
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tint =
        when {
            !enabled -> colors.onSurface.copy(alpha = 0.38f)
            selected -> colors.onSecondaryContainer
            else -> colors.onSurface
        }
    Box(
        modifier =
            Modifier.weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (selected) colors.secondaryContainer else Color.Transparent)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics {
                    this.contentDescription = contentDescription
                    this.selected = selected
                },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint, content = content)
    }
}

@Composable
internal fun ReaderToolIcon(@DrawableRes icon: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
internal fun ReaderToolText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = LocalContentColor.current,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScrubber(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    boundaries: List<Float> = emptyList(),
    bubble: @Composable (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    val shown = if (dragging) dragValue else fraction.coerceIn(0f, 1f)
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.secondaryContainer
    val gaps = boundaries.takeIf { it.size in 1..MAX_SEGMENTS }.orEmpty()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val thumbRadiusPx = with(density) { (ThumbDragging / 2).toPx() }

    Box(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = {
                dragValue = it
                dragging = true
            },
            onValueChangeFinished = {
                if (dragging) onSeek(dragValue)
                dragging = false
            },
            modifier = Modifier.fillMaxWidth().onSizeChanged { widthPx = it.width },
            thumb = {
                val size = if (dragging) ThumbDragging else ThumbIdle
                Box(modifier = Modifier.size(ThumbDragging), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(size).background(primary, CircleShape))
                }
            },
            track = { state ->
                Canvas(
                    modifier =
                        Modifier.fillMaxWidth().height(TrackHeight).graphicsLayer {
                            if (rtl) scaleX = -1f
                        }
                ) {
                    val start = 0f
                    val range = size.width.coerceAtLeast(1f)
                    val h = size.height
                    val corner = CornerRadius(h / 2f, h / 2f)
                    val thumbX = start + state.value.coerceIn(0f, 1f) * range
                    val gap = SegmentGap.toPx()
                    val edges = listOf(0f) + gaps + listOf(1f)
                    for (i in 0 until edges.size - 1) {
                        val x0 = start + edges[i] * range + if (i > 0) gap / 2f else 0f
                        val x1 =
                            start + edges[i + 1] * range - if (i < edges.size - 2) gap / 2f else 0f
                        if (x1 <= x0) continue
                        drawRoundRect(
                            color = inactive,
                            topLeft = Offset(x0, 0f),
                            size = Size(x1 - x0, h),
                            cornerRadius = corner,
                        )
                        val filledEnd = minOf(x1, thumbX)
                        if (filledEnd > x0) {
                            drawRoundRect(
                                color = primary,
                                topLeft = Offset(x0, 0f),
                                size = Size(filledEnd - x0, h),
                                cornerRadius = corner,
                            )
                        }
                    }
                }
            },
        )
        if (dragging && widthPx > 0) {
            val ltrX = thumbRadiusPx + shown * (widthPx - 2 * thumbRadiusPx)
            val centerX = if (rtl) widthPx - ltrX else ltrX
            val gapPx = with(density) { 6.dp.roundToPx() }
            Box(
                modifier =
                    Modifier.align(AbsoluteAlignment.TopLeft).layout { measurable, constraints ->
                        val placeable =
                            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(0, 0) {
                            val x =
                                (centerX - placeable.width / 2f)
                                    .roundToInt()
                                    .coerceIn(0, (widthPx - placeable.width).coerceAtLeast(0))
                            placeable.place(x, -placeable.height - gapPx)
                        }
                    }
            ) {
                bubble(shown)
            }
        }
    }
}

@Composable
internal fun ReaderBubbleLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier.widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
internal fun ReaderProgressLabels(start: @Composable () -> Unit, end: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        start()
        if (end != null) {
            Text(
                text = end,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
