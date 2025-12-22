package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.ui.player.PlayerViewModel
import kotlin.math.roundToInt

@UnstableApi
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
                    painter = if (direction > 0) painterResource(id = R.drawable.fast_forward) else painterResource(id = R.drawable.fast_rewind),
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
                    painter = getBrightnessIcon(level),
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
                    painter = getVolumeIcon(level),
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

@Composable
private fun getBrightnessIcon(level: Float): Painter {
    return when {
        level < 0.3f -> painterResource(id = R.drawable.brightness_down)
        level < 0.7f -> painterResource(id = R.drawable.brightness_medium)
        else -> painterResource(id = R.drawable.brightness_up)
    }
}

@Composable
private fun getVolumeIcon(level: Int): Painter {
    return when {
        level == 0 -> painterResource(id = R.drawable.volume_off)
        level < 30 -> painterResource(id = R.drawable.volume_down)
        else -> painterResource(id = R.drawable.volume_up)
    }
}