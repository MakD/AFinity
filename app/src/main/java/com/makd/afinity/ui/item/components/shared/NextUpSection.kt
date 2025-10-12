package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.ui.components.ContinueWatchingCard

@Composable
fun NextUpSection(
    episode: AfinityEpisode,
    onEpisodeClick: (AfinityEpisode) -> Unit
) {
    val isInProgress = episode.playbackPositionTicks > 0 && !episode.played
    val sectionTitle = if (isInProgress) "Continue Watching" else "Next Up"

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = sectionTitle,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        ContinueWatchingCard(
            item = episode,
            onClick = { onEpisodeClick(episode) }
        )
    }
}