package com.makd.afinity.ui.components

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.asPainter
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.vanniktech.blurhash.BlurHash
import timber.log.Timber

internal const val IMAGE_CROSSFADE_MILLIS = 80

private const val BLUR_HASH_BASE_SIZE = 32
private const val BLUR_HASH_MIN_SIZE = 25
private const val BLUR_HASH_CACHE_ENTRIES = 256

private val blurHashBitmapCache = LruCache<String, ImageBitmap>(BLUR_HASH_CACHE_ENTRIES)

private val FILL_WIDTH_BUCKETS = intArrayOf(160, 240, 320, 480, 640, 960, 1280, 1920)

private fun bucketedFillWidth(widthPx: Int): Int =
    FILL_WIDTH_BUCKETS.firstOrNull { it >= widthPx } ?: widthPx

internal fun optimizedImageUrl(imageUrl: String?, widthPx: Int): String? {
    if (imageUrl == null) return imageUrl
    if (!imageUrl.contains("/Items/") || !imageUrl.contains("/Images/")) return imageUrl
    if (imageUrl.contains("fillWidth") || imageUrl.contains("maxWidth")) return imageUrl

    val separator = if ('?' in imageUrl) "&" else "?"
    val quality = if (imageUrl.contains("quality=")) "" else "&quality=90"
    return "${imageUrl}${separator}fillWidth=${bucketedFillWidth(widthPx.coerceAtLeast(50))}$quality"
}

private fun snapBlurHashDimension(value: Int): Int =
    if (value >= (BLUR_HASH_MIN_SIZE + BLUR_HASH_BASE_SIZE) / 2) BLUR_HASH_BASE_SIZE
    else BLUR_HASH_MIN_SIZE

private fun decodeBlurHashBitmap(blurHash: String, width: Int, height: Int): ImageBitmap? =
    try {
        BlurHash.decode(blurHash = blurHash, width = width, height = height)?.asImageBitmap()
    } catch (e: Exception) {
        Timber.w("Failed to decode blur hash: ${e.message}")
        null
    }

@Composable
private fun rememberBlurHashPainter(
    blurHash: String?,
    targetWidth: Dp?,
    targetHeight: Dp?,
): Painter? {
    val decodeSize =
        remember(targetWidth, targetHeight) {
            val ratio =
                if (targetWidth != null && targetHeight != null && targetHeight.value > 0f) {
                    targetWidth.value / targetHeight.value
                } else {
                    1f
                }
            val width =
                if (ratio > 1) BLUR_HASH_BASE_SIZE
                else snapBlurHashDimension((BLUR_HASH_BASE_SIZE * ratio).toInt())
            val height =
                if (ratio < 1) BLUR_HASH_BASE_SIZE
                else snapBlurHashDimension((BLUR_HASH_BASE_SIZE / ratio).toInt())
            width to height
        }

    return remember(blurHash, decodeSize) {
        if (blurHash.isNullOrBlank()) return@remember null

        val cacheKey = "$blurHash|${decodeSize.first}x${decodeSize.second}"
        val bitmap =
            blurHashBitmapCache.get(cacheKey)
                ?: decodeBlurHashBitmap(blurHash, decodeSize.first, decodeSize.second)?.also {
                    blurHashBitmapCache.put(cacheKey, it)
                }
        bitmap?.let { BitmapPainter(it) }
    }
}

@Immutable
private class LowResPlaceholder(val memoryCacheKey: String, val painter: Painter)

@Composable
private fun rememberCachedLowResPlaceholder(
    enabled: Boolean,
    imageUrl: String?,
    targetWidthPx: Int?,
    filterQuality: FilterQuality,
): LowResPlaceholder? {
    val context = LocalContext.current

    return remember(enabled, imageUrl, targetWidthPx, filterQuality) {
        if (!enabled || imageUrl == null || targetWidthPx == null) return@remember null
        if (!imageUrl.contains("/Items/") || !imageUrl.contains("/Images/")) return@remember null
        if (imageUrl.contains("fillWidth") || imageUrl.contains("maxWidth")) return@remember null

        val memoryCache = context.imageLoader.memoryCache ?: return@remember null
        val targetBucket = bucketedFillWidth(targetWidthPx.coerceAtLeast(50))

        for (index in FILL_WIDTH_BUCKETS.indices.reversed()) {
            val bucket = FILL_WIDTH_BUCKETS[index]
            if (bucket >= targetBucket) continue
            val candidate = optimizedImageUrl(imageUrl, bucket) ?: continue
            val cached = memoryCache[MemoryCache.Key(candidate)] ?: continue
            return@remember LowResPlaceholder(
                memoryCacheKey = candidate,
                painter = cached.image.asPainter(context, filterQuality),
            )
        }
        null
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
    useLowResPlaceholder: Boolean = false,
    crossfadeMillis: Int = IMAGE_CROSSFADE_MILLIS,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val targetWidthPx =
        remember(targetWidth, density, scaleFactor) {
            targetWidth?.let { with(density) { (it.toPx() * scaleFactor).toInt() } }
        }

    val imageSize =
        remember(targetWidthPx, targetHeight, density, scaleFactor) {
            val heightPx =
                targetHeight?.let { with(density) { (it.toPx() * scaleFactor).toInt() } }
            if (targetWidthPx != null && heightPx != null) {
                Size(width = targetWidthPx, height = heightPx)
            } else {
                Size.ORIGINAL
            }
        }

    val optimizedUrl =
        remember(imageUrl, targetWidthPx) {
            if (targetWidthPx == null) imageUrl else optimizedImageUrl(imageUrl, targetWidthPx)
        }

    val blurHashPlaceholder = rememberBlurHashPainter(blurHash, targetWidth, targetHeight)
    val lowResPlaceholder =
        rememberCachedLowResPlaceholder(
            enabled = useLowResPlaceholder,
            imageUrl = imageUrl,
            targetWidthPx = targetWidthPx,
            filterQuality = filterQuality,
        )
    val loadingPlaceholder = lowResPlaceholder?.painter ?: blurHashPlaceholder ?: placeholder

    AsyncImage(
        model =
            ImageRequest.Builder(context)
                .data(optimizedUrl)
                .size(imageSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(
                    if (imageUrl?.startsWith("file://") == true) CachePolicy.DISABLED
                    else CachePolicy.ENABLED
                )
                .crossfade(crossfadeMillis)
                .placeholderMemoryCacheKey(lowResPlaceholder?.memoryCacheKey)
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
        placeholder = loadingPlaceholder,
        error = error,
        fallback = loadingPlaceholder ?: error,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
    )
}
