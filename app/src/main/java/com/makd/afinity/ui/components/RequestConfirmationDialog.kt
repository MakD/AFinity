package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
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
    modifier: Modifier = Modifier,
    mediaBackdropUrl: String? = null,
    mediaTagline: String? = null,
    mediaOverview: String? = null,
    releaseDate: String? = null,
    runtime: Int? = null,
    voteAverage: Double? = null,
    certification: String? = null,
    originalLanguage: String? = null,
    director: String? = null,
    genres: List<String> = emptyList(),
    ratingsCombined: RatingsCombined? = null
) {
    val alreadyRequested = existingStatus != null && (
            mediaType == MediaType.MOVIE ||
                    existingStatus == MediaStatus.AVAILABLE ||
                    existingStatus == MediaStatus.PROCESSING
            )
    val headerImageUrl = mediaBackdropUrl?.takeIf { it.isNotBlank() } ?: mediaPosterUrl

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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (!headerImageUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        OptimizedAsyncImage(
                            imageUrl = headerImageUrl,
                            contentDescription = mediaTitle,
                            blurHash = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.7f)
                                        ),
                                        startY = 0f,
                                        endY = Float.POSITIVE_INFINITY
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = mediaTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!mediaTagline.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mediaTagline,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Column {
                        Text(
                            text = mediaTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!mediaTagline.isNullOrBlank()) {
                            Text(
                                text = mediaTagline,
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {

                    if ((ratingsCombined != null && (ratingsCombined.imdb != null || ratingsCombined.rt != null)) || (voteAverage != null && voteAverage > 0)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterHorizontally
                            ),
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var needsSeparator = false

                            ratingsCombined?.imdb?.criticsScore?.let { score ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_imdb_logo),
                                        contentDescription = "IMDb",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f", score),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                    )
                                }
                                needsSeparator = true
                            }

                            ratingsCombined?.rt?.criticsScore?.let { score ->
                                if (needsSeparator) MetadataDot()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val icon = when {
                                        ratingsCombined.rt.criticsRating == "Certified Fresh" -> R.drawable.ic_rotten_tomato_fresh
                                        score >= 60 -> R.drawable.ic_rotten_tomato_fresh
                                        else -> R.drawable.ic_rotten_tomato_rotten
                                    }
                                    Icon(
                                        painter = painterResource(id = icon),
                                        contentDescription = "RT Critic",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "$score%",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                    )
                                }
                                needsSeparator = true
                            }

                            ratingsCombined?.rt?.audienceScore?.let { score ->
                                if (needsSeparator) MetadataDot()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val icon = if (score >= 60) {
                                        R.drawable.ic_rt_fresh_popcorn
                                    } else {
                                        R.drawable.ic_rt_stale_popcorn
                                    }
                                    Icon(
                                        painter = painterResource(id = icon),
                                        contentDescription = "RT Audience",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "$score%",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                    )
                                }
                                needsSeparator = true
                            }

                            voteAverage?.let { rating ->
                                if (rating > 0) {
                                    if (needsSeparator) MetadataDot()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_tmdb),
                                            contentDescription = "TMDB",
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${(rating * 10).toInt()}%",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                        )
                                    }
                                    needsSeparator = true
                                }
                            }
                            originalLanguage?.takeIf { it.isNotBlank() }?.let { lang ->
                                if (needsSeparator) MetadataDot()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_language),
                                        contentDescription = "TMDB",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = lang.uppercase(),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                    )
                                    needsSeparator = true
                                }
                            }
                            certification?.takeIf { it.isNotBlank() }?.let { cert ->
                                if (needsSeparator) MetadataDot()
                                Text(
                                    text = cert,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                )
                                needsSeparator = true
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (!mediaOverview.isNullOrBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Overview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = mediaOverview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                            )
                        }
                    }

                    director?.takeIf { it.isNotBlank() }?.let { dir ->
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Director: ")
                                }
                                append(dir)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }

                    releaseDate?.let { date ->
                        val formattedDate = formatReleaseDate(date)
                        if (formattedDate.isNotBlank()) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("Release Date: ")
                                    }
                                    append(formattedDate)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        }
                    }

                    runtime?.let { minutes ->
                        if (minutes > 0) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("Runtime: ")
                                    }
                                    append(formatRuntime(minutes))
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        }
                    }

                    if (genres.isNotEmpty()) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Genre: ")
                                }
                                append(genres.joinToString(", "))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        )
                    }
                }

                if (!alreadyRequested) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Request this ${mediaType.toApiString()} on Jellyseerr?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
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

@Composable
private fun MetadataDot() {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

private fun formatReleaseDate(dateString: String): String {
    return try {
        val parts = dateString.split("-")
        if (parts.size >= 3) {
            val year = parts[0]
            val month = parts[1].toIntOrNull()
            val day = parts[2].take(2)
            val monthName = when (month) {
                1 -> "Jan"
                2 -> "Feb"
                3 -> "Mar"
                4 -> "Apr"
                5 -> "May"
                6 -> "Jun"
                7 -> "Jul"
                8 -> "Aug"
                9 -> "Sep"
                10 -> "Oct"
                11 -> "Nov"
                12 -> "Dec"
                else -> null
            }
            if (monthName != null) {
                "$day $monthName $year"
            } else {
                "$day $year"
            }
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}

private fun formatRuntime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) {
        "${hours}h ${mins}m"
    } else {
        "${mins}m"
    }
}