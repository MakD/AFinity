package com.makd.afinity.ui.requests

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.MediaStatus

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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = attributes.containerColor),
    ) {
        Text(
            text = stringResource(attributes.textRes),
            style = MaterialTheme.typography.labelSmall,
            color = attributes.contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Bold,
        )
    }
}