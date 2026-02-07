package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.ui.utils.htmlToAnnotatedString

@Composable
fun ItemHeader(
    item: LibraryItem,
    progress: MediaProgress?,
    serverUrl: String?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coverUrl =
        if (serverUrl != null && item.media.coverPath != null) {
            "$serverUrl/api/items/${item.id}/cover"
        } else null

    Box(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(530.dp)) {
            ItemHeroBackground(coverUrl = coverUrl)
        }

        ItemHeaderContent(item = item, progress = progress, coverUrl = coverUrl, onPlay = onPlay)
    }
}

@Composable
fun ItemHeroBackground(coverUrl: String?, modifier: Modifier = Modifier) {
    if (coverUrl != null) {
        Box(modifier = modifier.fillMaxSize()) {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(radius = 30.dp).background(Color.Black),
                alpha = 0.6f,
                contentScale = ContentScale.Crop,
            )

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.surface,
                                    ),
                                startY = 100f,
                            )
                        )
            )
        }
    } else {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
    }
}

@Composable
fun ItemHeaderContent(
    item: LibraryItem,
    progress: MediaProgress?,
    coverUrl: String?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.statusBarsPadding())
        Spacer(modifier = Modifier.height(60.dp))

        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Cover",
                modifier =
                    Modifier.width(200.dp)
                        .aspectRatio(1f)
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Card(
                modifier = Modifier.width(200.dp).aspectRatio(1f),
                shape = RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
            ) {}
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = item.media.metadata.title ?: "Unknown Title",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        item.media.metadata.authorName?.let { author ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "by $author",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        item.media.duration?.let { duration ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (progress != null && progress.progress > 0) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    CircularProgressIndicator(
                        progress = { progress.progress.toFloat() },
                        modifier = Modifier.size(12.dp),
                        color = Color(0xFFFFC107),
                        strokeWidth = 2.dp,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val remainingSeconds = progress.duration - progress.currentTime
                    Text(
                        text = "${formatDuration(remainingSeconds)} left",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        val genres = item.media.metadata.genres
        if (!genres.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                genres.take(2).forEach { genre ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_play_filled),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        if (progress != null && progress.progress > 0) "Continue Listening"
                        else "Play",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun ExpandableSynopsis(description: String, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }
    var isEllipsized by remember { mutableStateOf(false) }

    val containsHtml =
        remember(description) {
            description.contains("<", ignoreCase = true) &&
                (description.contains("href=", ignoreCase = true) ||
                    description.contains("<br", ignoreCase = true) ||
                    description.contains("<p", ignoreCase = true) ||
                    description.contains("<i>", ignoreCase = true) ||
                    description.contains("<b>", ignoreCase = true))
        }

    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedText =
        remember(description, linkColor) {
            if (containsHtml) htmlToAnnotatedString(description, linkColor) else null
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Overview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        val textStyle = MaterialTheme.typography.bodyMedium
        val textColor = MaterialTheme.colorScheme.onSurfaceVariant
        val lineHeight = 20.sp
        val maxLines = if (isExpanded) Int.MAX_VALUE else 4
        val overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis
        val animModifier = Modifier.animateContentSize()

        if (containsHtml && annotatedText != null) {
            Text(
                text = annotatedText,
                style = textStyle,
                color = textColor,
                lineHeight = lineHeight,
                maxLines = maxLines,
                overflow = overflow,
                modifier = animModifier,
                onTextLayout = { result ->
                    if (!isExpanded) isEllipsized = result.hasVisualOverflow
                },
            )
        } else {
            Text(
                text = description,
                style = textStyle,
                color = textColor,
                lineHeight = lineHeight,
                maxLines = maxLines,
                overflow = overflow,
                modifier = animModifier,
                onTextLayout = { result ->
                    if (!isExpanded) isEllipsized = result.hasVisualOverflow
                },
            )
        }

        if (isEllipsized || isExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isExpanded) "Show Less" else "Read more",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            isExpanded = !isExpanded
                        }
                        .padding(vertical = 4.dp),
            )
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
