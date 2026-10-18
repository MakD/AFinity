package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

private val HeroDetailMaxHeight = 560.dp

@Composable
fun HeroSection(item: AfinityItem) {
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

    HeroSection(fallbackChain = fallbackChain)
}

@Composable
fun rememberLandscapeDetailColorScheme(): ColorScheme {
    val baseColorScheme = MaterialTheme.colorScheme
    return remember(baseColorScheme) {
        baseColorScheme.copy(
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.7f),
            outline = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
fun DetailBackdrop(fallbackChain: List<Pair<String, String?>>, contentDescription: String?) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val screenHeightDp = with(density) { windowInfo.containerSize.height.toDp() }

    var backdropIndex by remember(fallbackChain) { mutableIntStateOf(0) }
    val backdrop = fallbackChain.getOrNull(backdropIndex)

    if (backdrop != null) {
        AsyncImage(
            imageUrl = backdrop.first,
            contentDescription = contentDescription,
            blurHash = backdrop.second,
            targetWidth = screenWidthDp,
            targetHeight = screenHeightDp,
            useLowResPlaceholder = true,
            onError = {
                if (backdropIndex < fallbackChain.lastIndex) {
                    backdropIndex++
                }
            },
            modifier = Modifier.fillMaxSize().blur(0.dp),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

    Image(
        painter = painterResource(id = R.drawable.mask),
        contentDescription = stringResource(R.string.cd_mask_overlay),
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroSection(fallbackChain: List<Pair<String, String?>>) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val containerSize = windowInfo.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val screenHeight = with(density) { containerSize.height.toDp() }
    val isLandscape = containerSize.width > containerSize.height
    val heightMultiplier = if (isLandscape) 0.9f else 0.5f
    val heroHeight = (screenHeight * heightMultiplier).coerceAtMost(HeroDetailMaxHeight)

    var urlIndex by remember(fallbackChain) { mutableIntStateOf(0) }
    val current = fallbackChain.getOrNull(urlIndex)

    Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
        AsyncImage(
            imageUrl = current?.first,
            contentDescription = null,
            blurHash = current?.second,
            targetWidth = screenWidth,
            targetHeight = heroHeight,
            useLowResPlaceholder = true,
            onError = {
                if (urlIndex < fallbackChain.lastIndex) {
                    urlIndex++
                }
            },
            modifier =
                Modifier.fillMaxSize()
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
            alignment = Alignment.TopCenter,
        )
    }
}
