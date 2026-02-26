package com.makd.afinity.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

@Composable
fun AfinitySplashScreen(
    statusText: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    val isLightBackground = backgroundColor.luminance() > 0.5f
    val isAmoled = backgroundColor == Color.Black

    val glowAlpha =
        when {
            isLightBackground -> 0.5f
            isAmoled -> 0.15f
            else -> 0.25f
        }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val textAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 300),
        label = "text_alpha",
    )

    val textOffset by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 16.dp,
        animationSpec = tween(durationMillis = 1000, delayMillis = 300, easing = EaseOutExpo),
        label = "text_offset",
    )

    val bottomAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 450),
        label = "bottom_alpha",
    )

    val bottomOffset by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 1000, delayMillis = 450, easing = EaseOutExpo),
        label = "bottom_offset",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo_scale",
    )

    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aura_scale",
    )

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -screenWidthPx * 0.5f,
        targetValue = screenWidthPx * 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onBackground,
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        ),
        start = Offset(shimmerTranslate, 0f),
        end = Offset(shimmerTranslate + screenWidthPx * 0.3f, 0f),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .drawBehind {
                            val baseRadius = (size.width / 2.8f) * logoScale

                            val ringSpacing = 20.dp.toPx()

                            for (i in 3 downTo 0) {
                                val currentRadius = baseRadius + (ringSpacing * i * auraScale)

                                val alphaMultiplier = when (i) {
                                    3 -> 0.05f
                                    2 -> 0.1f
                                    1 -> 0.15f
                                    else -> 0.2f
                                }

                                drawCircle(
                                    color = primaryColor.copy(alpha = glowAlpha * alphaMultiplier),
                                    radius = currentRadius
                                )
                            }
                        }
                )
                val logoShimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    start = Offset(shimmerTranslate, 0f),
                    end = Offset(shimmerTranslate + screenWidthPx * 0.3f, 0f)
                )

                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_splash),
                    contentDescription = stringResource(R.string.cd_app_logo),
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                            alpha = 0.99f
                        }
                        .drawWithCache {
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = logoShimmerBrush,
                                    blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop
                                )
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    brush = shimmerBrush,
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha
                    translationY = textOffset.toPx()
                },
            )

            Spacer(modifier = Modifier.height(48.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = bottomAlpha
                    translationY = bottomOffset.toPx()
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AnimatedContent(
                        targetState = statusText,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                        label = "status_text_transition",
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }

                    if (progress != null) {
                        val animatedProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = tween(500),
                            label = "pill_progress",
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (progress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = primaryColor,
                        strokeWidth = 2.dp,
                    )
                } else {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(500),
                        label = "pill_progress",
                    )

                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(3.dp)
                            .background(
                                color = primaryColor.copy(alpha = 0.2f),
                                shape = CircleShape,
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            primaryColor.copy(alpha = 0.6f),
                                            primaryColor,
                                        )
                                    ),
                                    shape = CircleShape,
                                )
                        )
                    }
                }
            }
        }
    }
}