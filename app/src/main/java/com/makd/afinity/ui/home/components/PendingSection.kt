package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.utils.shimmerEffect

private const val SPOTLIGHT_PLACEHOLDER_COUNT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingSection(
    title: String,
    cardStyle: CustomSectionCardStyle,
    onVisible: () -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    var hasBeenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasBeenVisible) {
            onVisible()
            hasBeenVisible = true
        }
    }

    Column(modifier = modifier.padding(horizontal = 14.dp)) {
        HomeSectionHeader(title = title)

        if (cardStyle == CustomSectionCardStyle.SPOTLIGHT) {
            val isLandscape = isLandscapeWindow()
            val containerSize = LocalWindowInfo.current.containerSize
            val density = LocalDensity.current
            val containerWidth = with(density) { containerSize.width.toDp() }
            val containerHeight = with(density) { containerSize.height.toDp() }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemSize =
                    CardDimensions.carouselItemSize(
                        availableWidth = maxWidth,
                        windowHeight = containerHeight,
                        isLandscape = isLandscape,
                        widthFraction = if (isLandscape) 0.58f else 0.88f,
                        aspectRatio = CardDimensions.spotlightAspectRatio(isLandscape),
                        maxHeight = CardDimensions.spotlightMaxHeight(containerWidth),
                    )

                HorizontalMultiBrowseCarousel(
                    state = rememberCarouselState { SPOTLIGHT_PLACEHOLDER_COUNT },
                    preferredItemWidth = itemSize.width,
                    modifier = Modifier.height(itemSize.height),
                    itemSpacing = 12.dp,
                    maxSmallItemWidth = CarouselDefaults.MinSmallItemSize,
                ) {
                    Box(
                        modifier =
                            Modifier.height(itemSize.height)
                                .maskClip(MaterialTheme.shapes.extraLarge)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().shimmerEffect())
                    }
                }
            }
        } else {
            val cardWidth = CardDimensions.cardWidthFor(cardStyle, widthSizeClass)
            val aspectRatio = CardDimensions.aspectRatioFor(cardStyle)

            MediaRowSkeleton(
                cardWidth = cardWidth,
                height = CardDimensions.rowHeight(cardWidth, aspectRatio),
                aspectRatio = aspectRatio,
            )
        }
    }
}
