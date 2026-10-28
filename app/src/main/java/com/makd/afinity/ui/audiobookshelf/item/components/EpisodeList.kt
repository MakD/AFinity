package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import com.makd.afinity.ui.components.MetadataOverflowRow
import com.makd.afinity.ui.utils.htmlToAnnotatedString
import com.makd.afinity.util.DateSkeleton
import com.makd.afinity.util.localizedDateFormat
import java.util.Date
import java.util.Locale

fun LazyListScope.episodeListItems(
    episodes: List<PodcastEpisode>,
    onEpisodePlay: (PodcastEpisode) -> Unit,
    expandedEpisodeId: String?,
    onExpandEpisode: (String?) -> Unit,
    episodeProgressMap: Map<String, MediaProgress> = emptyMap(),
    episodeDownloadMap: Map<String, AbsDownloadInfo> = emptyMap(),
    onEpisodeDownload: ((String) -> Unit)? = null,
    onEpisodeCancelDownload: ((String) -> Unit)? = null,
    onEpisodeDeleteDownload: ((String) -> Unit)? = null,
    onEpisodeToggleFinished: ((PodcastEpisode) -> Unit)? = null,
    onEpisodeMediaInfo: ((PodcastEpisode) -> Unit)? = null,
    nowPlayingEpisodeId: String? = null,
    fadeLastItem: Boolean = false,
) {
    itemsIndexed(items = episodes, key = { _, episode -> episode.id }) { index, episode ->
        val isExpanded = expandedEpisodeId == episode.id

        Box(
            modifier =
                Modifier.animateItem()
                    .fadeOutBottom(fadeLastItem && index == episodes.lastIndex)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            ExpandableEpisodeItem(
                episode = episode,
                isExpanded = isExpanded,
                onPlay = { onEpisodePlay(episode) },
                onExpandToggle = { onExpandEpisode(if (isExpanded) null else episode.id) },
                progress = episodeProgressMap[episode.id],
                downloadInfo = episodeDownloadMap[episode.id],
                onDownload = onEpisodeDownload?.let { { it(episode.id) } },
                onCancelDownload = onEpisodeCancelDownload?.let { { it(episode.id) } },
                onDeleteDownload = onEpisodeDeleteDownload?.let { { it(episode.id) } },
                onToggleFinished = onEpisodeToggleFinished?.let { { it(episode) } },
                onMediaInfo = onEpisodeMediaInfo?.let { { it(episode) } },
                toggleFinishedEnabled = nowPlayingEpisodeId != episode.id,
            )
        }
    }
}

@Composable
private fun ExpandableEpisodeItem(
    episode: PodcastEpisode,
    isExpanded: Boolean,
    onPlay: () -> Unit,
    onExpandToggle: () -> Unit,
    progress: MediaProgress? = null,
    downloadInfo: AbsDownloadInfo? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    onToggleFinished: (() -> Unit)? = null,
    onMediaInfo: (() -> Unit)? = null,
    toggleFinishedEnabled: Boolean = true,
) {
    val hasDescription = !episode.description.isNullOrBlank()
    val isFinished = progress?.isFinished == true

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onPlay)
                .padding(12.dp)
                .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (isFinished) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                val metaItems =
                    remember(episode.publishedAt, episode.duration, progress) {
                        val list = mutableListOf<@Composable () -> Unit>()
                        episode.publishedAt?.let { timestamp ->
                            list.add {
                                Text(
                                    text = formatDate(timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        episode.duration?.let { duration ->
                            list.add {
                                Text(
                                    text = formatDuration(duration),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (progress != null && !progress.isFinished && progress.progress > 0) {
                            list.add {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        progress = { progress.progress.toFloat() },
                                        modifier = Modifier.size(12.dp),
                                        color = Color(0xFFFFC107),
                                        strokeWidth = 2.dp,
                                        trackColor =
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val remainingSeconds = progress.duration - progress.currentTime
                                    Text(
                                        text = "${formatDuration(remainingSeconds)} left",
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        list
                    }
                MetadataOverflowRow(items = metaItems)
            }

            Spacer(modifier = Modifier.width(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToggleFinished != null) {
                    IconButton(
                        onClick = onToggleFinished,
                        enabled = toggleFinishedEnabled,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter =
                                if (isFinished) painterResource(id = R.drawable.ic_circle_check)
                                else painterResource(id = R.drawable.ic_circle_check_outline),
                            contentDescription =
                                if (isFinished) stringResource(R.string.cd_watched_unmark)
                                else stringResource(R.string.cd_watched_mark),
                            tint =
                                when {
                                    !toggleFinishedEnabled ->
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    isFinished -> Color.Green
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (onDownload != null || downloadInfo != null) {
                    when {
                        downloadInfo?.status == AbsDownloadStatus.COMPLETED ->
                            IconButton(
                                onClick = { onDeleteDownload?.invoke() },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription =
                                        stringResource(R.string.cd_abs_downloaded_delete),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                        downloadInfo?.status == AbsDownloadStatus.DOWNLOADING ||
                            downloadInfo?.status == AbsDownloadStatus.QUEUED -> {
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    progress = { downloadInfo.progress },
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                IconButton(
                                    onClick = { onCancelDownload?.invoke() },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_cancel),
                                        contentDescription =
                                            stringResource(R.string.cd_cancel_download),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        else ->
                            IconButton(
                                onClick = { onDownload?.invoke() },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_download),
                                    contentDescription =
                                        stringResource(R.string.cd_abs_download_episode),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                    }
                }

                if (onMediaInfo != null) {
                    IconButton(onClick = onMediaInfo, modifier = Modifier.size(32.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_info),
                            contentDescription = stringResource(R.string.media_info_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        episode.description?.let { description ->
            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                HtmlText(
                    html = description,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    renderHtml = isExpanded,
                    onClick = onExpandToggle,
                    modifier = Modifier.weight(1f),
                )

                if (hasDescription) {
                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onExpandToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            painter =
                                painterResource(
                                    id =
                                        if (isExpanded) R.drawable.ic_keyboard_arrow_up
                                        else R.drawable.ic_keyboard_arrow_down
                                ),
                            contentDescription =
                                if (isExpanded) stringResource(R.string.cd_collapse)
                                else stringResource(R.string.cd_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    textColor: Color,
    renderHtml: Boolean,
    onClick: () -> Unit,
) {
    val text =
        remember(html, renderHtml, textColor) {
            if (renderHtml) {
                htmlToAnnotatedString(html, textColor)
            } else {
                AnnotatedString(
                    HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                )
            }
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            ),
    )
}

private fun formatDate(timestamp: Long): String {
    val locale = Locale.getDefault()
    return localizedDateFormat(locale, DateSkeleton.MONTH_DAY_YEAR).format(Date(timestamp))
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}
