package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.preview.FontScalePreviews
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.preview.WidthPreviews

@Composable
fun DetailActionRow(
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onShuffle: (() -> Unit)? = null,
    playbackEnabled: Boolean = true,
    leadingActions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = leadingActions,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 8.dp),
        ) {
            if (onShuffle != null) {
                IconButton(onClick = onShuffle, enabled = playbackEnabled) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrows_shuffle),
                        contentDescription = stringResource(R.string.cd_music_shuffle),
                        tint =
                            if (playbackEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            FloatingActionButton(
                onClick = { if (playbackEnabled) onPlay() },
                containerColor =
                    if (playbackEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor =
                    if (playbackEnabled) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_play_filled),
                    contentDescription = stringResource(R.string.cd_play),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun PreviewActionIcon(iconRes: Int) {
    IconButton(onClick = {}) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun DetailActionRowPreviewContent() {
    PreviewHost {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 4, 3).forEach { count ->
                DetailActionRow(
                    onShuffle = {},
                    onPlay = {},
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    listOf(
                            R.drawable.ic_favorite,
                            R.drawable.ic_compass,
                            R.drawable.ic_download,
                            R.drawable.ic_delete,
                            R.drawable.ic_edit,
                        )
                        .take(count)
                        .forEach { PreviewActionIcon(it) }
                }
            }
        }
    }
}

@WidthPreviews
@Composable
private fun DetailActionRowWidthPreview() {
    DetailActionRowPreviewContent()
}

@FontScalePreviews
@Composable
private fun DetailActionRowFontScalePreview() {
    DetailActionRowPreviewContent()
}
