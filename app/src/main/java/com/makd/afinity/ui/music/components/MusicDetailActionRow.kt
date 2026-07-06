package com.makd.afinity.ui.music.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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

@Composable
fun MusicDetailActionRow(
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
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
            IconButton(onClick = onShuffle) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrows_shuffle),
                    contentDescription = stringResource(R.string.cd_music_shuffle),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            FloatingActionButton(
                onClick = onPlay,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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