package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun MediaRowSection(
    title: String,
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
    onViewAllClick: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return

    val aspectRatio =
        if (items.firstOrNull() is AfinityEpisode) CardDimensions.ASPECT_RATIO_LANDSCAPE
        else CardDimensions.ASPECT_RATIO_PORTRAIT
    val fixedRowHeight = CardDimensions.rowHeight(cardWidth, aspectRatio)

    Column(
        modifier = modifier.padding(horizontal = CardDimensions.RowGutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionRowHeader(title = title, onViewAllClick = onViewAllClick)

        CardRow(modifier = Modifier.height(fixedRowHeight)) {
            items(items, key = { it.id }) { item ->
                if (item is AfinityEpisode) {
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
                    )
                }
            }
        }
    }
}
