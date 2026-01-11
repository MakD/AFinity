package com.makd.afinity.ui.item.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun BoxSetDetailContent(
    item: AfinityBoxSet,
    boxSetItems: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass
) {
    val cardWidth = widthSizeClass.portraitWidth

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TaglineSection(item = item)

        OverviewSection(item = item)

        DirectorSection(item = item)

        WriterSection(item = item)

        ExternalLinksSection(item = item)

        BoxSetItemsSection(
            items = boxSetItems,
            onItemClick = onItemClick,
            cardWidth = cardWidth
        )
    }
}

@Composable
internal fun BoxSetItemsSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    cardWidth: Dp
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Items in Collection",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(items) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}