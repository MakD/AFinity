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
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.components.windowHeightDp
import com.makd.afinity.ui.components.windowWidthDp
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.utils.shimmerEffect

private const val SPOTLIGHT_PLACEHOLDER_COUNT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingSection(
    title: String,
    cardStyle: CustomSectionCardStyle,
    onVisible: () -> Unit,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
) {
    DisposableEffect(Unit) {
        onVisible()
        onDispose { onHidden() }
    }

    if (cardStyle == CustomSectionCardStyle.SPOTLIGHT) {
        val isLandscape = isLandscapeWindow()
        val containerWidth = windowWidthDp()
        val containerHeight = windowHeightDp()
        Column(modifier = modifier) {
            HomeSectionHeader(title = title, startPadding = CardDimensions.RowGutter)
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(horizontal = CardDimensions.RowGutter)
            ) {
                val itemSize =
                    CardDimensions.carouselItemSize(
                        availableWidth = maxWidth,
                        windowHeight = containerHeight,
                        isLandscape = isLandscape,
                        widthFraction = CardDimensions.spotlightWidthFraction(isLandscape),
                        aspectRatio = CardDimensions.spotlightAspectRatio(isLandscape),
                        maxHeight = CardDimensions.spotlightMaxHeight(containerWidth),
                    )

                HorizontalMultiBrowseCarousel(
                    state = rememberCarouselState { SPOTLIGHT_PLACEHOLDER_COUNT },
                    preferredItemWidth = itemSize.width,
                    modifier = Modifier.height(itemSize.height),
                    itemSpacing = 12.dp,
                    maxSmallItemWidth = CardDimensions.spotlightMaxSmallItemWidth,
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
        }
    } else {
        RowSkeleton(
            title = title,
            cardWidth = CardDimensions.cardWidthFor(cardStyle),
            aspectRatio = CardDimensions.aspectRatioFor(cardStyle),
            showText = showText,
            modifier = modifier,
        )
    }
}

@Composable
fun RowSkeleton(
    title: String,
    cardWidth: Dp,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
) {
    Column(modifier = modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = title)

        MediaRowSkeleton(
            cardWidth = cardWidth,
            height =
                if (showText) CardDimensions.rowHeight(cardWidth, aspectRatio)
                else
                    CardDimensions.rowHeight(
                        cardWidth,
                        aspectRatio,
                        titleHeight = 0.dp,
                        metadataHeight = 0.dp,
                    ),
            aspectRatio = aspectRatio,
            showText = showText,
        )
    }
}
