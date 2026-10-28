package com.makd.afinity.ui.audiobookshelf.item.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.audiobookshelf.AudibleRating
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.ui.audiobookshelf.withAbsWidth
import com.makd.afinity.ui.components.CircleFlagIcon
import com.makd.afinity.ui.components.DetailActionRow
import com.makd.afinity.ui.components.DetailCoverImage
import com.makd.afinity.ui.components.DetailCoverTitle
import com.makd.afinity.ui.components.getAutoFlagUrl
import com.makd.afinity.ui.components.mediainfo.AudiobookMediaInfoSheet
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle

@Composable
fun ItemHeaderContent(
    modifier: Modifier = Modifier,
    item: LibraryItem,
    progress: MediaProgress?,
    coverUrl: String?,
    onPlay: (() -> Unit)?,
    downloadInfo: AbsDownloadInfo? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    audibleRating: AudibleRating? = null,
    onToggleFinished: (() -> Unit)? = null,
    toggleFinishedEnabled: Boolean = true,
    showActions: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailCoverImage(
            imageUrl = coverUrl?.withAbsWidth(600),
            contentDescription = stringResource(R.string.cd_abs_cover),
        )

        DetailCoverTitle(
            title = item.media.metadata.title ?: stringResource(R.string.unknown_title),
            subtitle =
                item.media.metadata.authorName?.let {
                    stringResource(R.string.abs_by_author_fmt, it)
                },
        )

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

                    val finishedColor =
                        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                            Color(0xFF81C784)
                        } else {
                            Color(0xFF1B6E22)
                        }

                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = stringResource(R.string.cd_finished),
                        tint = finishedColor,
                        modifier = Modifier.size(14.dp),
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = stringResource(R.string.abs_finished),
                        style = MaterialTheme.typography.labelMedium,
                        color = finishedColor,
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
                        text =
                            stringResource(
                                R.string.abs_time_left_fmt,
                                formatDuration(remainingSeconds),
                            ),
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
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxWidth(),
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

        val hasActions = showActions && hasItemActions(item, onPlay, onToggleFinished, onDownload)

        if (audibleRating != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AudibleRatingRow(rating = audibleRating)
            Spacer(modifier = Modifier.height(if (hasActions) 16.dp else 12.dp))
        } else {
            Spacer(modifier = Modifier.height(if (hasActions) 16.dp else 8.dp))
        }

        if (hasActions) {
            ItemActionRow(
                item = item,
                progress = progress,
                coverUrl = coverUrl,
                onPlay = onPlay,
                downloadInfo = downloadInfo,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onDeleteDownload = onDeleteDownload,
                onToggleFinished = onToggleFinished,
                toggleFinishedEnabled = toggleFinishedEnabled,
            )
        }
    }
}

internal fun hasItemActions(
    item: LibraryItem,
    onPlay: (() -> Unit)?,
    onToggleFinished: (() -> Unit)?,
    onDownload: (() -> Unit)?,
): Boolean =
    onPlay != null ||
        onToggleFinished != null ||
        onDownload != null ||
        item.mediaType.equals("book", ignoreCase = true)

@Composable
internal fun ItemActionRow(
    item: LibraryItem,
    progress: MediaProgress?,
    coverUrl: String?,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
    downloadInfo: AbsDownloadInfo? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    onToggleFinished: (() -> Unit)? = null,
    toggleFinishedEnabled: Boolean = true,
) {
    val hasMediaInfo = item.mediaType.equals("book", ignoreCase = true)
    var showMediaInfo by remember { mutableStateOf(false) }
    if (showMediaInfo) {
        AudiobookMediaInfoSheet(
            item = item,
            imageUrl = coverUrl?.withAbsWidth(200),
            onDismiss = { showMediaInfo = false },
        )
    }

    DetailActionRow(
        onPlay = { onPlay?.invoke() },
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        playbackEnabled = onPlay != null,
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
                            finished -> MaterialTheme.colorScheme.primary
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
                                contentDescription = stringResource(R.string.cd_cancel_download),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error,
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
                            contentDescription = stringResource(R.string.cd_delete_download),
                            tint = MaterialTheme.colorScheme.error,
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

        if (hasMediaInfo) {
            IconButton(onClick = { showMediaInfo = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_info),
                    contentDescription = stringResource(R.string.media_info_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
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
        DetailSectionTitle(text = stringResource(R.string.abs_details))
        Spacer(modifier = Modifier.height(12.dp))

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
                tags.forEach { tag ->
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
            text = stringResource(R.string.abs_on_audible),
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
