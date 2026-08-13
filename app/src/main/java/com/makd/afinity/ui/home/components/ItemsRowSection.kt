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
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.theme.CardDimensions

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
    val cardWidth = CardDimensions.cardWidthFor(cardStyle, widthSizeClass)
    val aspectRatio = CardDimensions.aspectRatioFor(cardStyle)
    val fixedRowHeight = CardDimensions.rowHeight(cardWidth, aspectRatio)

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
