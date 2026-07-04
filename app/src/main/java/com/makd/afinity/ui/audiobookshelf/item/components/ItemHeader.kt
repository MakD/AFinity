package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.audiobookshelf.AudibleRating
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.coverUrl
import com.makd.afinity.ui.components.CircleFlagIcon
import com.makd.afinity.ui.components.getAutoFlagUrl
import com.makd.afinity.ui.utils.htmlToAnnotatedString

private fun String.withAbsWidth(px: Int): String {
    if (startsWith("file://")) return this
    val sep = if ('?' in this) "&" else "?"
    return "${this}${sep}width=$px"
}

@Composable
fun ItemHeader(
    item: LibraryItem,
    progress: MediaProgress?,
    serverUrl: String?,
    onPlay: () -> Unit,
    downloadInfo: AbsDownloadInfo? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    audibleRating: AudibleRating? = null,
    onToggleFinished: (() -> Unit)? = null,
    toggleFinishedEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val coverUrl =
        if (
            downloadInfo?.status == AbsDownloadStatus.COMPLETED && downloadInfo.localDirPath != null
        ) {
            "file://${downloadInfo.localDirPath}/cover.jpg"
        } else if (serverUrl != null && item.media.coverPath != null) {
            item.coverUrl(serverUrl)
        } else null

    Box(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(530.dp)) {
            ItemHeroBackground(coverUrl = coverUrl)
        }

        ItemHeaderContent(
            item = item,
            progress = progress,
            coverUrl = coverUrl,
            onPlay = onPlay,
            downloadInfo = downloadInfo,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onDeleteDownload = onDeleteDownload,
            audibleRating = audibleRating,
            onToggleFinished = onToggleFinished,
            toggleFinishedEnabled = toggleFinishedEnabled,
        )
    }
}

@Composable
fun ItemHeroBackground(coverUrl: String?, modifier: Modifier = Modifier) {
    if (coverUrl != null) {
        Box(modifier = modifier.fillMaxSize()) {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl.withAbsWidth(600))
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
    downloadInfo: AbsDownloadInfo? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    audibleRating: AudibleRating? = null,
    onToggleFinished: (() -> Unit)? = null,
    toggleFinishedEnabled: Boolean = true,
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
                model = coverUrl.withAbsWidth(600),
                contentDescription = stringResource(R.string.cd_abs_cover),
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
            text = item.media.metadata.title ?: stringResource(R.string.unknown_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        item.media.metadata.authorName?.let { author ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.abs_by_author_fmt, author),
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

                Spacer(modifier = Modifier.width(4.dp))

                if (progress != null && progress.isFinished) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = stringResource(R.string.cd_finished),
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(14.dp),
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = stringResource(R.string.abs_finished),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (progress != null && progress.progress > 0) {
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
        val language = item.media.metadata.language?.takeIf { it.isNotBlank() }
        val flagUrl = remember(language) { language?.let { getAutoFlagUrl(it) } }
        val isExplicit = item.media.metadata.explicit == true
        val isAbridged = item.media.metadata.abridged == true
        val hasChips =
            !genres.isNullOrEmpty() ||
                flagUrl != null ||
                language != null ||
                isExplicit ||
                isAbridged

        if (hasChips) {
            val hasGenres = !genres.isNullOrEmpty()
            val hasLanguage = flagUrl != null || language != null
            val dotColor = MaterialTheme.colorScheme.onSurfaceVariant

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                genres?.take(2)?.forEach { genre ->
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

                if (hasLanguage && hasGenres) {
                    Text(text = "•", color = dotColor, style = MaterialTheme.typography.labelSmall)
                }

                if (flagUrl != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_language),
                            contentDescription = stringResource(R.string.cd_abs_language),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                        CircleFlagIcon(url = flagUrl, modifier = Modifier.size(16.dp))
                    }
                } else if (language != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = language,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }

                if (isExplicit && (hasGenres || hasLanguage)) {
                    Text(text = "•", color = dotColor, style = MaterialTheme.typography.labelSmall)
                }

                if (isExplicit) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_explicit),
                            contentDescription = stringResource(R.string.cd_abs_explicit),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(2.dp).size(14.dp),
                        )
                    }
                }

                if (isAbridged && (hasGenres || hasLanguage || isExplicit)) {
                    Text(text = "•", color = dotColor, style = MaterialTheme.typography.labelSmall)
                }

                if (isAbridged) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_abridged),
                            contentDescription = stringResource(R.string.cd_abs_abridged),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(2.dp).size(14.dp),
                        )
                    }
                }
            }
        }

        if (audibleRating != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AudibleRatingRow(rating = audibleRating)
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onToggleFinished != null) {
                    val finished = progress?.isFinished == true
                    IconButton(onClick = onToggleFinished, enabled = toggleFinishedEnabled) {
                        Icon(
                            painter =
                                if (finished) painterResource(id = R.drawable.ic_circle_check)
                                else painterResource(id = R.drawable.ic_circle_check_outline),
                            contentDescription =
                                if (finished) stringResource(R.string.cd_watched_unmark)
                                else stringResource(R.string.cd_watched_mark),
                            tint =
                                when {
                                    !toggleFinishedEnabled ->
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    finished -> Color.Green
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                if (onDownload != null) {
                    IconButton(
                        onClick = {
                            when (downloadInfo?.status) {
                                AbsDownloadStatus.QUEUED,
                                AbsDownloadStatus.DOWNLOADING -> onCancelDownload?.invoke()
                                AbsDownloadStatus.COMPLETED -> onDeleteDownload?.invoke()
                                else -> onDownload.invoke()
                            }
                        }
                    ) {
                        when (downloadInfo?.status) {
                            AbsDownloadStatus.DOWNLOADING -> {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { downloadInfo.progress },
                                        modifier = Modifier.size(26.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_cancel),
                                        contentDescription =
                                            stringResource(R.string.cd_cancel_download),
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.Red,
                                    )
                                }
                            }
                            AbsDownloadStatus.QUEUED -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            AbsDownloadStatus.COMPLETED -> {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription =
                                        stringResource(R.string.cd_delete_download),
                                    tint = Color.Red,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            else -> {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_download),
                                    contentDescription = stringResource(R.string.cd_abs_download),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = onPlay,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_play_filled),
                    contentDescription = stringResource(R.string.action_play),
                    modifier = Modifier.size(26.dp),
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
            text = stringResource(R.string.abs_overview),
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
                text =
                    if (isExpanded) stringResource(R.string.abs_show_less)
                    else stringResource(R.string.abs_read_more),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemDetailsSection(item: LibraryItem, modifier: Modifier = Modifier) {
    val metadata = item.media.metadata
    val publisher = metadata.publisher
    val year = metadata.publishedYear
    val tags = item.media.tags

    val hasPublisherOrYear = !publisher.isNullOrBlank() || !year.isNullOrBlank()
    val hasTags = !tags.isNullOrEmpty()

    if (!hasPublisherOrYear && !hasTags) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.abs_details),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (hasPublisherOrYear) {
            val label =
                when {
                    !publisher.isNullOrBlank() && !year.isNullOrBlank() ->
                        stringResource(R.string.abs_publisher)
                    !publisher.isNullOrBlank() -> stringResource(R.string.abs_publisher)
                    else -> stringResource(R.string.abs_year)
                }
            val value =
                when {
                    !publisher.isNullOrBlank() && !year.isNullOrBlank() -> "$publisher \u2022 $year"
                    !publisher.isNullOrBlank() -> publisher
                    else -> year!!
                }
            DetailRow(label = label, value = value)
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (hasTags) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.abs_tags),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags!!.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(
                    style =
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                        )
                ) {
                    append("$label: ")
                }
                withStyle(
                    style =
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                ) {
                    append(value)
                }
            },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun AudibleRatingRow(rating: AudibleRating, modifier: Modifier = Modifier) {
    val amber = Color(0xFFFFC107)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_star),
            contentDescription = null,
            tint = amber,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "%.1f".format(rating.rating),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "on Audible",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
