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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.logoBlurHash
import com.makd.afinity.data.models.extensions.logoImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.OptimizedAsyncImage
import kotlinx.coroutines.delay
import mx.platacard.pagerindicator.PagerIndicatorOrientation
import mx.platacard.pagerindicator.PagerWormIndicator
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter


@Composable
fun OptimizedHeroCarousel(
    items: List<AfinityItem>,
    height: Dp,
    onWatchNowClick: (AfinityItem) -> Unit,
    onPlayTrailerClick: (AfinityItem) -> Unit,
    onMoreInformationClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        HeroCarouselLandscape(
            items = items,
            height = height,
            onWatchNowClick = onWatchNowClick,
            onPlayTrailerClick = onPlayTrailerClick,
            onMoreInformationClick = onMoreInformationClick,
            modifier = modifier,
            isScrolling = isScrolling
        )
    } else {
        HeroCarouselPortrait(
            items = items,
            height = height,
            onWatchNowClick = onWatchNowClick,
            onPlayTrailerClick = onPlayTrailerClick,
            onMoreInformationClick = onMoreInformationClick,
            modifier = modifier,
            isScrolling = isScrolling
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
    isScrolling: Boolean = false
) {
    if (items.isEmpty()) return

    val infinitePageCount = Int.MAX_VALUE
    val middleStart = infinitePageCount / 2
    val adjustedStart = middleStart - (middleStart % items.size)
    val pagerState = rememberPagerState(
        initialPage = adjustedStart,
        pageCount = { infinitePageCount }
    )

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

    val currentItem by remember {
        derivedStateOf { items[pagerState.currentPage % items.size] }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
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
                            colors = listOf(
                                Color.Black,
                                Color.Transparent
                            ),
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        item.images.logo?.let { logoUri ->
                            OptimizedAsyncImage(
                                imageUrl = item.images.logoImageUrl,
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
                val item = currentItem
                Spacer(modifier = Modifier.weight(1f))
                        when (item) {
                            is AfinityMovie -> {
                                item.communityRating?.let { rating ->
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
                                            text = String.format("%.1f", rating),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            is AfinityShow -> {
                                item.communityRating?.let { rating ->
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
                                            text = String.format("%.1f", rating),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            is AfinityEpisode -> {
                                item.communityRating?.let { rating ->
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
                                            text = String.format("%.1f", rating),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        when (item) {
                            is AfinityMovie -> {
                                item.criticRating?.let { rtRating ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                id = if (rtRating > 60) {
                                                    R.drawable.ic_rotten_tomato_fresh
                                                } else {
                                                    R.drawable.ic_rotten_tomato_rotten
                                                }
                                            ),
                                            contentDescription = if (rtRating > 60) "Fresh" else "Rotten",
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "${rtRating.toInt()}%",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        when (item) {
                            is AfinityMovie -> {
                                item.premiereDate?.let { date ->
                                    Text(
                                        text = date.year.toString(),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        when (item) {
                            is AfinityMovie -> {
                                item.officialRating?.let { rating ->
                                    Text(
                                        text = rating,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .background(
                                                Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            is AfinityShow -> {
                                item.officialRating?.let { rating ->
                                    Text(
                                        text = rating,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .background(
                                                Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        when (item) {
                            is AfinityMovie -> {
                                if (currentItem.runtimeTicks > 0) {
                                    val runtimeMillis = currentItem.runtimeTicks / 10_000L
                                    val endTime = LocalTime.now().plusNanos(runtimeMillis * 1_000_000L)
                                    val formatter = DateTimeFormatter.ofPattern("HH:mm")
                                    Text(
                                        text = "Ends at ${endTime.format(formatter)}",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is AfinityShow -> {
                                item.seasonCount?.let { count ->
                                    Text(
                                        text = if (count == 1) "1 Season" else "$count Seasons",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } ?: run {
                                    Text(
                                        text = "TV Series",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .fillMaxWidth()
            ) {
                val item = currentItem
                when (item) {
                    is AfinityMovie -> {
                        if (item.genres.isNotEmpty()) {
                            Text(
                                text = item.genres.take(3).joinToString(" • "),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    is AfinityShow -> {
                        if (item.genres.isNotEmpty()) {
                            Text(
                                text = item.genres.take(3).joinToString(" • "),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
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
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
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
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Play Trailer",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { onWatchNowClick(currentItem) },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Media",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        if (items.size > 1) {
            val currentPageFraction by remember {
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
                currentPageFraction = remember { derivedStateOf { currentPageFraction } },
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
    isScrolling: Boolean = false
) {
    if (items.isEmpty()) return

    val infinitePageCount = Int.MAX_VALUE
    val middleStart = infinitePageCount / 2
    val adjustedStart = middleStart - (middleStart % items.size)
    val pagerState = rememberPagerState(
        initialPage = adjustedStart,
        pageCount = { infinitePageCount }
    )

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

    val currentItem by remember {
        derivedStateOf { items[pagerState.currentPage % items.size] }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(landscapeHeight)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
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
                            colors = listOf(
                                Color.Black,
                                Color.Transparent
                            ),
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
                .padding(start = 48.dp, bottom = 48.dp, top = 48.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.45f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                    imageUrl = item.images.logoImageUrl,
                                    contentDescription = "${item.name} logo",
                                    blurHash = item.images.logoBlurHash,
                                    targetWidth = (LocalConfiguration.current.screenWidthDp * 0.4f).dp,
                                    targetHeight = 100.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentScale = ContentScale.Fit
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
                        .height(28.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val item = currentItem
                        when (item) {
                            is AfinityMovie -> {
                                item.communityRating?.let { rating ->
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
                                            text = String.format("%.1f", rating),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                item.criticRating?.let { rtRating ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                id = if (rtRating > 60) {
                                                    R.drawable.ic_rotten_tomato_fresh
                                                } else {
                                                    R.drawable.ic_rotten_tomato_rotten
                                                }
                                            ),
                                            contentDescription = if (rtRating > 60) "Fresh" else "Rotten",
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "${rtRating.toInt()}%",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                (currentItem as AfinityMovie).premiereDate?.let { date ->
                                    Text(
                                        text = date.year.toString(),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                item.officialRating?.let { rating ->
                                    Text(
                                        text = rating,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .background(
                                                Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (currentItem.runtimeTicks > 0) {
                                    val runtimeMillis = currentItem.runtimeTicks / 10_000L
                                    val endTime =
                                        LocalTime.now().plusNanos(runtimeMillis * 1_000_000L)
                                    val formatter = DateTimeFormatter.ofPattern("HH:mm")
                                    Text(
                                        text = "Ends at ${endTime.format(formatter)}",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            is AfinityShow -> {
                                val show = currentItem as AfinityShow

                                item.communityRating?.let { rating ->
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
                                            text = String.format("%.1f", rating),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                item.officialRating?.let { rating ->
                                    Text(
                                        text = rating,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .background(
                                                Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                show.seasonCount?.let { count ->
                                    Text(
                                        text = if (count == 1) "1 Season" else "$count Seasons",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } ?: run {
                                    Text(
                                        text = "TV Series",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    when (currentItem) {
                        is AfinityMovie -> {
                            val movie = currentItem as AfinityMovie
                            if (movie.genres.isNotEmpty()) {
                                Text(
                                    text = movie.genres.take(3).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is AfinityShow -> {
                            val show = currentItem as AfinityShow
                            if (show.genres.isNotEmpty()) {
                                Text(
                                    text = show.genres.take(3).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentAlignment = Alignment.TopStart
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

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Move buttons and dots OUTSIDE the Column, as direct children of the parent Box
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 48.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = { onMoreInformationClick(currentItem) },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "More Information",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { onPlayTrailerClick(currentItem) },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Play Trailer",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { onWatchNowClick(currentItem) },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Watch Now",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (items.size > 1) {
                val currentPageFraction by remember {
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
                    currentPageFraction = remember { derivedStateOf { currentPageFraction } },
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