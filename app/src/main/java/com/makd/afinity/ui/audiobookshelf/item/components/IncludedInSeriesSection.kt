package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.audiobookshelf.item.SeriesDisplayData

@Composable
fun IncludedInSeriesSection(
    seriesList: List<SeriesDisplayData>,
    serverUrl: String?,
    onSeriesClick: (seriesId: String, seriesName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (seriesList.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "Included in Series",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        seriesList.forEach { series ->
            val coverUrls =
                if (serverUrl != null) {
                    series.bookItems.take(4).map { "$serverUrl/api/items/${it.id}/cover" }
                } else {
                    emptyList()
                }

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable { onSeriesClick(series.id, series.name) }
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeriesCoverGrid(coverUrls = coverUrls, modifier = Modifier.size(130.dp))

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = series.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${series.totalBooks} books",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = "View series",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
