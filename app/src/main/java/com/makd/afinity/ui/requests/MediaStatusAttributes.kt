package com.makd.afinity.ui.requests

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.ui.components.AfinityBadge

data class StatusAttributes(val textRes: Int, val containerColor: Color, val contentColor: Color)

@Composable
fun mediaStatusAttributes(mediaStatus: MediaStatus): StatusAttributes =
    when (mediaStatus) {
        MediaStatus.PENDING ->
            StatusAttributes(
                R.string.status_pending,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.onTertiary,
            )
        MediaStatus.PROCESSING ->
            StatusAttributes(
                R.string.status_processing,
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.onPrimary,
            )
        MediaStatus.PARTIALLY_AVAILABLE ->
            StatusAttributes(
                R.string.status_partially_available,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.onSecondary,
            )
        MediaStatus.AVAILABLE ->
            StatusAttributes(
                R.string.status_available,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.onSecondary,
            )
        MediaStatus.DELETED ->
            StatusAttributes(
                R.string.status_deleted,
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.onError,
            )
        MediaStatus.BLOCKLISTED ->
            StatusAttributes(
                R.string.status_blocklisted,
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.onError,
            )
        MediaStatus.UNKNOWN ->
            StatusAttributes(
                R.string.status_unknown,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }

@Composable
fun StatusChip(attributes: StatusAttributes, modifier: Modifier = Modifier) {
    AfinityBadge(
        text = stringResource(attributes.textRes),
        modifier = modifier,
        containerColor = attributes.containerColor,
        contentColor = attributes.contentColor,
    )
}
