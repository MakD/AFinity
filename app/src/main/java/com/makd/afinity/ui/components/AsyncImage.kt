package com.makd.afinity.ui.components

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.compose.asPainter
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.makd.afinity.navigation.LocalSkipServerImageResize
import com.vanniktech.blurhash.BlurHash
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.math.roundToInt

internal const val IMAGE_CROSSFADE_MILLIS = 80

private const val BLUR_HASH_BASE_SIZE = 32
private const val BLUR_HASH_MIN_SIZE = 25
private const val BLUR_HASH_CACHE_ENTRIES = 256

private val blurHashBitmapCache = LruCache<String, ImageBitmap>(BLUR_HASH_CACHE_ENTRIES)

private val blurHashDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "blurhash-decode").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
        .asCoroutineDispatcher()

private const val IMAGE_QUALITY = 80

private val FILL_WIDTH_BUCKETS = intArrayOf(160, 240, 320, 480, 640, 960, 1280, 1920)

private val SIZE_PARAMS = listOf("fillWidth", "fillHeight", "maxWidth", "maxHeight")

private fun bucketedFillWidth(widthPx: Int): Int =
    FILL_WIDTH_BUCKETS.firstOrNull { it >= widthPx } ?: FILL_WIDTH_BUCKETS.last()

private fun String.isResizableItemImage(): Boolean =
    contains("/Items/") && contains("/Images/") && SIZE_PARAMS.none { contains(it) }

internal fun optimizedImageUrl(
    imageUrl: String?,
    widthPx: Int,
    skipServerResize: Boolean = false,
): String? {
    if (imageUrl == null) return imageUrl
    if (skipServerResize) return imageUrl
    if (!imageUrl.isResizableItemImage()) return imageUrl

    val separator = if ('?' in imageUrl) "&" else "?"
    val quality = if (imageUrl.contains("quality=")) "" else "&quality=$IMAGE_QUALITY"
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

private class BlurHashPainter(initial: ImageBitmap?) : Painter() {
    var bitmap by mutableStateOf(initial)

    override val intrinsicSize: androidx.compose.ui.geometry.Size
        get() = androidx.compose.ui.geometry.Size.Unspecified

    override fun DrawScope.onDraw() {
        val image = bitmap ?: return
        drawImage(
            image = image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
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

    val cacheKey =
        remember(blurHash, decodeSize) {
            if (blurHash.isNullOrBlank()) null
            else "$blurHash|${decodeSize.first}x${decodeSize.second}"
        }

    val painter =
        remember(cacheKey) { cacheKey?.let { BlurHashPainter(blurHashBitmapCache.get(it)) } }

    LaunchedEffect(cacheKey) {
        if (cacheKey == null || blurHash == null || painter == null) return@LaunchedEffect
        if (painter.bitmap != null) return@LaunchedEffect

        val bitmap =
            withContext(blurHashDispatcher) {
                decodeBlurHashBitmap(blurHash, decodeSize.first, decodeSize.second)
            }
        if (bitmap != null) {
            blurHashBitmapCache.put(cacheKey, bitmap)
            painter.bitmap = bitmap
        }
    }

    return painter
}

@Immutable private class LowResPlaceholder(val memoryCacheKey: String, val painter: Painter)

@Composable
private fun rememberCachedLowResPlaceholder(
    enabled: Boolean,
    imageUrl: String?,
    targetWidthPx: Int?,
    filterQuality: FilterQuality,
): LowResPlaceholder? {
    val context = LocalContext.current

    val skipServerResize = LocalSkipServerImageResize.current

    return remember(enabled, imageUrl, targetWidthPx, filterQuality, skipServerResize) {
        if (!enabled || imageUrl == null || targetWidthPx == null) return@remember null
        if (skipServerResize) return@remember null
        if (!imageUrl.isResizableItemImage()) return@remember null

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
            val heightPx = targetHeight?.let { with(density) { (it.toPx() * scaleFactor).toInt() } }
            if (targetWidthPx != null && heightPx != null) {
                Size(width = targetWidthPx, height = heightPx)
            } else {
                null
            }
        }

    val skipServerResize = LocalSkipServerImageResize.current

    val optimizedUrl =
        remember(imageUrl, targetWidthPx, skipServerResize) {
            if (targetWidthPx == null) imageUrl
            else optimizedImageUrl(imageUrl, targetWidthPx, skipServerResize)
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
                .apply { imageSize?.let { size(it) } }
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
