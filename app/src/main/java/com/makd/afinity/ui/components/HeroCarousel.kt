package com.makd.afinity.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.imageLoader
import coil3.request.ImageRequest
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.logoBlurHash
import com.makd.afinity.data.models.extensions.logoImageUrlWithTransparency
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import kotlinx.coroutines.delay
import mx.platacard.pagerindicator.PagerIndicatorOrientation
import mx.platacard.pagerindicator.PagerWormIndicator
import timber.log.Timber
import java.util.Locale

@Composable
fun HeroCarousel(
    items: List<AfinityItem>,
    height: Dp,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val infinitePageCount = Int.MAX_VALUE
    val middleStart = infinitePageCount / 2
    val adjustedStart = middleStart - (middleStart % items.size)
    val currentPageIndex = rememberSaveable(items.size) { mutableStateOf(adjustedStart) }

    if (isLandscape) {
        HeroCarouselLandscape(
            items = items,
            height = height,
            onWatchNowClick = onWatchNowClick,
            onPlayTrailerClick = onPlayTrailerClick,
            onMoreInformationClick = onMoreInformationClick,
            modifier = modifier,
            isScrolling = isScrolling,
            initialPageIndex = currentPageIndex.value,
            onPageChanged = { currentPageIndex.value = it }
        )
    } else {
        HeroCarouselPortrait(
            items = items,
            height = height,
            onWatchNowClick = onWatchNowClick,
            onPlayTrailerClick = onPlayTrailerClick,
            onMoreInformationClick = onMoreInformationClick,
            modifier = modifier,
            isScrolling = isScrolling,
            initialPageIndex = currentPageIndex.value,
            onPageChanged = { currentPageIndex.value = it }
        )
    }
}

@Composable
fun HeroCarouselPortrait(
    items: List<AfinityItem>,
    height: Dp,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false,
    initialPageIndex: Int,
    onPageChanged: (Int) -> Unit
) {
    if (items.isEmpty()) return
    val infinitePageCount = Int.MAX_VALUE
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { infinitePageCount }
    )

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    val context = LocalContext.current
    LaunchedEffect(items.size, isScrolling, pagerState.settledPage) {
        if (items.size > 1) {
            while (!isScrolling) {
                delay(5000)
                if (!isScrolling) {
                    try {
                        pagerState.animateScrollToPage(
                            page = pagerState.currentPage + 1,
                            animationSpec = tween(durationMillis = 800)
                        )
                    } catch (e: Exception) {
                        Timber.w("Hero carousel animation interrupted: ${e.message}")
                    }
                }
            }
        }
    }

    val currentItem by remember { derivedStateOf { items[pagerState.currentPage % items.size] } }

    LaunchedEffect(pagerState.currentPage) {
        val currentIndex = pagerState.currentPage % items.size
        val nextIndex = (currentIndex + 1) % items.size
        val prevIndex = (currentIndex - 1 + items.size) % items.size
        listOf(nextIndex, prevIndex).forEach { index ->
            val item = items[index]
            val imageUrl = item.images.backdropImageUrl ?: item.images.primaryImageUrl
            if (imageUrl != null) {
                val request = ImageRequest.Builder(context).data(imageUrl).build()
                context.imageLoader.enqueue(request)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { page -> "hero_page_${page % items.size}" }
        ) { page ->
            val actualIndex = page % items.size
            val item = items[actualIndex]
            OptimizedAsyncImage(
                imageUrl = item.images.backdropImageUrl ?: item.images.primaryImageUrl,
                contentDescription = item.name,
                blurHash = item.images.backdropBlurHash ?: item.images.primaryBlurHash,
                targetWidth = LocalConfiguration.current.screenWidthDp.dp,
                targetHeight = height,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val gradient = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = size.height * 0.75f,
                            endY = size.height
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = 0.3f))
                            drawRect(gradient, blendMode = BlendMode.DstIn)
                        }
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.8f)
                    .padding(bottom = 144.dp)
                    .sizeIn(maxHeight = 120.dp)
            ) {
                Crossfade(
                    targetState = currentItem,
                    animationSpec = tween(durationMillis = 700),
                    label = "logo_crossfade"
                ) { item ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        item.images.logo?.let { logoUri ->
                            OptimizedAsyncImage(
                                imageUrl = item.images.logoImageUrlWithTransparency,
                                contentDescription = "${item.name} logo",
                                blurHash = item.images.logoBlurHash,
                                targetWidth = (LocalConfiguration.current.screenWidthDp * 0.8f).dp,
                                targetHeight = 120.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                contentScale = ContentScale.Fit
                            )
                        } ?: run {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 36.sp
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 112.dp)
                    .fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                HeroMetadata(currentItem)
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .fillMaxWidth()
            ) {
                val genres = when (currentItem) {
                    is AfinityMovie -> (currentItem as AfinityMovie).genres
                    is AfinityShow -> (currentItem as AfinityShow).genres
                    else -> emptyList()
                }
                if (genres.isNotEmpty()) {
                    Text(
                        text = genres.take(3).joinToString(" • "),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            IconButton(
                onClick = { onMoreInformationClick(currentItem) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_info),
                    contentDescription = "More Information",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                IconButton(
                    onClick = { onPlayTrailerClick(currentItem) },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_video),
                        contentDescription = "Play Trailer",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = { onWatchNowClick(currentItem) },
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_arrow),
                        contentDescription = "Play Media",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        if (items.size > 1) {
            val currentPageFractionState = remember {
                derivedStateOf {
                    val currentPage = pagerState.currentPage % items.size
                    val pageOffset = pagerState.currentPageOffsetFraction
                    when {
                        pageOffset > 0.5f && currentPage == items.size - 1 -> 0f
                        pageOffset < -0.5f && currentPage == 0 -> (items.size - 1).toFloat()
                        else -> (currentPage + pageOffset).coerceIn(0f, (items.size - 1).toFloat())
                    }
                }
            }

            PagerWormIndicator(
                pageCount = items.size,
                currentPageFraction = currentPageFractionState,
                activeDotColor = MaterialTheme.colorScheme.primary,
                dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(40.dp),
                dotCount = 5,
                activeDotSize = 8.dp,
                minDotSize = 4.dp,
                space = 5.dp,
                orientation = PagerIndicatorOrientation.Horizontal
            )
        }
    }
}

@Composable
private fun HeroCarouselLandscape(
    items: List<AfinityItem>,
    height: Dp,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false,
    initialPageIndex: Int,
    onPageChanged: (Int) -> Unit
) {
    if (items.isEmpty()) return
    val infinitePageCount = Int.MAX_VALUE
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { infinitePageCount }
    )

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    val configuration = LocalConfiguration.current
    val landscapeHeight = (configuration.screenHeightDp * 0.95f).dp

    LaunchedEffect(items.size, isScrolling, pagerState.settledPage) {
        if (items.size > 1) {
            while (!isScrolling) {
                delay(5000)
                if (!isScrolling) {
                    try {
                        pagerState.animateScrollToPage(
                            page = pagerState.currentPage + 1,
                            animationSpec = tween(durationMillis = 800)
                        )
                    } catch (e: Exception) {
                        Timber.w("Hero carousel animation interrupted: ${e.message}")
                    }
                }
            }
        }
    }

    val currentItem by remember { derivedStateOf { items[pagerState.currentPage % items.size] } }
    val context = LocalContext.current

    LaunchedEffect(pagerState.currentPage) {
        val currentIndex = pagerState.currentPage % items.size
        val nextIndex = (currentIndex + 1) % items.size
        val prevIndex = (currentIndex - 1 + items.size) % items.size
        listOf(nextIndex, prevIndex).forEach { index ->
            val item = items[index]
            val imageUrl = item.images.backdropImageUrl ?: item.images.primaryImageUrl
            if (imageUrl != null) {
                val request = ImageRequest.Builder(context).data(imageUrl).build()
                context.imageLoader.enqueue(request)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(landscapeHeight)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { page -> "hero_page_${page % items.size}" }
        ) { page ->
            val actualIndex = page % items.size
            val item = items[actualIndex]
            OptimizedAsyncImage(
                imageUrl = item.images.backdropImageUrl ?: item.images.primaryImageUrl,
                contentDescription = item.name,
                blurHash = item.images.backdropBlurHash ?: item.images.primaryBlurHash,
                targetWidth = LocalConfiguration.current.screenWidthDp.dp,
                targetHeight = landscapeHeight,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val gradient = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = size.height * 0.75f,
                            endY = size.height
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(Color.Black.copy(alpha = 0.3f))
                            drawRect(gradient, blendMode = BlendMode.DstIn)
                        }
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, bottom = 48.dp, top = 48.dp, end = 48.dp)
        ) {

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Column(
                    modifier = Modifier.fillMaxWidth(0.75f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(100.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Crossfade(
                            targetState = currentItem,
                            animationSpec = tween(durationMillis = 700),
                            label = "logo_crossfade"
                        ) { item ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                item.images.logo?.let { logoUri ->
                                    OptimizedAsyncImage(
                                        imageUrl = item.images.logoImageUrlWithTransparency,
                                        contentDescription = "${item.name} logo",
                                        blurHash = item.images.logoBlurHash,
                                        targetWidth = (LocalConfiguration.current.screenWidthDp * 0.4f).dp,
                                        targetHeight = 100.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight(),
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.CenterStart
                                    )
                                } ?: run {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeroMetadata(currentItem)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    ) {
                        val genres = when (currentItem) {
                            is AfinityMovie -> (currentItem as AfinityMovie).genres
                            is AfinityShow -> (currentItem as AfinityShow).genres
                            else -> emptyList()
                        }
                        if (genres.isNotEmpty()) {
                            Text(
                                text = genres.take(3).joinToString(" • "),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        currentItem.overview?.let { overview ->
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        IconButton(
                            onClick = { onMoreInformationClick(currentItem) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_info),
                                contentDescription = "More Information",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = { onPlayTrailerClick(currentItem) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_video),
                                contentDescription = "Play Trailer",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = { onWatchNowClick(currentItem) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_play_arrow),
                                contentDescription = "Watch Now",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (items.size > 1) {
                        val currentPageFractionState = remember {
                            derivedStateOf {
                                val currentPage = pagerState.currentPage % items.size
                                val pageOffset = pagerState.currentPageOffsetFraction
                                when {
                                    pageOffset > 0.5f && currentPage == items.size - 1 -> 0f
                                    pageOffset < -0.5f && currentPage == 0 -> (items.size - 1).toFloat()
                                    else -> (currentPage + pageOffset).coerceIn(
                                        0f,
                                        (items.size - 1).toFloat()
                                    )
                                }
                            }
                        }

                        PagerWormIndicator(
                            pageCount = items.size,
                            currentPageFraction = currentPageFractionState,
                            activeDotColor = MaterialTheme.colorScheme.primary,
                            dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            dotCount = 5,
                            activeDotSize = 8.dp,
                            minDotSize = 4.dp,
                            space = 5.dp,
                            orientation = PagerIndicatorOrientation.Horizontal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetadata(item: AfinityItem) {
    val communityRating = when (item) {
        is AfinityMovie -> item.communityRating
        is AfinityShow -> item.communityRating
        is AfinityEpisode -> item.communityRating
        else -> null
    }

    communityRating?.let { rating ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_imdb_logo),
                contentDescription = "IMDB",
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = String.format(Locale.US, "%.1f", rating),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (item is AfinityMovie) {
        item.criticRating?.let { rtRating ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (rtRating > 60) R.drawable.ic_rotten_tomato_fresh
                        else R.drawable.ic_rotten_tomato_rotten
                    ),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${rtRating.toInt()}%",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item.premiereDate?.let { date ->
            Text(
                text = date.year.toString(),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val officialRating = when (item) {
        is AfinityMovie -> item.officialRating
        is AfinityShow -> item.officialRating
        else -> null
    }

    officialRating?.let { rating ->
        Text(
            text = rating,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }

    if (item is AfinityShow) {
        item.seasonCount?.let { count ->
            Text(
                text = if (count == 1) "1 Season" else "$count Seasons",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}