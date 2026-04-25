package com.makd.afinity.ui.item.components.shared

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.showBackdropBlurHash
import com.makd.afinity.data.models.extensions.showBackdropImageUrl
import com.makd.afinity.data.models.extensions.showPrimaryBlurHash
import com.makd.afinity.data.models.extensions.showPrimaryImageUrl
import com.makd.afinity.data.models.extensions.showThumbBlurHash
import com.makd.afinity.data.models.extensions.showThumbImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun HeroSection(
    item: AfinityItem,
    localTrailerUrl: String? = null,
    isTrailerPlaying: Boolean = false,
    isTrailerMuted: Boolean = true,
    onDismissTrailer: () -> Unit = {},
    onToggleMute: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val heightMultiplier = if (isLandscape) 0.9f else 0.5f

    val normalHeight = screenHeight * heightMultiplier
    val trailerHeight =
        if (isLandscape) {
            minOf(screenWidth * (9f / 16f), screenHeight * 0.9f)
        } else {
            screenWidth * (9f / 16f)
        }

    val heroHeight by
        animateDpAsState(
            targetValue = if (isTrailerPlaying) trailerHeight else normalHeight,
            animationSpec = tween(500),
            label = "heroHeight",
        )

    val fallbackChain =
        remember(item.id) {
            when (item) {
                is AfinityShow ->
                    listOfNotNull(
                        item.images.backdropImageUrl?.let { it to item.images.backdropBlurHash },
                        item.images.thumbImageUrl?.let { it to item.images.thumbBlurHash },
                        item.images.primaryImageUrl?.let { it to item.images.primaryBlurHash },
                    )
                is AfinitySeason ->
                    listOfNotNull(
                        item.images.backdropImageUrl?.let { it to item.images.backdropBlurHash },
                        item.images.showBackdropImageUrl?.let {
                            it to item.images.showBackdropBlurHash
                        },
                        item.images.thumbImageUrl?.let { it to item.images.thumbBlurHash },
                        item.images.showThumbImageUrl?.let { it to item.images.showThumbBlurHash },
                        item.images.primaryImageUrl?.let { it to item.images.primaryBlurHash },
                        item.images.showPrimaryImageUrl?.let {
                            it to item.images.showPrimaryBlurHash
                        },
                    )
                else ->
                    listOfNotNull(
                        item.images.backdropImageUrl?.let { it to item.images.backdropBlurHash },
                        item.images.primaryImageUrl?.let { it to item.images.primaryBlurHash },
                    )
            }
        }

    var urlIndex by remember(item.id) { mutableIntStateOf(0) }
    val current = fallbackChain.getOrNull(urlIndex)

    var isVideoReady by remember { mutableStateOf(false) }
    LaunchedEffect(isTrailerPlaying) { if (!isTrailerPlaying) isVideoReady = false }

    val backdropAlpha by
        animateFloatAsState(
            targetValue = if (isTrailerPlaying && isVideoReady) 0f else 1f,
            animationSpec = tween(600),
            label = "backdropAlpha",
        )

    var showControls by remember { mutableStateOf(true) }
    var controlsToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(isTrailerPlaying, isVideoReady, controlsToken) {
        if (isTrailerPlaying && isVideoReady) {
            showControls = true
            delay(3000)
            showControls = false
        }
    }
    val controlsAlpha by
        animateFloatAsState(
            targetValue = if (showControls) 1f else 0f,
            animationSpec = tween(400),
            label = "controlsAlpha",
        )

    Box(
        modifier =
            Modifier.fillMaxWidth().height(heroHeight).pointerInput(isTrailerPlaying) {
                if (isTrailerPlaying) detectTapGestures { controlsToken++ }
            }
    ) {
        if (isTrailerPlaying && localTrailerUrl != null) {
            TrailerPlayer(
                trailerUrl = localTrailerUrl,
                isMuted = isTrailerMuted,
                onVideoReady = { isVideoReady = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        AsyncImage(
            imageUrl = current?.first,
            contentDescription = null,
            blurHash = current?.second,
            targetWidth = configuration.screenWidthDp.dp,
            targetHeight = screenHeight * heightMultiplier,
            onError = { if (urlIndex < fallbackChain.lastIndex) urlIndex++ },
            modifier =
                Modifier.fillMaxSize()
                    .alpha(backdropAlpha)
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val gradient =
                            Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height * 0.75f,
                                endY = size.height,
                            )
                        onDrawWithContent {
                            drawContent()
                            drawRect(gradient, blendMode = BlendMode.DstIn)
                        }
                    },
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )

        if (isTrailerPlaying) {
            Box(modifier = Modifier.fillMaxSize().alpha(controlsAlpha)) {
                IconButton(
                    onClick = onDismissTrailer,
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Dismiss trailer",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                IconButton(
                    onClick = {
                        controlsToken++
                        onToggleMute()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isTrailerMuted) R.drawable.ic_volume_off
                                else R.drawable.ic_volume_up
                            ),
                        contentDescription = if (isTrailerMuted) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
