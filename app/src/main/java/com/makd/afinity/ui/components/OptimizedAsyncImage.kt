package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import timber.log.Timber

@Composable
fun OptimizedAsyncImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    onLoading: ((Boolean) -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: androidx.compose.ui.graphics.FilterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
    blurHash: String? = null,
    targetWidth: Dp? = null,
    targetHeight: Dp? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val imageSize = remember(targetWidth, targetHeight, density) {
        when {
            targetWidth != null && targetHeight != null -> {
                Size(
                    width = with(density) { (targetWidth.toPx() * 1.2f).toInt() },
                    height = with(density) { (targetHeight.toPx() * 1.2f).toInt() }
                )
            }

            else -> Size.ORIGINAL
        }
    }

    val blurHashPlaceholder = remember(blurHash) {
        blurHash?.let { hash ->
            try {
                val bitmap = com.vanniktech.blurhash.BlurHash.decode(
                    blurHash = hash,
                    width = 20,
                    height = 20
                )
                bitmap?.let { BitmapPainter(it.asImageBitmap()) }
            } catch (e: Exception) {
                Timber.w("Failed to decode blur hash: ${e.message}")
                null
            }
        }
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(imageSize)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(if (imageUrl?.startsWith("file://") == true) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .crossfade(true)
            .placeholderMemoryCacheKey(blurHash)
            .listener(
                onStart = { onLoading?.invoke(true) },
                onSuccess = { _, _ ->
                    onLoading?.invoke(false)
                    onSuccess?.invoke()
                },
                onError = { _, result ->
                    onLoading?.invoke(false)
                    onError?.invoke()
                }
            )
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = blurHashPlaceholder ?: placeholder,
        error = error,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality
    )
}