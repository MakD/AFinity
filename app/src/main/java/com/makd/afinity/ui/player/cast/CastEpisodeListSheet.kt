package com.makd.afinity.ui.player.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastEpisodeListSheet(
    episodes: List<AfinityItem>,
    currentItemId: UUID?,
    onEpisodeClick: (AfinityItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.cd_episodes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn {
                items(episodes) { episode ->
                    val isCurrentItem = episode.id == currentItemId
                    val episodeInfo = episode as? AfinityEpisode

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onEpisodeClick(episode)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val titleText = buildString {
                                if (episodeInfo != null) {
                                    val num = episodeInfo.indexNumber
                                    if (num != null) {
                                        append("E${num.toString().padStart(2, '0')} - ")
                                    }
                                }
                                append(episode.name)
                            }

                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrentItem) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrentItem) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (episode.runtimeTicks > 0) {
                                val runtimeMinutes = (episode.runtimeTicks / 10000 / 60000).toInt()
                                Text(
                                    text = "${runtimeMinutes} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }

                        if (isCurrentItem) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.cast_now_playing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}