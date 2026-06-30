package com.makd.afinity.ui.music.components

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.music.RadioMode
import com.makd.afinity.data.models.music.RadioSeed

private data class RadioModeOption(
    val mode: RadioMode,
    val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
)

private val RADIO_MODE_OPTIONS =
    listOf(
        RadioModeOption(
            mode = RadioMode.SIMILAR,
            iconRes = R.drawable.ic_compass,
            titleRes = R.string.music_radio_mode_similar,
            descriptionRes = R.string.music_radio_mode_similar_desc,
        ),
        RadioModeOption(
            mode = RadioMode.CONTINUOUS,
            iconRes = R.drawable.ic_broadcast,
            titleRes = R.string.music_radio_mode_continuous,
            descriptionRes = R.string.music_radio_mode_continuous_desc,
        ),
        RadioModeOption(
            mode = RadioMode.ALBUM_MIX,
            iconRes = R.drawable.ic_music_playlist,
            titleRes = R.string.music_radio_mode_album_mix,
            descriptionRes = R.string.music_radio_mode_album_mix_desc,
        ),
        RadioModeOption(
            mode = RadioMode.RESHUFFLE,
            iconRes = R.drawable.ic_arrows_shuffle,
            titleRes = R.string.music_radio_mode_reshuffle,
            descriptionRes = R.string.music_radio_mode_reshuffle_desc,
        ),
        RadioModeOption(
            mode = RadioMode.RANDOM,
            iconRes = R.drawable.ic_auto_awesome,
            titleRes = R.string.music_radio_mode_random,
            descriptionRes = R.string.music_radio_mode_random_desc,
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
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.music_action_start_radio_header),
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
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(option.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(option.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(option.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
