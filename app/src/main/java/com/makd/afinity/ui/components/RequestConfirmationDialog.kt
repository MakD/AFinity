package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestConfirmationDialog(
    modifier: Modifier = Modifier,
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
                text = "Request on Seerr",
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
                                            Color.Black.copy(alpha = 0.8f)
                                        ),
                                        startY = 100f,
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
                    data class MetaItem(
                        val icon: Int = 0,
                        val text: String,
                        val contentDesc: String? = null,
                        val tint: Color = Color.Unspecified,
                        val flagUrl: String? = null
                    )

                    val metadataItems =
                        remember(ratingsCombined, voteAverage, originalLanguage, certification) {
                            buildList {
                                ratingsCombined?.imdb?.criticsScore?.let { score ->
                                    add(
                                        MetaItem(
                                            R.drawable.ic_imdb_logo,
                                            String.format(Locale.US, "%.1f", score),
                                            "IMDb"
                                        )
                                    )
                                }

                                ratingsCombined?.rt?.criticsScore?.let { score ->
                                    val icon =
                                        if (score >= 60) R.drawable.ic_rotten_tomato_fresh else R.drawable.ic_rotten_tomato_rotten
                                    add(MetaItem(icon, "$score%", "RT Critic"))
                                }

                                ratingsCombined?.rt?.audienceScore?.let { score ->
                                    val icon =
                                        if (score >= 60) R.drawable.ic_rt_fresh_popcorn else R.drawable.ic_rt_stale_popcorn
                                    add(MetaItem(icon, "$score%", "RT Audience"))
                                }

                                voteAverage?.let { rating ->
                                    if (rating > 0) {
                                        add(
                                            MetaItem(
                                                R.drawable.ic_tmdb,
                                                "${(rating * 10).toInt()}%",
                                                "TMDB"
                                            )
                                        )
                                    }
                                }

                                originalLanguage?.takeIf { it.isNotBlank() }?.let { lang ->
                                    add(
                                        MetaItem(
                                            icon = R.drawable.ic_language,
                                            text = lang.uppercase(),
                                            flagUrl = getAutoFlagUrl(lang),
                                            contentDesc = "Language"
                                        )
                                    )
                                }

                                certification?.takeIf { it.isNotBlank() }?.let { cert ->
                                    add(MetaItem(text = cert))
                                }
                            }
                        }

                    if (metadataItems.isNotEmpty()) {
                        SeparatedFlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            separator = { MetadataDot() }
                        ) {
                            metadataItems.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (item.icon != 0) {
                                        val itemTint = if (item.contentDesc == "Language") MaterialTheme.colorScheme.onSurface else item.tint
                                        Icon(
                                            painter = painterResource(id = item.icon),
                                            contentDescription = null,
                                            tint = itemTint,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    if (item.flagUrl != null) {
                                        CircleFlagIcon(url = item.flagUrl)
                                    } else if (item.icon == 0) {
                                        androidx.compose.material3.Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            ),
                                            color = Color.Transparent
                                        ) {
                                            Text(
                                                text = item.text,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = item.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                        )
                                    }
                                }
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
                                    append(
                                        "Director: "
                                    )
                                }
                                append(dir)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        )
                    }

                    releaseDate?.let { date ->
                        val formattedDate = formatReleaseDate(date)
                        if (formattedDate.isNotBlank()) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(
                                            "Release Date: "
                                        )
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
                                        append(
                                            "Runtime: "
                                        )
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
                                    append(
                                        "Genre: "
                                    )
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
                        text = "Request this ${mediaType.toApiString()} on Seerr?",
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

@Composable
private fun CircleFlagIcon(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val model = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = model,
        contentDescription = "Flag",
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun SeparatedFlowRow(
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 4.dp,
    separator: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val itemMeasurables = subcompose("content", content)
        val itemPlaceables = itemMeasurables.map { it.measure(constraints.copy(minWidth = 0)) }

        if (itemPlaceables.isEmpty()) {
            return@SubcomposeLayout layout(0, 0) {}
        }

        val separatorMeasurables = subcompose("separator_ref") { separator() }
        val separatorPlaceableRef =
            separatorMeasurables.firstOrNull()?.measure(constraints.copy(minWidth = 0))
        val separatorWidth = separatorPlaceableRef?.width ?: 0
        val spacingPx = itemSpacing.roundToPx()

        val sequences = mutableListOf<List<Placeable>>()
        var currentSequence = mutableListOf<Placeable>()
        var currentWidth = 0

        itemPlaceables.forEach { placeable ->
            val addedWidth = if (currentSequence.isEmpty()) {
                placeable.width
            } else {
                separatorWidth + (spacingPx * 2) + placeable.width
            }

            if (currentWidth + addedWidth <= constraints.maxWidth) {
                currentSequence.add(placeable)
                currentWidth += addedWidth
            } else {
                if (currentSequence.isNotEmpty()) sequences.add(currentSequence)
                currentSequence = mutableListOf(placeable)
                currentWidth = placeable.width
            }
        }
        if (currentSequence.isNotEmpty()) sequences.add(currentSequence)

        val totalSeparatorsNeeded = itemPlaceables.size - sequences.size
        val separatorList = subcompose("separators_real") {
            repeat(totalSeparatorsNeeded) { separator() }
        }.map { it.measure(constraints.copy(minWidth = 0)) }

        var separatorIndex = 0

        val verticalGap = 8.dp.roundToPx()
        val totalHeight = sequences.sumOf { line -> line.maxOf { it.height } } +
                ((sequences.size - 1) * verticalGap)

        layout(constraints.maxWidth, totalHeight) {
            var yOffset = 0
            sequences.forEach { line ->
                var xOffset = 0
                val lineHeight = line.maxOf { it.height }

                val lineWidth =
                    line.sumOf { it.width } + ((line.size - 1) * (separatorWidth + spacingPx * 2))
                xOffset = (constraints.maxWidth - lineWidth) / 2

                line.forEachIndexed { index, placeable ->
                    placeable.placeRelative(xOffset, yOffset + (lineHeight - placeable.height) / 2)
                    xOffset += placeable.width

                    if (index < line.lastIndex) {
                        xOffset += spacingPx

                        val sep = separatorList.getOrNull(separatorIndex++)
                        sep?.placeRelative(xOffset, yOffset + (lineHeight - sep.height) / 2)

                        xOffset += separatorWidth + spacingPx
                    }
                }
                yOffset += lineHeight + verticalGap
            }
        }
    }
}

private fun getAutoFlagUrl(langCode: String): String? {
    if (langCode.isBlank()) return null

    val manualMapping = when (langCode.lowercase()) {
        "en" -> "us"
        "hi" -> "in"
        "ja" -> "jp"
        "ko" -> "kr"
        "zh" -> "cn"
        "es" -> "es"
        "fr" -> "fr"
        "it" -> "it"
        "de" -> "de"
        "ru" -> "ru"
        "pt" -> "br"
        "tr" -> "tr"
        "th" -> "th"
        "id" -> "id"
        "tl" -> "ph"
        "vi" -> "vn"
        "pl" -> "pl"
        "uk" -> "ua"
        "ta" -> "in"
        "te" -> "in"
        "ml" -> "in"
        "kn" -> "in"
        "pa" -> "in"
        "mr" -> "in"
        "bn" -> "in"
        "sv" -> "se"
        "da" -> "dk"
        "no" -> "no"
        "fi" -> "fi"
        "is" -> "is"
        "nl" -> "nl"
        "cs" -> "cz"
        "hu" -> "hu"
        "el" -> "gr"
        "he" -> "il"
        "ar" -> "sa"
        "fa" -> "ir"
        else -> null
    }

    val countryCode = manualMapping ?: run {
        val locale = Locale.forLanguageTag(langCode)
        locale.country.ifBlank {
            Locale.getAvailableLocales()
                .firstOrNull { it.language == langCode && it.country.isNotBlank() }
                ?.country
        }
    }

    return if (!countryCode.isNullOrBlank()) {
        "https://hatscripts.github.io/circle-flags/flags/${countryCode.lowercase()}.svg"
    } else {
        null
    }
}

private fun formatReleaseDate(dateString: String): String {
    if (dateString.isBlank()) return ""
    return try {
        val date = LocalDate.parse(dateString)
        date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
    } catch (e: DateTimeParseException) {
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