package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.hasCardMetadata
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import com.makd.afinity.ui.theme.CardDimensions.squareWidth

@Composable
fun ItemsRowSection(
    title: String,
    items: List<AfinityItem>,
    sectionKey: String,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    cardStyle: CustomSectionCardStyle = CustomSectionCardStyle.PORTRAIT,
    onViewAllClick: (() -> Unit)? = null,
) {
    val isLandscape = cardStyle == CustomSectionCardStyle.LANDSCAPE
    val isSquare = cardStyle == CustomSectionCardStyle.SQUARE
    val cardWidth =
        when {
            isLandscape -> widthSizeClass.landscapeWidth
            isSquare -> widthSizeClass.squareWidth
            else -> widthSizeClass.portraitWidth
        }
    val aspectRatio =
        when {
            isLandscape -> CardDimensions.ASPECT_RATIO_LANDSCAPE
            isSquare -> CardDimensions.ASPECT_RATIO_SQUARE
            else -> CardDimensions.ASPECT_RATIO_PORTRAIT
        }
    val cardHeight = CardDimensions.calculateHeight(cardWidth, aspectRatio)
    val showRatings = LocalShowRatings.current
    val hasMetadataLine =
        isLandscape || items.any { it.hasCardMetadata(showRatings) }
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + if (hasMetadataLine) 22.dp else 0.dp

    Column(modifier = modifier.padding(horizontal = 14.dp)) {
        HomeSectionHeader(title = title, onViewAllClick = onViewAllClick)

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = items, key = { item -> "${sectionKey}_${item.id}" }) { item ->
                if (isLandscape) {
                    ContinueWatchingCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        cardWidth = cardWidth,
                    )
                } else {
                    MediaItemCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        cardWidth = cardWidth,
                        aspectRatio = aspectRatio,
                    )
                }
            }
        }
    }
}