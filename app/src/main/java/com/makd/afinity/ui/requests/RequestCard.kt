package com.makd.afinity.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.RequestStatus
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.rememberLandscapeCardWidth

@Composable
fun RequestCard(
    request: JellyseerrRequest,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = rememberLandscapeCardWidth()
    val isPending = request.status == RequestStatus.PENDING.value

    Column(
        modifier = modifier.width(cardWidth)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val backdropUrl = request.media.getBackdropUrl() ?: request.media.getPosterUrl()
                OptimizedAsyncImage(
                    imageUrl = backdropUrl,
                    contentDescription = request.media.getDisplayTitle(),
                    blurHash = null,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth * 9f / 16f,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                )

                request.media.getPosterUrl()?.let { posterUrl ->
                    OptimizedAsyncImage(
                        imageUrl = posterUrl,
                        contentDescription = request.media.getDisplayTitle(),
                        blurHash = null,
                        targetWidth = cardWidth * 0.3f,
                        targetHeight = cardWidth * 9f / 16f,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
                            .fillMaxHeight()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                val status = MediaStatus.fromValue(request.media.status ?: 1)
                if (status != null) {
                    val backgroundColor = when (status) {
                        MediaStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                        MediaStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                        MediaStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                        MediaStatus.PARTIALLY_AVAILABLE -> MaterialTheme.colorScheme.secondary
                        MediaStatus.AVAILABLE -> MaterialTheme.colorScheme.secondary
                        MediaStatus.DELETED -> MaterialTheme.colorScheme.error
                    }

                    val textColor = when (status) {
                        MediaStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                        MediaStatus.PENDING -> MaterialTheme.colorScheme.onTertiary
                        MediaStatus.PROCESSING -> MaterialTheme.colorScheme.onPrimary
                        MediaStatus.PARTIALLY_AVAILABLE -> MaterialTheme.colorScheme.onSecondary
                        MediaStatus.AVAILABLE -> MaterialTheme.colorScheme.onSecondary
                        MediaStatus.DELETED -> MaterialTheme.colorScheme.onError
                    }

                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = backgroundColor
                        )
                    ) {
                        Text(
                            text = MediaStatus.getDisplayName(status),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isPending && isAdmin) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Card(
                            onClick = onApprove,
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = "Approve",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Card(
                            onClick = onDecline,
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_cancel),
                                    contentDescription = "Decline",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = request.media.getDisplayTitle(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Requested by ${request.requestedBy.displayName ?: "Unknown"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}