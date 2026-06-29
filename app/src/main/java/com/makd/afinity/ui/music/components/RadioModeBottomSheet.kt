package com.makd.afinity.ui.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.music.RadioMode
import com.makd.afinity.data.models.music.RadioSeed

private data class RadioModeOption(
    val mode: RadioMode,
    val iconRes: Int,
    val title: String,
    val description: String,
)

private val RADIO_MODE_OPTIONS = listOf(
    RadioModeOption(
        mode = RadioMode.SIMILAR,
        iconRes = R.drawable.ic_compass,
        title = "Similar",
        description = "Plays tracks similar to the seed, sourced from Jellyfin's Instant Mix",
    ),
    RadioModeOption(
        mode = RadioMode.CONTINUOUS,
        iconRes = R.drawable.ic_broadcast,
        title = "Continuous",
        description = "Each new track is similar to the previous one, flowing naturally",
    ),
    RadioModeOption(
        mode = RadioMode.ALBUM_MIX,
        iconRes = R.drawable.ic_music_playlist,
        title = "Album Mix",
        description = "Queues full albums that are similar to the seed album",
    ),
    RadioModeOption(
        mode = RadioMode.RESHUFFLE,
        iconRes = R.drawable.ic_arrows_shuffle,
        title = "Reshuffle",
        description = "Reshuffles the source tracks on repeat, like shuffle-repeat",
    ),
    RadioModeOption(
        mode = RadioMode.RANDOM,
        iconRes = R.drawable.ic_auto_awesome,
        title = "Random",
        description = "Picks tracks randomly from the source, duplicates allowed",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioModeBottomSheet(
    seed: RadioSeed,
    onDismiss: () -> Unit,
    onSelectMode: (RadioSeed, RadioMode) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = "START RADIO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            RADIO_MODE_OPTIONS.forEach { option ->
                RadioModeRow(
                    option = option,
                    onClick = {
                        onSelectMode(seed, option.mode)
                        onDismiss()
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun RadioModeRow(option: RadioModeOption, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(option.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}