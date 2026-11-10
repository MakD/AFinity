package com.makd.afinity.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.makd.afinity.R
import com.makd.afinity.data.models.HeroContentSource
import com.makd.afinity.data.models.HeroIndicator
import com.makd.afinity.data.models.common.HeroStyle
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.logoImageUrlWithTransparency
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.LocalHeroStyle
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.utils.bottomOverlap
import java.util.Locale
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import timber.log.Timber

private val HeroMaxHeight = 560.dp
private val HeroBottomOverlap = 40.dp
internal val HeroFadeHeight = 135.dp
private const val HeroDimAlpha = 0.25f
private const val HeroPortraitFraction = 0.65f
private const val HeroLandscapeFraction = 0.95f
private val HeroLogoHeight = 140.dp
private const val HeroLogoPortraitWidthFraction = 0.8f
private const val HeroLogoLandscapeWidthFraction = 0.4f
private const val HeroLandscapeLogoHeightFraction = 0.25f
private const val LIGHT_BACKGROUND_LUMINANCE = 0.5f

private const val HeroToneSampleWidth = 160
private const val HeroCenteredToneWidthFraction = 0.7f
private val HeroDarkText = Color(0xFF111111)
private val HeroTextShadowBlur = 6.dp
private const val HeroTextShadowAlpha = 0.45f

private val HeroPeekTopGap = 8.dp
private const val HeroPeekPortraitFraction = 0.6f
private val HeroPeekMaxCardHeight = 520.dp
private val HeroPeekMinCardHeight = 220.dp
private val HeroPeekIndicatorHeight = 32.dp
private val HeroPeekPortraitPadding = 40.dp
private val HeroPeekLandscapePadding = 48.dp
private val HeroPeekPageSpacing = 10.dp
private const val HeroPeekSideScale = 0.92f
private val HeroInsetPadding = 16.dp
private val HeroInsetPageSpacing = 16.dp
private val HeroPeekLogoHeight = 120.dp
private const val HeroPeekLogoWidthFraction = 0.85f
private val HeroPeekLandscapeLogoHeight = 88.dp
private const val HeroPeekLandscapeContentFraction = 0.55f
private const val HeroPeekLandscapeLogoWidthFraction = 0.8f
private val HeroPeekLandscapeContentStart = 28.dp
private val HeroPeekLandscapeContentBottom = 24.dp
private val HeroPeekLandscapeButtonSize = 44.dp
private val HeroButtonSize = 48.dp
private val HeroButtonIconSize = 22.dp
private val HeroButtonPlayIconSize = 24.dp
private val HeroRichButtonSize = 36.dp
private val HeroButtonMinSize = 40.dp
private val HeroButtonMaxSize = 56.dp

internal val HeroFadeStops =
    listOf(
        Color.Black,
        Color.Black.copy(alpha = 0.98f),
        Color.Black.copy(alpha = 0.91f),
        Color.Black.copy(alpha = 0.82f),
        Color.Black.copy(alpha = 0.70f),
        Color.Black.copy(alpha = 0.57f),
        Color.Black.copy(alpha = 0.43f),
        Color.Black.copy(alpha = 0.30f),
        Color.Black.copy(alpha = 0.18f),
        Color.Black.copy(alpha = 0.09f),
        Color.Black.copy(alpha = 0.02f),
        Color.Transparent,
    )

private val HeroLandscapeSideScrim =
    Brush.horizontalGradient(
        0f to Color.Black.copy(alpha = 0.85f),
        0.4f to Color.Black.copy(alpha = 0.47f),
        0.7f to Color.Transparent,
    )

private val HeroPeekPortraitScrim =
    Brush.verticalGradient(
        0f to Color.Transparent,
        0.3f to Color.Transparent,
        0.6f to Color.Black.copy(alpha = 0.48f),
        1f to Color.Black.copy(alpha = 0.88f),
    )

private val HeroPeekLandscapeBottomScrim =
    Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.51f))

private val LocalHeroIndicator = staticCompositionLocalOf { HeroIndicator.DOTS }

private val HeroFadeStopAlphas = FloatArray(HeroFadeStops.size) { HeroFadeStops[it].alpha }

private val LocalHeroPageColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

private val LocalHeroTextShadow = staticCompositionLocalOf<Shadow?> { null }

private class HeroTextRegionTracker {
    var container: LayoutCoordinates? = null
    var column: LayoutCoordinates? = null
    var logo: LayoutCoordinates? = null
    var region by mutableStateOf<HeroTextRegion?>(null)
        private set

    fun update() {
        val container = container?.takeIf { it.isAttached } ?: return
        val column = column?.takeIf { it.isAttached } ?: return
        val bounds = container.localBoundingBoxOf(column, clipBounds = false)
        val top =
            logo
                ?.takeIf { it.isAttached }
                ?.let { container.localBoundingBoxOf(it, clipBounds = false).bottom } ?: bounds.top
        region =
            HeroTextRegion(
                left = bounds.left,
                top = top,
                right = bounds.right,
                bottom = bounds.bottom,
                heroWidth = container.size.width.toFloat(),
                heroHeight = container.size.height.toFloat(),
            )
    }
}

private data class HeroPeekMetrics(val top: Dp, val cardHeight: Dp, val indicatorHeight: Dp) {
    val total: Dp
        get() = top + cardHeight + indicatorHeight
}

private fun heroLogoUrl(item: AfinityItem): String? = item.images.logoImageUrlWithTransparency()

private fun heroGenres(item: AfinityItem): List<String> =
    when (item) {
        is AfinityMovie -> item.genres
        is AfinityShow -> item.genres
        else -> emptyList()
    }

private fun PagerState.offsetFrom(page: Int): Float =
    (currentPage - page) + currentPageOffsetFraction

private fun heroBackdropUrl(item: AfinityItem): String? =
    item.images.backdropImageUrl ?: item.images.primaryImageUrl

private suspend fun loadHeroBackdropSample(context: Context, url: String): HeroBackdropSample? {
    val request =
        ImageRequest.Builder(context)
            .data(optimizedImageUrl(url, HeroToneSampleWidth))
            .size(HeroToneSampleWidth, HeroToneSampleWidth)
            .allowHardware(false)
            .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    val decoded = result.image.asDrawable(context.resources).toBitmap()
    val bitmap =
        if (decoded.config == Bitmap.Config.HARDWARE) {
            decoded.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else {
            decoded
        }
    if (bitmap.width <= 0 || bitmap.height <= 0) return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return HeroBackdropSample(bitmap.width, bitmap.height, pixels)
}

private fun heroTextTone(
    sample: HeroBackdropSample?,
    region: HeroTextRegion?,
    isLandscape: Boolean,
    fadeHeight: Float,
    pageColor: Color,
): HeroTextTone? {
    if (sample == null || region == null) return null
    val sampled =
        if (isLandscape) {
            region
        } else {
            val inset = (region.right - region.left) * (1f - HeroCenteredToneWidthFraction) / 2f
            region.copy(left = region.left + inset, right = region.right - inset)
        }
    return measureHeroTextTone(
            sample = sample,
            region = sampled,
            fadeStart = max(region.heroHeight - fadeHeight, region.heroHeight * 0.35f),
            fadeStops = HeroFadeStopAlphas,
            dimAlpha = HeroDimAlpha,
            pageArgb = pageColor.toArgb(),
            darkTextArgb = HeroDarkText.toArgb(),
        )
        ?.tone
}

@Composable
private fun heroPeekMetrics(screenHeightDp: Dp, isLandscape: Boolean): HeroPeekMetrics {
    val top =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            afinityTopAppBarHeight() +
            HeroPeekTopGap
    return if (isLandscape) {
        HeroPeekMetrics(
            top = top,
            cardHeight =
                ((screenHeightDp * HeroLandscapeFraction).coerceAtMost(HeroMaxHeight) - top)
                    .coerceAtLeast(HeroPeekMinCardHeight),
            indicatorHeight = 0.dp,
        )
    } else {
        HeroPeekMetrics(
            top = top,
            cardHeight =
                (screenHeightDp * HeroPeekPortraitFraction).coerceAtMost(HeroPeekMaxCardHeight),
            indicatorHeight = HeroPeekIndicatorHeight,
        )
    }
}

private fun heroCardSidePadding(isLandscape: Boolean, peek: Boolean): Dp =
    when {
        !peek -> HeroInsetPadding
        isLandscape -> HeroPeekLandscapePadding
        else -> HeroPeekPortraitPadding
    }

private fun heroFullBleedHeight(screenHeightDp: Dp, isLandscape: Boolean): Dp =
    (screenHeightDp * (if (isLandscape) HeroLandscapeFraction else HeroPortraitFraction))
        .coerceAtMost(HeroMaxHeight)

private fun heroLandscapeLogoHeight(containerHeight: Dp, maxHeight: Dp): Dp =
    minOf(maxHeight, containerHeight * HeroLandscapeLogoHeightFraction)

private fun heroPeekLogoSize(cardWidth: Dp, cardHeight: Dp, isLandscape: Boolean): DpSize =
    if (isLandscape) {
        DpSize(
            (cardWidth * HeroPeekLandscapeContentFraction - HeroPeekLandscapeContentStart) *
                HeroPeekLandscapeLogoWidthFraction,
            heroLandscapeLogoHeight(cardHeight, HeroPeekLandscapeLogoHeight),
        )
    } else {
        DpSize(cardWidth * HeroPeekLogoWidthFraction, HeroPeekLogoHeight)
    }

@Composable
fun heroCarouselLayoutHeight(): Dp {
    val screenHeightDp = windowHeightDp()
    val isLandscape = isLandscapeWindow()
    return when {
        LocalHeroStyle.current != HeroStyle.FULL_BLEED ->
            heroPeekMetrics(screenHeightDp, isLandscape).total
        isLandscape ->
            (screenHeightDp * HeroLandscapeFraction).coerceAtMost(HeroMaxHeight) - HeroBottomOverlap
        else -> (screenHeightDp * HeroPortraitFraction).coerceAtMost(HeroMaxHeight)
    }
}

@Composable
fun HeroCarousel(
    items: List<AfinityItem>,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false,
    slideSeconds: Int = HeroContentSource.DEFAULT_SLIDE_SECONDS,
    indicator: HeroIndicator = HeroIndicator.DOTS,
) {
    if (items.isEmpty()) return

    val isLandscape = isLandscapeWindow()
    val heroStyle = LocalHeroStyle.current
    val isCard = heroStyle != HeroStyle.FULL_BLEED
    val peek = heroStyle == HeroStyle.PEEK

    val density = LocalDensity.current
    val screenWidthDp = windowWidthDp()
    val screenHeightDp = windowHeightDp()

    val infinitePageCount = Int.MAX_VALUE
    val middleStart = infinitePageCount / 2
    val adjustedStart = middleStart - (middleStart % items.size)

    val pagerState =
        rememberPagerState(initialPage = adjustedStart, pageCount = { infinitePageCount })

    val cardWidth = screenWidthDp - heroCardSidePadding(isLandscape, peek) * 2
    val peekMetrics = if (isCard) heroPeekMetrics(screenHeightDp, isLandscape) else null
    val logoSize =
        if (peekMetrics != null) {
            heroPeekLogoSize(cardWidth, peekMetrics.cardHeight, isLandscape)
        } else {
            val fullBleedHeight = heroFullBleedHeight(screenHeightDp, isLandscape)
            DpSize(
                screenWidthDp *
                    (if (isLandscape) HeroLogoLandscapeWidthFraction
                    else HeroLogoPortraitWidthFraction),
                minOf(HeroLogoHeight, fullBleedHeight * 0.25f),
            )
        }

    HeroCarouselAutoScrollAndPrefetch(
        pagerState = pagerState,
        items = items,
        isScrolling = isScrolling,
        slideSeconds = slideSeconds,
        logoWidthPx = with(density) { logoSize.width.toPx().toInt() },
        logoHeightPx = with(density) { logoSize.height.toPx().toInt() },
    )

    CompositionLocalProvider(LocalHeroIndicator provides indicator) {
        if (peekMetrics != null) {
            HeroCardCarousel(
                pagerState = pagerState,
                items = items,
                metrics = peekMetrics,
                isLandscape = isLandscape,
                peek = peek,
                cardWidth = cardWidth,
                logoSize = logoSize,
                onWatchNowClick = onWatchNowClick,
                onPlayTrailerClick = onPlayTrailerClick,
                onMoreInformationClick = onMoreInformationClick,
                modifier = modifier,
            )
        } else {
            HeroFullBleed(
                pagerState = pagerState,
                items = items,
                isLandscape = isLandscape,
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                logoHeight = logoSize.height,
                onWatchNowClick = onWatchNowClick,
                onPlayTrailerClick = onPlayTrailerClick,
                onMoreInformationClick = onMoreInformationClick,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun HeroFullBleed(
    pagerState: PagerState,
    items: List<AfinityItem>,
    isLandscape: Boolean,
    screenWidthDp: Dp,
    screenHeightDp: Dp,
    logoHeight: Dp,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroHeight = heroFullBleedHeight(screenHeightDp, isLandscape)

    val currentItem by
        remember(items) { derivedStateOf { items[pagerState.currentPage % items.size] } }
    val contentAlpha by remember {
        derivedStateOf {
            1f - (kotlin.math.abs(pagerState.currentPageOffsetFraction) * 2f).coerceIn(0f, 1f)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > LIGHT_BACKGROUND_LUMINANCE
    val pageColor = colorScheme.surface
    val context = LocalContext.current
    val urlRewriter = LocalImageUrlRewriter.current
    val backdropUrls = remember(items) { items.mapNotNull(::heroBackdropUrl).distinct() }
    val backdropSamples by
        produceState(emptyMap<String, HeroBackdropSample>(), backdropUrls, urlRewriter) {
            value = value.filterKeys { it in backdropUrls }
            for (url in backdropUrls) {
                if (url in value) continue
                val rewritten = urlRewriter?.invoke(url) ?: url
                val sample =
                    loadHeroBackdropSample(context, rewritten)
                        ?: if (rewritten != url) loadHeroBackdropSample(context, url) else null
                if (sample != null) value = value + (url to sample)
            }
        }

    val toneTracker = remember(isLandscape) { HeroTextRegionTracker() }
    val fadeHeightPx = with(LocalDensity.current) { HeroFadeHeight.toPx() }
    val currentSample = heroBackdropUrl(currentItem)?.let { backdropSamples[it] }
    val textRegion = toneTracker.region
    val measuredTone =
        remember(currentSample, textRegion, isLandscape, fadeHeightPx, pageColor) {
            heroTextTone(currentSample, textRegion, isLandscape, fadeHeightPx, pageColor)
        }
    val textTone = measuredTone ?: if (isLightTheme) HeroTextTone.DARK else HeroTextTone.LIGHT

    val overlap = if (isLandscape) HeroBottomOverlap else 0.dp

    Box(
        modifier =
            modifier.bottomOverlap(overlap).fillMaxWidth().height(heroHeight).onGloballyPositioned {
                toneTracker.container = it
                toneTracker.update()
            }
    ) {
        HeroBackdrop(
            pagerState = pagerState,
            items = items,
            screenWidthDp = screenWidthDp,
            height = heroHeight,
        )

        HeroTextColors(tone = textTone, shadow = true) {
            if (isLandscape) {
                HeroContentRich(
                    currentItem = currentItem,
                    items = items,
                    pagerState = pagerState,
                    contentAlpha = contentAlpha,
                    toneTracker = toneTracker,
                    screenWidthDp = screenWidthDp,
                    logoHeight = logoHeight,
                    onWatchNowClick = onWatchNowClick,
                    onPlayTrailerClick = onPlayTrailerClick,
                    onMoreInformationClick = onMoreInformationClick,
                )
            } else {
                HeroContentCentered(
                    currentItem = currentItem,
                    items = items,
                    pagerState = pagerState,
                    contentAlpha = contentAlpha,
                    toneTracker = toneTracker,
                    screenWidthDp = screenWidthDp,
                    logoHeight = logoHeight,
                    onWatchNowClick = onWatchNowClick,
                    onPlayTrailerClick = onPlayTrailerClick,
                    onMoreInformationClick = onMoreInformationClick,
                )
            }
        }
    }
}

@Composable
private fun HeroTextColors(
    tone: HeroTextTone = HeroTextTone.LIGHT,
    shadow: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme
    val scheme =
        remember(base, tone) {
            when (tone) {
                HeroTextTone.LIGHT ->
                    base.copy(
                        onSurface = Color.White,
                        onSurfaceVariant = Color.White.copy(alpha = 0.8f),
                        surface = Color.Black,
                    )
                HeroTextTone.DARK ->
                    base.copy(
                        onSurface = HeroDarkText,
                        onSurfaceVariant = HeroDarkText.copy(alpha = 0.8f),
                    )
            }
        }
    val shadowBlur = with(LocalDensity.current) { HeroTextShadowBlur.toPx() }
    val textShadow =
        remember(tone, shadow, shadowBlur) {
            if (shadow) {
                val halo = if (tone == HeroTextTone.LIGHT) Color.Black else Color.White
                Shadow(color = halo.copy(alpha = HeroTextShadowAlpha), blurRadius = shadowBlur)
            } else {
                null
            }
        }
    CompositionLocalProvider(
        LocalHeroPageColorScheme provides base,
        LocalHeroTextShadow provides textShadow,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
@ReadOnlyComposable
private fun heroButtonColors(): Pair<Color, Color> {
    val scheme = LocalHeroPageColorScheme.current ?: MaterialTheme.colorScheme
    return if (scheme.background.luminance() > LIGHT_BACKGROUND_LUMINANCE) {
        Color.Black to Color.White
    } else {
        scheme.surface to scheme.onSurface
    }
}

@Composable
private fun HeroCarouselAutoScrollAndPrefetch(
    pagerState: PagerState,
    items: List<AfinityItem>,
    isScrolling: Boolean,
    slideSeconds: Int,
    logoWidthPx: Int,
    logoHeightPx: Int,
) {
    val context = LocalContext.current
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState.settledPage, isScrolling, isDragged, slideSeconds) {
        if (!isDragged && !isScrolling && items.size > 1) {
            if (
                !pagerState.isScrollInProgress &&
                    kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.001f
            ) {
                try {
                    pagerState.animateScrollToPage(pagerState.currentPage)
                } catch (e: Exception) {
                    Timber.w("Carousel recovery interrupted: ${e.message}")
                }
            }

            if (slideSeconds <= 0) return@LaunchedEffect

            delay(slideSeconds.seconds)

            try {
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(durationMillis = 800),
                )
            } catch (e: Exception) {
                Timber.w("Hero carousel animation interrupted: ${e.message}")
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, logoWidthPx, logoHeightPx) {
        if (items.size < 2) return@LaunchedEffect
        val current = pagerState.currentPage
        val neighbours = listOf(current + 1, current - 1).map { it.mod(items.size) }.distinct()
        for (index in neighbours) {
            val item = items[index]
            val logoUrl = heroLogoUrl(item)
            if (logoUrl != null) {
                val logoRequest =
                    ImageRequest.Builder(context)
                        .data(optimizedImageUrl(logoUrl, logoWidthPx, heightPx = logoHeightPx))
                        .size(logoWidthPx, logoHeightPx)
                        .build()
                context.imageLoader.enqueue(logoRequest)
            }
        }
    }
}

@Composable
private fun HeroBackdrop(
    pagerState: PagerState,
    items: List<AfinityItem>,
    screenWidthDp: Dp,
    height: Dp,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { page -> "hero_page_$page" },
    ) { page ->
        val actualIndex = page % items.size
        val item = items[actualIndex]
        AsyncImage(
            imageUrl = item.images.backdropImageUrl ?: item.images.primaryImageUrl,
            contentDescription = item.name,
            blurHash = item.images.backdropBlurHash ?: item.images.primaryBlurHash,
            targetWidth = screenWidthDp,
            targetHeight = height,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val fadeStart =
                            (size.height - HeroFadeHeight.toPx()).coerceAtLeast(size.height * 0.35f)
                        val gradient =
                            Brush.verticalGradient(
                                colors = HeroFadeStops,
                                startY = fadeStart,
                                endY = size.height,
                            )
                        onDrawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = HeroDimAlpha))
                            drawRect(gradient, blendMode = BlendMode.DstIn)
                        }
                    },
        )
    }
}

@Composable
private fun HeroCardCarousel(
    pagerState: PagerState,
    items: List<AfinityItem>,
    metrics: HeroPeekMetrics,
    isLandscape: Boolean,
    peek: Boolean,
    cardWidth: Dp,
    logoSize: DpSize,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = MaterialTheme.shapes.extraLarge
    val sidePadding = heroCardSidePadding(isLandscape, peek)
    val sideScale = if (peek) HeroPeekSideScale else 1f

    Box(modifier = modifier.fillMaxWidth().height(metrics.total)) {
        Box(
            modifier =
                Modifier.padding(top = metrics.top)
                    .fillMaxWidth()
                    .height(metrics.cardHeight)
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                    )
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSpacing = if (peek) HeroPeekPageSpacing else HeroInsetPageSpacing,
                beyondViewportPageCount = 1,
                key = { page -> "hero_page_$page" },
            ) { page ->
                val item = items[page % items.size]
                Box(
                    modifier =
                        Modifier.fillMaxSize().graphicsLayer {
                            val offset = pagerState.offsetFrom(page)
                            val scale =
                                1f - (1f - sideScale) * kotlin.math.abs(offset).coerceIn(0f, 1f)
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(if (offset > 0f) 1f else 0f, 0.5f)
                            shape = cardShape
                            clip = true
                        }
                ) {
                    AsyncImage(
                        imageUrl = item.images.backdropImageUrl ?: item.images.primaryImageUrl,
                        contentDescription = item.name,
                        blurHash = item.images.backdropBlurHash ?: item.images.primaryBlurHash,
                        targetWidth = cardWidth,
                        targetHeight = metrics.cardHeight,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier.fillMaxSize().drawWithContent {
                                drawContent()
                                if (isLandscape) {
                                    drawRect(HeroLandscapeSideScrim)
                                    drawRect(HeroPeekLandscapeBottomScrim)
                                } else {
                                    drawRect(HeroPeekPortraitScrim)
                                }
                            },
                    )

                    HeroTextColors {
                        val contentModifier = Modifier.graphicsLayer {
                            alpha =
                                1f -
                                    (kotlin.math.abs(pagerState.offsetFrom(page)) * 2f).coerceIn(
                                        0f,
                                        1f,
                                    )
                        }
                        val isCurrent = pagerState.currentPage == page
                        if (isLandscape) {
                            HeroPeekContentRich(
                                item = item,
                                enabled = isCurrent,
                                logoSize = logoSize,
                                modifier = contentModifier,
                                onWatchNowClick = onWatchNowClick,
                                onPlayTrailerClick = onPlayTrailerClick,
                                onMoreInformationClick = onMoreInformationClick,
                            )
                        } else {
                            HeroPeekContentCentered(
                                item = item,
                                enabled = isCurrent,
                                logoSize = logoSize,
                                modifier = contentModifier,
                                onWatchNowClick = onWatchNowClick,
                                onPlayTrailerClick = onPlayTrailerClick,
                                onMoreInformationClick = onMoreInformationClick,
                            )
                        }
                    }
                }
            }

            if (isLandscape && items.size > 1) {
                HeroTextColors {
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .padding(
                                    end = sidePadding + HeroPeekLandscapeContentStart,
                                    bottom = HeroPeekLandscapeContentBottom,
                                )
                                .height(HeroPeekLandscapeButtonSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        HeroPagerIndicator(pagerState = pagerState, size = items.size)
                    }
                }
            }
        }

        if (!isLandscape && items.size > 1) {
            Box(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(metrics.indicatorHeight),
                contentAlignment = Alignment.Center,
            ) {
                HeroPagerIndicator(pagerState = pagerState, size = items.size)
            }
        }
    }
}

@Composable
private fun BoxScope.HeroPeekContentCentered(
    item: AfinityItem,
    enabled: Boolean,
    logoSize: DpSize,
    modifier: Modifier,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
) {
    Column(
        modifier =
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroPeekLogo(item = item, logoSize = logoSize, alignStart = false)

        HeroMetadata(item)

        val genres = heroGenres(item)
        if (genres.isNotEmpty()) {
            Text(
                text = genres.take(3).joinToString(" • "),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroRoundButton(
                iconRes = R.drawable.ic_info,
                contentDescription = stringResource(R.string.hero_btn_more_info),
                onClick = { onMoreInformationClick(item) },
                enabled = enabled,
                size = HeroButtonSize,
                iconSize = HeroButtonIconSize,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                iconTint = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroRoundButton(
                    iconRes = R.drawable.ic_video,
                    contentDescription = stringResource(R.string.hero_btn_play_trailer),
                    onClick = { onPlayTrailerClick(item) },
                    enabled = enabled,
                    size = HeroButtonSize,
                    iconSize = HeroButtonIconSize,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    iconTint = MaterialTheme.colorScheme.onSurface,
                )
                HeroRoundButton(
                    iconRes = R.drawable.ic_player_play_filled,
                    contentDescription = stringResource(R.string.hero_btn_play_media),
                    onClick = { onWatchNowClick(item) },
                    enabled = enabled,
                    size = HeroButtonSize,
                    iconSize = HeroButtonPlayIconSize,
                    containerColor = MaterialTheme.colorScheme.primary,
                    iconTint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.HeroPeekContentRich(
    item: AfinityItem,
    enabled: Boolean,
    logoSize: DpSize,
    modifier: Modifier,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
) {
    Column(
        modifier =
            modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(HeroPeekLandscapeContentFraction)
                .padding(
                    start = HeroPeekLandscapeContentStart,
                    bottom = HeroPeekLandscapeContentBottom,
                ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroPeekLogo(item = item, logoSize = logoSize, alignStart = true)

            HeroMetadata(item)

            val genres = heroGenres(item)
            if (genres.isNotEmpty()) {
                Text(
                    text = genres.take(3).joinToString(" • "),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = item.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroRoundButton(
                iconRes = R.drawable.ic_info,
                contentDescription = stringResource(R.string.hero_btn_more_info),
                onClick = { onMoreInformationClick(item) },
                enabled = enabled,
                size = HeroPeekLandscapeButtonSize,
                iconSize = 22.dp,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                iconTint = MaterialTheme.colorScheme.onSurface,
            )
            HeroRoundButton(
                iconRes = R.drawable.ic_video,
                contentDescription = stringResource(R.string.hero_btn_play_trailer),
                onClick = { onPlayTrailerClick(item) },
                enabled = enabled,
                size = HeroPeekLandscapeButtonSize,
                iconSize = 22.dp,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                iconTint = MaterialTheme.colorScheme.onSurface,
            )
            HeroRoundButton(
                iconRes = R.drawable.ic_player_play_filled,
                contentDescription = stringResource(R.string.hero_btn_watch_now),
                onClick = { onWatchNowClick(item) },
                enabled = enabled,
                size = HeroPeekLandscapeButtonSize,
                iconSize = 22.dp,
                containerColor = MaterialTheme.colorScheme.primary,
                iconTint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun HeroPeekLogo(item: AfinityItem, logoSize: DpSize, alignStart: Boolean) {
    val alignment = if (alignStart) Alignment.CenterStart else Alignment.Center

    Box(
        modifier = Modifier.width(logoSize.width).height(logoSize.height),
        contentAlignment = alignment,
    ) {
        if (item.images.logo != null) {
            AsyncImage(
                imageUrl = heroLogoUrl(item),
                contentDescription = item.name,
                blurHash = null,
                targetWidth = logoSize.width,
                targetHeight = logoSize.height,
                modifier =
                    Modifier.fillMaxHeight()
                        .wrapContentWidth(
                            if (alignStart) Alignment.Start else Alignment.CenterHorizontally
                        ),
                contentScale = ContentScale.Fit,
                alignment = alignment,
            )
        } else {
            Text(
                text = item.name,
                style =
                    (if (alignStart) MaterialTheme.typography.headlineLarge
                        else MaterialTheme.typography.displaySmall)
                        .copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeroRoundButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    size: Dp,
    iconSize: Dp,
    containerColor: Color,
    iconTint: Color,
) {
    val scale = heroButtonScale(size)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size * scale).background(containerColor, CircleShape),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize * scale),
        )
    }
}

@Composable
@ReadOnlyComposable
private fun heroButtonScale(baseSize: Dp): Float =
    CardDimensions.fontScaled(baseSize)
        .coerceIn(minOf(baseSize, HeroButtonMinSize), HeroButtonMaxSize) / baseSize

@Composable
private fun HeroPagerIndicator(pagerState: PagerState, size: Int, modifier: Modifier = Modifier) {
    val colorScheme = LocalHeroPageColorScheme.current ?: MaterialTheme.colorScheme
    when (LocalHeroIndicator.current) {
        HeroIndicator.DOTS ->
            WormPagerIndicator(
                pageCount = size,
                pageFractionProvider = {
                    pagerState.currentPage % size + pagerState.currentPageOffsetFraction
                },
                activeColor = colorScheme.primary,
                inactiveColor = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = modifier,
            )
        HeroIndicator.COUNTER ->
            Text(
                text =
                    stringResource(
                        R.string.hero_indicator_counter_fmt,
                        pagerState.currentPage % size + 1,
                        size,
                    ),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = colorScheme.onSurfaceVariant,
                modifier = modifier,
            )
        HeroIndicator.HIDDEN -> Unit
    }
}

@Composable
private fun BoxScope.HeroContentCentered(
    currentItem: AfinityItem,
    items: List<AfinityItem>,
    pagerState: PagerState,
    contentAlpha: Float,
    toneTracker: HeroTextRegionTracker,
    screenWidthDp: Dp,
    logoHeight: Dp,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
) {
    val buttonScale = heroButtonScale(HeroButtonSize)
    val (buttonContainer, buttonContent) = heroButtonColors()
    val bottomPad = if (logoHeight < 120.dp) 64.dp else 80.dp
    val textShadow = LocalHeroTextShadow.current

    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = bottomPad)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = contentAlpha }
                    .onGloballyPositioned {
                        toneTracker.column = it
                        toneTracker.update()
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth(HeroLogoPortraitWidthFraction)
                        .height(logoHeight)
                        .onGloballyPositioned {
                            toneTracker.logo = it
                            toneTracker.update()
                        },
                contentAlignment = Alignment.Center,
            ) {
                currentItem.images.logo?.let { _ ->
                    AsyncImage(
                        imageUrl = heroLogoUrl(currentItem),
                        contentDescription = currentItem.name,
                        blurHash = null,
                        targetWidth = screenWidthDp * HeroLogoPortraitWidthFraction,
                        targetHeight = logoHeight,
                        modifier =
                            Modifier.fillMaxHeight().wrapContentWidth(Alignment.CenterHorizontally),
                        contentScale = ContentScale.Fit,
                    )
                }
                    ?: run {
                        Text(
                            text = currentItem.name,
                            style =
                                (if (logoHeight < 110.dp) MaterialTheme.typography.headlineLarge
                                    else MaterialTheme.typography.displaySmall)
                                    .copy(fontWeight = FontWeight.Bold, shadow = textShadow),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
            }

            HeroMetadata(currentItem)

            val genres = heroGenres(currentItem)
            if (genres.isNotEmpty()) {
                Text(
                    text = genres.take(3).joinToString(" • "),
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            shadow = textShadow,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Box(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        IconButton(
            onClick = { onMoreInformationClick(currentItem) },
            modifier =
                Modifier.align(Alignment.CenterStart)
                    .size(HeroButtonSize * buttonScale)
                    .background(buttonContainer.copy(alpha = 0.8f), CircleShape),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_info),
                contentDescription = stringResource(R.string.hero_btn_more_info),
                tint = buttonContent,
                modifier = Modifier.size(HeroButtonIconSize * buttonScale),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            IconButton(
                onClick = { onPlayTrailerClick(currentItem) },
                modifier =
                    Modifier.size(HeroButtonSize * buttonScale)
                        .background(buttonContainer.copy(alpha = 0.8f), CircleShape),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_video),
                    contentDescription = stringResource(R.string.hero_btn_play_trailer),
                    tint = buttonContent,
                    modifier = Modifier.size(HeroButtonIconSize * buttonScale),
                )
            }
            IconButton(
                onClick = { onWatchNowClick(currentItem) },
                modifier =
                    Modifier.size(HeroButtonSize * buttonScale)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_play_filled),
                    contentDescription = stringResource(R.string.hero_btn_play_media),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(HeroButtonPlayIconSize * buttonScale),
                )
            }
        }
    }

    if (items.size > 1) {
        HeroPagerIndicator(
            pagerState = pagerState,
            size = items.size,
            modifier = Modifier.align(Alignment.BottomCenter).padding(40.dp),
        )
    }
}

@Composable
private fun HeroContentRich(
    currentItem: AfinityItem,
    items: List<AfinityItem>,
    pagerState: PagerState,
    contentAlpha: Float,
    toneTracker: HeroTextRegionTracker,
    screenWidthDp: Dp,
    logoHeight: Dp,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
) {
    val buttonScale = heroButtonScale(HeroRichButtonSize)
    val (buttonContainer, buttonContent) = heroButtonColors()
    val textShadow = LocalHeroTextShadow.current

    BoxWithConstraints(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(start = 14.dp, end = 48.dp)
    ) {
        val availableHeight = maxHeight
        val isCompactHeight = availableHeight < 380.dp
        val verticalPad = if (isCompactHeight) 24.dp else 48.dp
        val contentSpacing = if (isCompactHeight) 8.dp else 16.dp
        val clampedLogoHeight = if (isCompactHeight) minOf(logoHeight, 80.dp) else logoHeight

        Column(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(top = verticalPad, bottom = verticalPad),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            Column(
                modifier =
                    Modifier.weight(1f, fill = false)
                        .fillMaxWidth(0.75f)
                        .graphicsLayer { alpha = contentAlpha }
                        .onGloballyPositioned {
                            toneTracker.column = it
                            toneTracker.update()
                        },
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth(0.5f)
                            .heightIn(max = clampedLogoHeight)
                            .onGloballyPositioned {
                                toneTracker.logo = it
                                toneTracker.update()
                            },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        currentItem.images.logo?.let { _ ->
                            AsyncImage(
                                imageUrl = heroLogoUrl(currentItem),
                                contentDescription = currentItem.name,
                                blurHash = null,
                                targetWidth = screenWidthDp * HeroLogoLandscapeWidthFraction,
                                targetHeight = clampedLogoHeight,
                                modifier =
                                    Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                            )
                        }
                            ?: run {
                                Text(
                                    text = currentItem.name,
                                    style =
                                        MaterialTheme.typography.displayMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            shadow = textShadow,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                    }
                }

                HeroMetadata(currentItem)

                val genres = heroGenres(currentItem)
                if (genres.isNotEmpty()) {
                    Text(
                        text = genres.take(3).joinToString(" • "),
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                shadow = textShadow,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (currentItem.overview.isNotBlank()) {
                    Text(
                        text = currentItem.overview,
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isCompactHeight) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconButton(
                        onClick = { onMoreInformationClick(currentItem) },
                        modifier =
                            Modifier.size(HeroRichButtonSize * buttonScale)
                                .background(buttonContainer.copy(alpha = 0.8f), CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_info),
                            contentDescription = stringResource(R.string.hero_btn_more_info),
                            tint = buttonContent,
                            modifier = Modifier.size(HeroButtonPlayIconSize * buttonScale),
                        )
                    }
                    IconButton(
                        onClick = { onPlayTrailerClick(currentItem) },
                        modifier =
                            Modifier.size(HeroRichButtonSize * buttonScale)
                                .background(buttonContainer.copy(alpha = 0.8f), CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_video),
                            contentDescription = stringResource(R.string.hero_btn_play_trailer),
                            tint = buttonContent,
                            modifier = Modifier.size(HeroButtonPlayIconSize * buttonScale),
                        )
                    }
                    IconButton(
                        onClick = { onWatchNowClick(currentItem) },
                        modifier =
                            Modifier.size(HeroRichButtonSize * buttonScale)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_play_filled),
                            contentDescription = stringResource(R.string.hero_btn_watch_now),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(HeroButtonPlayIconSize * buttonScale),
                        )
                    }
                }

                if (items.size > 1) {
                    HeroPagerIndicator(pagerState = pagerState, size = items.size)
                }
            }
        }
    }
}

@Composable
private fun HeroMetadata(
    item: AfinityItem,
    modifier: Modifier = Modifier,
) {
    val communityRating =
        when (item) {
            is AfinityMovie -> item.communityRating
            is AfinityShow -> item.communityRating
            is AfinityEpisode -> item.communityRating
            else -> null
        }
    val ratingScale = rememberRatingMetadataScale()
    val textStyle =
        MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            shadow = LocalHeroTextShadow.current,
        )

    val items =
        buildList<@Composable () -> Unit> {
            if (LocalShowRatings.current) {
                communityRating?.let { rating ->
                    add {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_community_rating),
                                contentDescription = stringResource(R.string.cd_imdb),
                                tint = Color.Unspecified,
                                modifier = Modifier.size(ratingScale.rtIconSize),
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", rating),
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (item is AfinityMovie) {
                    item.criticRating?.let { rtRating ->
                        add {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            id =
                                                if (rtRating > 60) R.drawable.ic_rotten_tomato_fresh
                                                else R.drawable.ic_rotten_tomato_rotten
                                        ),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(ratingScale.rtIconSize),
                                )
                                Text(
                                    text = "${rtRating.toInt()}%",
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (item is AfinityMovie) {
                item.premiereDate?.let { date ->
                    add {
                        Text(
                            text = date.year.toString(),
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val officialRating =
                when (item) {
                    is AfinityMovie -> item.officialRating
                    is AfinityShow -> item.officialRating
                    else -> null
                }

            officialRating?.let { rating ->
                add {
                    Text(
                        text = rating,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier.background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            if (item is AfinityShow) {
                item.seasonCount?.let { count ->
                    add {
                        Text(
                            text = pluralStringResource(R.plurals.hero_season_plural, count, count),
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

    if (items.isNotEmpty()) {
        MetadataOverflowRow(
            items = items,
            spacing = 12.dp,
            showSeparators = false,
            modifier = modifier,
        )
    }
}
