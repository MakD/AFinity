package com.makd.afinity.ui.components

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
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
import com.vanniktech.blurhash.BlurHash
import timber.log.Timber

private const val BLUR_HASH_BASE_SIZE = 32
private const val BLUR_HASH_MIN_SIZE = 25
private const val BLUR_HASH_CACHE_ENTRIES = 256

private val blurHashPainterCache = LruCache<String, BitmapPainter>(BLUR_HASH_CACHE_ENTRIES)

private val FILL_WIDTH_BUCKETS = intArrayOf(160, 240, 320, 480, 640, 960, 1280, 1920)

private fun bucketedFillWidth(widthPx: Int): Int =
    FILL_WIDTH_BUCKETS.firstOrNull { it >= widthPx } ?: widthPx

private fun decodeBlurHashPainter(blurHash: String, width: Int, height: Int): BitmapPainter? =
    try {
        BlurHash.decode(blurHash = blurHash, width = width, height = height)
            ?.asImageBitmap()
            ?.let { BitmapPainter(it) }
    } catch (e: Exception) {
        Timber.w("Failed to decode blur hash: ${e.message}")
        null
    }

@Composable
private fun rememberBlurHashPainter(
    blurHash: String?,
    targetWidth: Dp?,
    targetHeight: Dp?,
): BitmapPainter? {
    val decodeSize =
        remember(targetWidth, targetHeight) {
            val ratio =
                if (targetWidth != null && targetHeight != null && targetHeight.value > 0f) {
                    targetWidth.value / targetHeight.value
                } else {
                    1f
                }
            val width =
                if (ratio > 1) BLUR_HASH_BASE_SIZE else (BLUR_HASH_BASE_SIZE * ratio).toInt()
            val height =
                if (ratio < 1) BLUR_HASH_BASE_SIZE else (BLUR_HASH_BASE_SIZE / ratio).toInt()
            width.coerceAtLeast(BLUR_HASH_MIN_SIZE) to height.coerceAtLeast(BLUR_HASH_MIN_SIZE)
        }

    return remember(blurHash, decodeSize) {
        if (blurHash.isNullOrBlank()) return@remember null

        val cacheKey = "$blurHash|${decodeSize.first}x${decodeSize.second}"
        blurHashPainterCache.get(cacheKey)
            ?: decodeBlurHashPainter(blurHash, decodeSize.first, decodeSize.second)?.also {
                blurHashPainterCache.put(cacheKey, it)
            }
    }
}

@Composable
fun AsyncImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    onLoading: ((Boolean) -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = FilterQuality.Low,
    blurHash: String? = null,
    targetWidth: Dp? = null,
    targetHeight: Dp? = null,
    scaleFactor: Float = 1.0f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val imageSize =
        remember(targetWidth, targetHeight, density, scaleFactor) {
            when {
                targetWidth != null && targetHeight != null -> {
                    Size(
                        width = with(density) { (targetWidth.toPx() * scaleFactor).toInt() },
                        height = with(density) { (targetHeight.toPx() * scaleFactor).toInt() },
                    )
                }
                else -> Size.ORIGINAL
            }
        }

    val optimizedUrl =
        remember(imageUrl, targetWidth, density, scaleFactor) {
            if (
                imageUrl != null &&
                    targetWidth != null &&
                    imageUrl.contains("/Items/") &&
                    imageUrl.contains("/Images/") &&
                    !imageUrl.contains("fillWidth") &&
                    !imageUrl.contains("maxWidth")
            ) {
                val widthPx =
                    with(density) { (targetWidth.toPx() * scaleFactor).toInt() }.coerceAtLeast(50)
                val separator = if ('?' in imageUrl) "&" else "?"
                val quality = if (imageUrl.contains("quality=")) "" else "&quality=90"
                "${imageUrl}${separator}fillWidth=${bucketedFillWidth(widthPx)}$quality"
            } else {
                imageUrl
            }
        }

    val blurHashPlaceholder = rememberBlurHashPainter(blurHash, targetWidth, targetHeight)

    AsyncImage(
        model =
            ImageRequest.Builder(context)
                .data(optimizedUrl)
                .size(imageSize)
                .memoryCacheKey(optimizedUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(
                    if (imageUrl?.startsWith("file://") == true) CachePolicy.DISABLED
                    else CachePolicy.ENABLED
                )
                .crossfade(true)
                .placeholderMemoryCacheKey(blurHash)
                .listener(
                    onStart = { onLoading?.invoke(true) },
                    onSuccess = { _, _ ->
                        onLoading?.invoke(false)
                        onSuccess?.invoke()
                    },
                    onError = { _, _ ->
                        onLoading?.invoke(false)
                        onError?.invoke()
                    },
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
        filterQuality = filterQuality,
    )
}
