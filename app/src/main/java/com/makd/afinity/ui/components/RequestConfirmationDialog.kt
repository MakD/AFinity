package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType

@Composable
fun RequestConfirmationDialog(
    mediaTitle: String,
    mediaPosterUrl: String?,
    mediaType: MediaType,
    availableSeasons: Int = 0,
    selectedSeasons: List<Int>,
    onSeasonsChange: (List<Int>) -> Unit,
    disabledSeasons: List<Int> = emptyList(),
    existingStatus: MediaStatus? = null,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alreadyRequested = existingStatus != null && (
        mediaType == MediaType.MOVIE ||
        existingStatus == MediaStatus.AVAILABLE ||
        existingStatus == MediaStatus.PROCESSING
    )
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = "Request on Jellyseerr",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!mediaPosterUrl.isNullOrBlank()) {
                    OptimizedAsyncImage(
                        imageUrl = mediaPosterUrl,
                        contentDescription = mediaTitle,
                        blurHash = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = mediaTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (alreadyRequested && existingStatus != null) {
                    Text(
                        text = "This ${mediaType.toApiString()} is already ${MediaStatus.getDisplayName(existingStatus)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Request this ${mediaType.toApiString()} on Jellyseerr?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (mediaType == MediaType.TV && !alreadyRequested) {
                    SeasonSelector(
                        availableSeasons = availableSeasons,
                        selectedSeasons = selectedSeasons,
                        onSeasonsChange = onSeasonsChange,
                        disabledSeasons = disabledSeasons
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !alreadyRequested && !isLoading && (mediaType == MediaType.MOVIE || selectedSeasons.isNotEmpty() || availableSeasons == 0)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (alreadyRequested) "Already Requested" else "Request")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}