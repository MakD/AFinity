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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.RequestStatus
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun RequestCard(
    request: JellyseerrRequest,
    baseUrl: String?,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val isPending = request.status == RequestStatus.PENDING.value

    Column(modifier = modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                    modifier = Modifier.fillMaxSize().align(Alignment.Center),
                )

                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)))

                request.media.getPosterUrl()?.let { posterUrl ->
                    AsyncImage(
                        imageUrl = posterUrl,
                        contentDescription = request.media.getDisplayTitle(),
                        blurHash = null,
                        targetWidth = cardWidth * 0.4f,
                        targetHeight = cardWidth * 9f / 16f,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier.align(Alignment.CenterStart)
                                .padding(6.dp)
                                .fillMaxHeight()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp)),
                    )
                }

                val statusAttributes = getRequestStatusAttributes(request)
                Card(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors =
                        CardDefaults.cardColors(containerColor = statusAttributes.containerColor),
                ) {
                    Text(
                        text = stringResource(statusAttributes.textRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusAttributes.contentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val rawAvatarUrl = request.requestedBy.avatar
                    val avatarUrl =
                        remember(rawAvatarUrl, baseUrl) {
                            if (rawAvatarUrl.isNullOrBlank()) null
                            else if (rawAvatarUrl.startsWith("http")) rawAvatarUrl
                            else if (!baseUrl.isNullOrBlank()) {
                                var cleanBase = baseUrl.trimEnd('/')
                                if (!cleanBase.startsWith("http")) cleanBase = "http://$cleanBase"
                                "$cleanBase/${rawAvatarUrl.trimStart('/')}"
                            } else null
                        }

                    if (avatarUrl != null) {
                        AsyncImage(
                            imageUrl = avatarUrl,
                            contentDescription =
                                stringResource(
                                    R.string.requested_by_fmt,
                                    request.requestedBy.displayName ?: "",
                                ),
                            blurHash = null,
                            targetWidth = 32.dp,
                            targetHeight = 32.dp,
                            modifier = Modifier.size(24.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user_circle),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    if (isPending && isAdmin) {
                        FilledIconButton(
                            onClick = onApprove,
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = stringResource(R.string.cd_approve),
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        FilledIconButton(
                            onClick = onDecline,
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_cancel),
                                contentDescription = stringResource(R.string.cd_decline),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = request.media.getDisplayTitle(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text =
                stringResource(
                    R.string.requested_by_fmt,
                    request.requestedBy.displayName ?: stringResource(R.string.user_unknown),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class StatusAttributes(
    val textRes: Int,
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
private fun getRequestStatusAttributes(request: JellyseerrRequest): StatusAttributes {
    val requestStatus = RequestStatus.fromValue(request.status)
    val statusValue =
        if (request.is4k) {
            request.media.status4k ?: 1
        } else {
            request.media.status ?: 1
        }
    val mediaStatus = MediaStatus.fromValue(statusValue)

    return when (requestStatus) {
        RequestStatus.DECLINED ->
            StatusAttributes(
                textRes = R.string.status_declined,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        RequestStatus.APPROVED if mediaStatus == MediaStatus.PENDING ->
            StatusAttributes(
                textRes = R.string.status_processing,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        else -> {
            val (textRes, containerColor, contentColor) =
                when (mediaStatus) {
                    MediaStatus.PENDING ->
                        Triple(
                            R.string.status_pending,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.onTertiary,
                        )
                    MediaStatus.PROCESSING ->
                        Triple(
                            R.string.status_processing,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.onPrimary,
                        )
                    MediaStatus.PARTIALLY_AVAILABLE ->
                        Triple(
                            R.string.status_partially_available,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.onSecondary,
                        )
                    MediaStatus.AVAILABLE ->
                        Triple(
                            R.string.status_available,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.onSecondary,
                        )
                    MediaStatus.DELETED ->
                        Triple(
                            R.string.status_deleted,
                            MaterialTheme.colorScheme.error,
                            MaterialTheme.colorScheme.onError,
                        )
                    else ->
                        Triple(
                            R.string.status_unknown,
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            StatusAttributes(textRes, containerColor, contentColor)
        }
    }
}
