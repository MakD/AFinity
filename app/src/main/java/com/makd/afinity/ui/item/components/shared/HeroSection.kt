package com.makd.afinity.ui.item.components.shared

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroSection(item: AfinityItem, onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val heightMultiplier = if (isLandscape) 0.9f else 0.5f

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

    Box(modifier = Modifier.fillMaxWidth().height(screenHeight * heightMultiplier)) {
        AsyncImage(
            imageUrl = current?.first,
            contentDescription = null,
            blurHash = current?.second,
            targetWidth = configuration.screenWidthDp.dp,
            targetHeight = screenHeight * heightMultiplier,
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
            alignment = Alignment.Center,
        )
    }
}
