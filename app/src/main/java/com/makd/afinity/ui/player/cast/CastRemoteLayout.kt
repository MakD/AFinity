package com.makd.afinity.ui.player.cast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.ui.components.AsyncImage

private val RemoteBaseColor = Color(0xFF0B0E12)
private val TwoPaneMinWidth = 600.dp

private val LocalCastRemoteCompact = staticCompositionLocalOf { false }

@Composable
fun rememberCastRemoteBackground(accentColor: Color): Color =
    remember(accentColor) { lerp(RemoteBaseColor, accentColor, 0.08f) }

@Composable
fun CastRemoteLayout(
    artworkUrl: String?,
    accentColor: Color,
    deviceName: String,
    onMinimize: () -> Unit,
    onDeviceClick: () -> Unit,
    moreButton: @Composable () -> Unit,
    titleBlock: @Composable ColumnScope.() -> Unit,
    transport: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    artworkContentDescription: String? = null,
    artwork: (@Composable (Modifier) -> Unit)? = null,
    artworkOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val background = rememberCastRemoteBackground(accentColor)

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(background)) {
        val twoPane = maxWidth > maxHeight || maxWidth >= TwoPaneMinWidth
        if (twoPane) {
            CompositionLocalProvider(LocalCastRemoteCompact provides true) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(0.55f).fillMaxHeight()) {
                        if (artwork != null) {
                            artwork(Modifier.matchParentSize())
                        } else {
                            RemoteArtwork(
                                artworkUrl = artworkUrl,
                                contentDescription = artworkContentDescription,
                                background = background,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        Box(
                            modifier =
                                Modifier.align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.4f)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                background.copy(alpha = 0.75f),
                                                background,
                                            )
                                        )
                                    )
                        )
                        artworkOverlay()
                        CastRemoteIconButton(
                            painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                            contentDescription = stringResource(R.string.cd_music_minimize),
                            onClick = onMinimize,
                            modifier =
                                Modifier.align(Alignment.TopStart)
                                    .windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(
                                            WindowInsetsSides.Top + WindowInsetsSides.Start
                                        )
                                    )
                                    .padding(16.dp),
                        )
                    }
                    Box(
                        modifier =
                            Modifier.weight(0.45f)
                                .fillMaxHeight()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(
                                        WindowInsetsSides.Vertical + WindowInsetsSides.End
                                    )
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier =
                                Modifier.widthIn(max = 480.dp)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 8.dp, end = 28.dp, top = 12.dp, bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CastDeviceChip(
                                    deviceName = deviceName,
                                    accentColor = accentColor,
                                    onClick = onDeviceClick,
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                )
                                moreButton()
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            titleBlock()
                            Spacer(modifier = Modifier.height(16.dp))
                            transport()
                            CastRemoteActionRow(actions = actions)
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (artwork != null) {
                        artwork(Modifier.matchParentSize())
                    } else {
                        RemoteArtwork(
                            artworkUrl = artworkUrl,
                            contentDescription = artworkContentDescription,
                            background = background,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            background.copy(alpha = 0.78f),
                                            background,
                                        )
                                    )
                                )
                    )
                    artworkOverlay()
                    Row(
                        modifier =
                            Modifier.align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(
                                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CastRemoteIconButton(
                            painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                            contentDescription = stringResource(R.string.cd_music_minimize),
                            onClick = onMinimize,
                        )
                        CastDeviceChip(
                            deviceName = deviceName,
                            accentColor = accentColor,
                            onClick = onDeviceClick,
                            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                        )
                        moreButton()
                    }
                    Column(
                        modifier =
                            Modifier.align(Alignment.BottomCenter)
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                                )
                                .padding(horizontal = 28.dp)
                                .padding(bottom = 8.dp),
                        content = titleBlock,
                    )
                }
                Column(
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                                )
                            )
                            .padding(horizontal = 28.dp)
                            .padding(top = 18.dp, bottom = 12.dp)
                ) {
                    transport()
                    CastRemoteActionRow(actions = actions)
                }
            }
        }
    }
}

@Composable
fun CastRemoteArtwork(
    artworkUrl: String?,
    contentDescription: String?,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    RemoteArtwork(
        artworkUrl = artworkUrl,
        contentDescription = contentDescription,
        background = rememberCastRemoteBackground(accentColor),
        modifier = modifier,
    )
}

@Composable
private fun RemoteArtwork(
    artworkUrl: String?,
    contentDescription: String?,
    background: Color,
    modifier: Modifier = Modifier,
) {
    if (artworkUrl != null) {
        AsyncImage(
            imageUrl = artworkUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = modifier.background(lerp(background, Color.White, 0.06f)))
    }
}

@Composable
private fun CastRemoteActionRow(actions: @Composable RowScope.() -> Unit) {
    val compact = LocalCastRemoteCompact.current
    HorizontalDivider(
        modifier = Modifier.padding(top = if (compact) 10.dp else 20.dp),
        color = Color.White.copy(alpha = 0.08f),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = actions,
    )
}

@Composable
fun CastRemoteIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.32f),
        contentColor = Color.White,
        modifier = modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun CastDeviceChip(
    deviceName: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Black.copy(alpha = 0.36f),
) {
    val label = stringResource(R.string.cd_cast_device_fmt, deviceName)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = Color.White,
        modifier = modifier.heightIn(min = 36.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = 13.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cast_connected),
                contentDescription = label,
                tint = lerp(accentColor, Color.White, 0.45f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = deviceName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CastRemoteTitle(
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    overline: String? = null,
    meta: String? = null,
    onOverlineClick: (() -> Unit)? = null,
    onMetaClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val compact = LocalCastRemoteCompact.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    text = overline.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = lerp(accentColor, Color.White, 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        if (onOverlineClick != null) Modifier.clickable(onClick = onOverlineClick)
                        else Modifier,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = title,
                style =
                    if (compact) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.64f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        if (onMetaClick != null) Modifier.clickable(onClick = onMetaClick)
                        else Modifier,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastRemoteTransport(
    position: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    formatTime: (Float) -> String,
    accentColor: Color,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onSeek: (Float) -> Unit,
    onPlayPause: () -> Unit,
    rewindPainter: Painter,
    rewindContentDescription: String,
    onRewind: () -> Unit,
    forwardPainter: Painter,
    forwardContentDescription: String,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: (() -> Unit)? = null,
    previousContentDescription: String = "",
    onNext: (() -> Unit)? = null,
    nextContentDescription: String = "",
    markFractions: List<Float> = emptyList(),
    centerLabel: String? = null,
) {
    val compact = LocalCastRemoteCompact.current
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val displayValue = (dragValue ?: position).coerceIn(valueRange.start, valueRange.endInclusive)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val inactiveColor = Color.White.copy(alpha = 0.16f)
    val markColor = Color.White.copy(alpha = 0.55f)

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = displayValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let(onSeek)
                dragValue = null
            },
            valueRange = valueRange,
            thumb = { Box(modifier = Modifier.size(14.dp).background(Color.White, CircleShape)) },
            track = { sliderState ->
                val fraction = ((sliderState.value - valueRange.start) / span).coerceIn(0f, 1f)
                Canvas(modifier = Modifier.fillMaxWidth().height(18.dp)) {
                    val y = size.height / 2f
                    val stroke = 3.dp.toPx()
                    drawLine(
                        color = inactiveColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, y),
                        end = Offset(size.width * fraction, y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    val half = 4.dp.toPx()
                    markFractions.forEach { mark ->
                        val x = size.width * mark.coerceIn(0f, 1f)
                        drawLine(
                            color = markColor,
                            start = Offset(x, y - half),
                            end = Offset(x, y + half),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTime(displayValue - valueRange.start),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
            if (centerLabel != null) {
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.6.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                )
            }
            Text(
                text = "-" + formatTime(valueRange.endInclusive - displayValue),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onPrevious != null) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_player_skip_back),
                        contentDescription = previousContentDescription,
                        tint = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(onClick = onRewind, modifier = Modifier.size(52.dp)) {
                Icon(
                    painter = rewindPainter,
                    contentDescription = rewindContentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            val playSize = if (compact) 58.dp else 68.dp
            Surface(
                onClick = onPlayPause,
                shape = CircleShape,
                color = Color.White,
                contentColor = RemoteBaseColor,
                modifier = Modifier.size(playSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(playSize * 0.45f),
                            color = RemoteBaseColor,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            painter =
                                painterResource(
                                    if (isPlaying) R.drawable.ic_player_pause_filled
                                    else R.drawable.ic_player_play_filled
                                ),
                            contentDescription =
                                stringResource(
                                    if (isPlaying) R.string.cd_pause else R.string.cd_play
                                ),
                            modifier = Modifier.size(playSize * 0.45f),
                        )
                    }
                }
            }
            IconButton(onClick = onForward, modifier = Modifier.size(52.dp)) {
                Icon(
                    painter = forwardPainter,
                    contentDescription = forwardContentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            if (onNext != null) {
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_player_skip_forward),
                        contentDescription = nextContentDescription,
                        tint = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.CastRemoteAction(
    painter: Painter,
    label: String,
    onClick: () -> Unit,
    accentColor: Color,
    active: Boolean = false,
) {
    val color =
        if (active) lerp(accentColor, Color.White, 0.35f) else Color.White.copy(alpha = 0.74f)
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun RowScope.CastRemoteValueAction(
    value: String,
    label: String,
    onClick: () -> Unit,
    accentColor: Color,
    active: Boolean = false,
) {
    val color =
        if (active) lerp(accentColor, Color.White, 0.35f) else Color.White.copy(alpha = 0.74f)
    TextButton(
        onClick = onClick,
        modifier =
            Modifier.weight(0.7f).heightIn(min = 44.dp).semantics { contentDescription = label },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
