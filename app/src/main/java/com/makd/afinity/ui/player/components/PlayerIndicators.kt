package com.makd.afinity.ui.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.ui.player.PlayerViewModel
import kotlin.math.roundToInt

@androidx.media3.common.util.UnstableApi
@Composable
fun PlayerIndicators(
    uiState: PlayerViewModel.PlayerUiState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        SeekIndicator(
            show = uiState.showSeekIndicator,
            direction = uiState.seekDirection,
            modifier = Modifier.align(Alignment.Center)
        )

        BrightnessIndicator(
            show = uiState.showBrightnessIndicator,
            level = uiState.brightnessLevel,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        VolumeIndicator(
            show = uiState.showVolumeIndicator,
            level = uiState.volumeLevel,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun SeekIndicator(
    show: Boolean,
    direction: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = show,
        enter = scaleIn(animationSpec = tween(200)) + fadeIn(),
        exit = scaleOut(animationSpec = tween(200)) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (direction > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = if (direction > 0) "Fast Forward" else "Rewind",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (direction > 0) "+10s" else "-10s",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BrightnessIndicator(
    show: Boolean,
    level: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = show,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(200)
        ) + fadeIn(),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(200)
        ) + fadeOut(),
        modifier = modifier.padding(start = 32.dp)
    ) {
        Card(
            modifier = Modifier
                .width(80.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = getBrightnessIcon(level),
                    contentDescription = "Brightness",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )

                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .weight(1f)
                        .background(
                            Color.White.copy(alpha = 0.3f),
                            RoundedCornerShape(3.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(level)
                            .background(
                                Color.White,
                                RoundedCornerShape(3.dp)
                            )
                            .align(Alignment.BottomCenter)
                    )
                }

                Text(
                    text = "${(level * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun VolumeIndicator(
    show: Boolean,
    level: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = show,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(200)
        ) + fadeIn(),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(200)
        ) + fadeOut(),
        modifier = modifier.padding(end = 32.dp)
    ) {
        Card(
            modifier = Modifier
                .width(80.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = getVolumeIcon(level),
                    contentDescription = "Volume",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )

                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .weight(1f)
                        .background(
                            Color.White.copy(alpha = 0.3f),
                            RoundedCornerShape(3.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(level / 100f)
                            .background(
                                Color.White,
                                RoundedCornerShape(3.dp)
                            )
                            .align(Alignment.BottomCenter)
                    )
                }

                Text(
                    text = "$level%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun getBrightnessIcon(level: Float): ImageVector {
    return when {
        level < 0.3f -> Icons.Default.BrightnessLow
        level < 0.7f -> Icons.Default.BrightnessMedium
        else -> Icons.Default.BrightnessHigh
    }
}

private fun getVolumeIcon(level: Int): ImageVector {
    return when {
        level == 0 -> Icons.Default.VolumeOff
        level < 30 -> Icons.Default.VolumeDown
        else -> Icons.Default.VolumeUp
    }
}