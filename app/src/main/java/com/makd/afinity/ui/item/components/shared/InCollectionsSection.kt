package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun InCollectionsSection(
    boxSets: List<AfinityBoxSet>,
    onBoxSetClick: (AfinityBoxSet) -> Unit,
    widthSizeClass: WindowWidthSizeClass
) {
    if (boxSets.isEmpty()) return

    val cardWidth = widthSizeClass.portraitWidth

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.collections_included_in),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(boxSets) { boxSet ->
                MediaItemCard(
                    item = boxSet,
                    onClick = { onBoxSetClick(boxSet) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}