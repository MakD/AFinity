package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.ui.components.ContinueWatchingCard

@Composable
fun NextUpSection(
    episodes: List<AfinityEpisode>,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val cardWidth = (configuration.screenWidthDp.dp - 28.dp - 12.dp) / 2f
    val fixedRowHeight = (cardWidth / (16f / 9f)) + 8.dp + 20.dp + 22.dp
    Column(
        modifier = Modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = "Next Up",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = episodes,
                key = { episode -> "nextup_${episode.id}" }
            ) { episode ->
                ContinueWatchingCard(
                    item = episode,
                    onClick = { onEpisodeClick(episode) }
                )
            }
        }
    }
}