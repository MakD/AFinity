package com.makd.afinity.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

enum class BackdropStyle {
    Cinematic,
    BlurredCover,
    BlurredPhoto,
}

fun backdropChainOf(imageUrl: String?, blurHash: String? = null): List<Pair<String, String?>> =
    listOfNotNull(imageUrl?.let { it to blurHash })

@Composable
fun DetailBackdrop(
    style: BackdropStyle,
    imageUrl: String?,
    blurHash: String? = null,
    contentDescription: String? = null,
) {
    DetailBackdrop(style, backdropChainOf(imageUrl, blurHash), contentDescription)
}

@Composable
fun DetailBackdrop(
    style: BackdropStyle,
    fallbackChain: List<Pair<String, String?>>,
    contentDescription: String? = null,
) {
    var backdropIndex by remember(fallbackChain) { mutableIntStateOf(0) }
    val backdrop = fallbackChain.getOrNull(backdropIndex)
    val onError = {
        if (backdropIndex < fallbackChain.lastIndex) {
            backdropIndex++
        }
    }

    when (style) {
        BackdropStyle.Cinematic -> CinematicBackdrop(backdrop, contentDescription, onError)
        BackdropStyle.BlurredCover -> BlurredCoverBackdrop(backdrop, contentDescription, onError)
        BackdropStyle.BlurredPhoto -> BlurredPhotoBackdrop(backdrop, contentDescription, onError)
    }
}

@Composable
private fun CinematicBackdrop(
    backdrop: Pair<String, String?>?,
    contentDescription: String?,
    onError: () -> Unit,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val screenHeightDp = with(density) { windowInfo.containerSize.height.toDp() }

    if (backdrop != null) {
        AsyncImage(
            imageUrl = backdrop.first,
            contentDescription = contentDescription,
            blurHash = backdrop.second,
            targetWidth = screenWidthDp,
            targetHeight = screenHeightDp,
            useLowResPlaceholder = true,
            onError = onError,
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

@Composable
private fun BlurredCoverBackdrop(
    backdrop: Pair<String, String?>?,
    contentDescription: String?,
    onError: () -> Unit,
) {
    if (backdrop != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                imageUrl = backdrop.first,
                contentDescription = contentDescription,
                onError = onError,
                modifier = Modifier.fillMaxSize().blur(radius = 30.dp).background(Color.Black),
                alpha = 0.6f,
                contentScale = ContentScale.Crop,
            )
            val surfaceColor = MaterialTheme.colorScheme.surface
            val surfaceHalfAlpha = surfaceColor.copy(alpha = 0.5f)
            Box(
                modifier =
                    Modifier.fillMaxSize().drawWithCache {
                        val gradient =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        surfaceHalfAlpha,
                                        surfaceColor,
                                    ),
                                startY = size.height * 0.25f,
                                endY = size.height,
                            )
                        onDrawBehind { drawRect(gradient) }
                    }
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
    }
}

@Composable
private fun BlurredPhotoBackdrop(
    backdrop: Pair<String, String?>?,
    contentDescription: String?,
    onError: () -> Unit,
) {
    val backdropWidth = windowWidthDp()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (backdrop != null) {
            AsyncImage(
                imageUrl = backdrop.first,
                contentDescription = contentDescription,
                blurHash = backdrop.second,
                onError = onError,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(radius = 20.dp).alpha(0.6f),
                targetWidth = backdropWidth,
                targetHeight = backdropWidth,
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
    }
}
