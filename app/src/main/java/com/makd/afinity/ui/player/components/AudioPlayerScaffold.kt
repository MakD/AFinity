package com.makd.afinity.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AsyncImage

@Immutable
data class AudioCoverStyle(
    val cornerRadius: Dp,
    val elevation: Dp,
    val placeholderIconSize: Dp,
    val horizontalOverflow: Dp,
)

private val PortraitCoverStyle =
    AudioCoverStyle(
        cornerRadius = 32.dp,
        elevation = 24.dp,
        placeholderIconSize = 64.dp,
        horizontalOverflow = 24.dp,
    )

private val LandscapeCoverStyle =
    AudioCoverStyle(
        cornerRadius = 24.dp,
        elevation = 16.dp,
        placeholderIconSize = 48.dp,
        horizontalOverflow = 32.dp,
    )

@Composable
fun AudioPlayerScaffold(
    isLandscape: Boolean,
    isCasting: Boolean,
    accentColor: Color,
    snackbarHostState: SnackbarHostState,
    onMinimize: () -> Unit,
    onCastClick: () -> Unit,
    onMoreClick: () -> Unit,
    castRemote: @Composable () -> Unit,
    cover: @Composable (AudioCoverStyle) -> Unit,
    info: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    overlays: @Composable () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val gradientColors =
        if (isDark) {
            listOf(
                accentColor.copy(alpha = 0.8f),
                Color.Black.copy(alpha = 0.5f),
                Color.Black.copy(alpha = 0.95f),
            )
        } else {
            listOf(
                accentColor.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                MaterialTheme.colorScheme.surface,
            )
        }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState, snackbar = { AFinitySnackbar(it) })
        },
        containerColor = if (isDark) Color.Black else MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize().background(Brush.verticalGradient(colors = gradientColors))
        ) {
            if (isCasting) {
                castRemote()
            } else if (isLandscape) {
                Row(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(0.45f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        cover(LandscapeCoverStyle)
                    }

                    Column(
                        modifier =
                            Modifier.weight(0.55f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AudioPlayerTopRow(
                            isLandscape = true,
                            isCasting = isCasting,
                            accentColor = accentColor,
                            onMinimize = onMinimize,
                            onCastClick = onCastClick,
                            onMoreClick = onMoreClick,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        info()
                        Spacer(modifier = Modifier.height(24.dp))
                        controls()
                        Spacer(modifier = Modifier.height(16.dp))
                        AudioPlayerControlRow(content = actions)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                Column(
                    modifier =
                        Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AudioPlayerTopRow(
                        isLandscape = false,
                        isCasting = isCasting,
                        accentColor = accentColor,
                        onMinimize = onMinimize,
                        onCastClick = onCastClick,
                        onMoreClick = onMoreClick,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        cover(PortraitCoverStyle)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    info()
                    Spacer(modifier = Modifier.height(12.dp))
                    controls()
                    Spacer(modifier = Modifier.height(28.dp))
                    AudioPlayerControlRow(
                        modifier = Modifier.padding(bottom = 16.dp),
                        content = actions,
                    )
                }
            }
        }

        overlays()
    }
}

@Composable
private fun AudioPlayerTopRow(
    isLandscape: Boolean,
    isCasting: Boolean,
    accentColor: Color,
    onMinimize: () -> Unit,
    onCastClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        IconButton(onClick = onMinimize, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                contentDescription = stringResource(R.string.cd_abs_minimize),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = if (isLandscape) Modifier else Modifier.size(32.dp),
            )
        }

        Text(
            stringResource(R.string.abs_now_playing),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.Center),
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCastClick) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cast_devices),
                        contentDescription = stringResource(R.string.cd_cast),
                        tint = if (isCasting) accentColor else MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier =
                            Modifier.size(4.dp)
                                .align(Alignment.BottomCenter)
                                .alpha(if (isCasting) 1f else 0f)
                                .background(accentColor, CircleShape)
                    )
                }
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_dots_vertical),
                    contentDescription = stringResource(R.string.cd_music_more_options),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun AudioPlayerTitleBlock(
    title: String,
    subtitle: String,
    accentLine: String?,
    accentColor: Color,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    onSubtitleClick: (() -> Unit)? = null,
    onAccentLineClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val displayAccentColor =
        if (!isDark && accentColor.luminance() > 0.5f) {
            MaterialTheme.colorScheme.primary
        } else {
            accentColor
        }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style =
                    if (isLandscape) {
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isLandscape) {
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = subtitle,
                style =
                    if (isLandscape) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.plainClickable(onSubtitleClick),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = accentLine ?: " ",
                style = MaterialTheme.typography.labelLarge,
                color = displayAccentColor,
                maxLines = 1,
                modifier =
                    Modifier.plainClickable(onAccentLineClick)
                        .alpha(if (accentLine != null) 1f else 0f)
                        .basicMarquee(iterations = Int.MAX_VALUE, velocity = 30.dp),
            )
        }
        trailing()
    }
}

@Composable
fun AudioCoverArt(
    imageUrl: String?,
    blurHash: String?,
    placeholderIcon: Painter,
    style: AudioCoverStyle,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(style.cornerRadius)
    Surface(
        modifier =
            modifier.shadow(elevation = style.elevation, shape = shape, spotColor = Color.Black),
        shape = shape,
        color = Color.Transparent,
    ) {
        if (imageUrl != null || blurHash != null) {
            AsyncImage(
                imageUrl = imageUrl,
                contentDescription = null,
                blurHash = blurHash,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                targetWidth = AudioPlayerLayout.CoverMaxSize,
                targetHeight = AudioPlayerLayout.CoverMaxSize,
            )
        } else {
            Box(
                modifier =
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = placeholderIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(style.placeholderIconSize),
                )
            }
        }
    }
}

private fun Modifier.plainClickable(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this
    else clickable(interactionSource = null, indication = null, onClick = onClick)
