package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.ui.components.MediaItemCard

@Composable
fun InCollectionsSection(
    boxSets: List<AfinityBoxSet>,
    onBoxSetClick: (AfinityBoxSet) -> Unit
) {
    if (boxSets.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Included In",
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
                    onClick = { onBoxSetClick(boxSet) }
                )
            }
        }
    }
}
