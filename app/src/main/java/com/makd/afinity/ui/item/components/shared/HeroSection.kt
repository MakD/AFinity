package com.makd.afinity.ui.item.components.shared

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.OptimizedAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroSection(
    item: AfinityItem,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val heightMultiplier = if (isLandscape) 0.9f else 0.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(screenHeight * heightMultiplier)
    ) {
        OptimizedAsyncImage(
            imageUrl = item.images.backdropImageUrl ?: item.images.primaryImageUrl,
            contentDescription = null,
            blurHash = item.images.backdropBlurHash ?: item.images.primaryBlurHash,
            targetWidth = configuration.screenWidthDp.dp,
            targetHeight = screenHeight * heightMultiplier,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.99f }
                .drawWithCache {
                    val gradient = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black,
                            Color.Transparent
                        ),
                        startY = size.height * 0.75f,
                        endY = size.height
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(gradient, blendMode = BlendMode.DstIn)
                    }
                },
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )
    }
}