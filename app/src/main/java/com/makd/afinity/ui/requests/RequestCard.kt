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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.RequestStatus
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun RequestCard(
    request: JellyseerrRequest,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier
) {
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
                AsyncImage(
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
                    AsyncImage(
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

                val requestStatus = RequestStatus.fromValue(request.status)
                val mediaStatus = MediaStatus.fromValue(request.media.status ?: 1)

                val (badgeText, badgeColor, badgeTextColor) = when {
                    requestStatus == RequestStatus.DECLINED -> Triple(
                        stringResource(R.string.status_declined),
                        MaterialTheme.colorScheme.error,
                        MaterialTheme.colorScheme.onError
                    )

                    requestStatus == RequestStatus.APPROVED && mediaStatus == MediaStatus.PENDING -> Triple(
                        stringResource(R.string.status_processing),
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.onPrimary
                    )

                    else -> {
                        val statusText = when (mediaStatus) {
                            MediaStatus.PENDING -> stringResource(R.string.status_pending)
                            MediaStatus.PROCESSING -> stringResource(R.string.status_processing)
                            MediaStatus.PARTIALLY_AVAILABLE -> stringResource(R.string.status_partially_available)
                            MediaStatus.AVAILABLE -> stringResource(R.string.status_available)
                            MediaStatus.DELETED -> stringResource(R.string.status_deleted)
                            else -> stringResource(R.string.status_unknown)
                        }

                        Triple(
                            statusText,
                            when (mediaStatus) {
                                MediaStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                                MediaStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                                MediaStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                                MediaStatus.PARTIALLY_AVAILABLE -> MaterialTheme.colorScheme.secondary
                                MediaStatus.AVAILABLE -> MaterialTheme.colorScheme.secondary
                                MediaStatus.DELETED -> MaterialTheme.colorScheme.error
                            },
                            when (mediaStatus) {
                                MediaStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                                MediaStatus.PENDING -> MaterialTheme.colorScheme.onTertiary
                                MediaStatus.PROCESSING -> MaterialTheme.colorScheme.onPrimary
                                MediaStatus.PARTIALLY_AVAILABLE -> MaterialTheme.colorScheme.onSecondary
                                MediaStatus.AVAILABLE -> MaterialTheme.colorScheme.onSecondary
                                MediaStatus.DELETED -> MaterialTheme.colorScheme.onError
                            }
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = badgeColor
                    )
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
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
                                    contentDescription = stringResource(R.string.cd_approve),
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
                                    contentDescription = stringResource(R.string.cd_decline),
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
            text = stringResource(
                R.string.requested_by_fmt,
                request.requestedBy.displayName ?: stringResource(R.string.user_unknown)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}