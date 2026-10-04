package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.CollapsedSeries
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.ListeningSession
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisodeOrder
import com.makd.afinity.data.models.audiobookshelf.PodcastUpNext
import com.makd.afinity.data.models.audiobookshelf.imageUrl
import com.makd.afinity.ui.audiobookshelf.item.AuthorDetails
import com.makd.afinity.ui.audiobookshelf.item.ListeningSummary
import com.makd.afinity.ui.audiobookshelf.item.RecommendationRow
import com.makd.afinity.ui.audiobookshelf.libraries.components.AudiobookCard
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle
import com.makd.afinity.ui.item.components.shared.OverviewSection
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.util.DateSkeleton
import com.makd.afinity.util.localizedDateFormat
import java.util.Date

@Composable
internal fun AbsSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        DetailSectionTitle(
            text = text,
            modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
        )
        trailing?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
internal fun ListExpandButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedButton(
            onClick = onToggle,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
        ) {
            Crossfade(targetState = expanded, label = "see_more_label") { isExpanded ->
                Text(
                    text =
                        if (isExpanded) stringResource(R.string.action_see_less)
                        else stringResource(R.string.action_see_more),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

internal fun Modifier.fadeOutBottom(enabled: Boolean): Modifier =
    if (enabled) {
        graphicsLayer { alpha = 0.99f }
            .drawWithCache {
                val gradient =
                    Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height * 0.2f,
                        endY = size.height,
                    )
                onDrawWithContent {
                    drawContent()
                    drawRect(gradient, blendMode = BlendMode.DstIn)
                }
            }
    } else this

@Composable
fun NarratorsSection(narrators: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AbsSectionTitle(
            text = stringResource(R.string.abs_narrators_title),
            trailing = narrators.size.takeIf { it > 1 }?.toString(),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            itemsIndexed(narrators, key = { index, name -> "${index}_$name" }) { _, name ->
                Column(
                    modifier = Modifier.width(72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    InitialsAvatar(name = name, size = 56.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun InitialsAvatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val initials =
        remember(name) {
            name
                .split(' ')
                .filter { it.isNotBlank() }
                .let { parts -> listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull()) }
                .joinToString("") { it.take(1) }
                .uppercase()
        }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun AuthorCard(
    authorName: String,
    details: AuthorDetails?,
    serverUrl: String?,
    modifier: Modifier = Modifier,
) {
    val photoPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val photoUrl =
        remember(details, serverUrl, photoPx) {
            serverUrl?.let { url -> details?.author?.imageUrl(url, photoPx * 2) }
        }
    val bio = details?.author?.description?.takeIf { it.isNotBlank() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photoUrl != null) {
                    AsyncImage(
                        imageUrl = photoUrl,
                        contentDescription = authorName,
                        modifier = Modifier.size(56.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    InitialsAvatar(name = authorName, size = 56.dp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.abs_author_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    details?.titleCount?.let { count ->
                        Text(
                            text =
                                pluralStringResource(
                                    R.plurals.abs_titles_in_library_fmt,
                                    count,
                                    count,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            bio?.let {
                Spacer(modifier = Modifier.height(12.dp))
                OverviewSection(overview = it)
            }
        }
    }
}

@Composable
fun ListeningSection(summary: ListeningSummary, modifier: Modifier = Modifier) {
    var showSessions by remember { mutableStateOf(false) }
    val locale = LocalLocale.current.platformLocale
    val shortDate = remember(locale) { localizedDateFormat(locale, DateSkeleton.MONTH_DAY) }
    val longDate = remember(locale) { localizedDateFormat(locale, DateSkeleton.MONTH_DAY_YEAR) }

    Column(modifier = modifier.fillMaxWidth()) {
        AbsSectionTitle(text = stringResource(R.string.abs_your_listening))
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                iconRes = R.drawable.ic_headphones,
                value = formatListenTime(summary.totalListened),
                label = stringResource(R.string.abs_listened_label),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                iconRes = R.drawable.ic_history,
                value = summary.sessionCount.toString(),
                label = pluralStringResource(R.plurals.abs_sessions_label, summary.sessionCount),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                iconRes = R.drawable.ic_calendar,
                value = summary.lastPlayedAt?.let { shortDate.format(Date(it)) } ?: "–",
                label = stringResource(R.string.abs_last_played_label),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        summary.firstStartedAt?.let { startedAt ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text =
                    pluralStringResource(
                        R.plurals.abs_listening_since_fmt,
                        summary.listeningDays,
                        summary.listeningDays,
                        longDate.format(Date(startedAt)),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary.recent.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            role = Role.Button,
                        ) {
                            showSessions = !showSessions
                        }
                        .padding(vertical = 4.dp),
            ) {
                Text(
                    text =
                        if (showSessions) stringResource(R.string.abs_show_less)
                        else stringResource(R.string.abs_show_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    painter =
                        if (showSessions) painterResource(id = R.drawable.ic_keyboard_arrow_up)
                        else painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription =
                        if (showSessions) stringResource(R.string.cd_collapse)
                        else stringResource(R.string.cd_expand),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(
                visible = showSessions,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    summary.recent.forEach { session ->
                        SessionRow(
                            session = session,
                            dateText = session.updatedAt?.let { shortDate.format(Date(it)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    @DrawableRes iconRes: Int,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Box(
                modifier =
                    Modifier.size(28.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionRow(session: ListeningSession, dateText: String?) {
    val device = session.deviceInfo?.let { it.deviceName ?: it.clientName }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = listOfNotNull(dateText, device).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatListenTime(session.timeListening),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun RecommendationRowSection(
    row: RecommendationRow,
    serverUrl: String?,
    cardWidth: Dp,
    onItemClick: (LibraryItem) -> Unit,
    onSeriesClick: (CollapsedSeries) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        when (row.kind) {
            RecommendationRow.Kind.AUTHOR -> stringResource(R.string.abs_more_by_fmt, row.subject)
            RecommendationRow.Kind.NARRATOR ->
                stringResource(R.string.abs_narrated_by_fmt, row.subject)

            RecommendationRow.Kind.GENRE ->
                stringResource(R.string.related_more_in_genre_fmt, row.subject)
        }
    val rowHeight =
        CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_SQUARE) +
            CardDimensions.CardTextSpacing +
            CardDimensions.TitleLine +
            18.dp

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionTitle(text = title)
        LazyRow(
            modifier = Modifier.height(rowHeight),
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = row.items, key = { it.collapsedSeries?.id ?: it.id }) { entry ->
                val series = entry.collapsedSeries
                val booksLabel =
                    series?.numBooks?.let {
                        pluralStringResource(R.plurals.abs_series_books_fmt, it, it)
                    }
                val display =
                    remember(entry, booksLabel) {
                        if (series == null) entry
                        else
                            entry.copy(
                                media =
                                    entry.media.copy(
                                        metadata =
                                            entry.media.metadata.copy(
                                                title = series.name,
                                                authorName =
                                                    booksLabel ?: entry.media.metadata.authorName,
                                            )
                                    ),
                                userMediaProgress = null,
                            )
                    }
                AudiobookCard(
                    item = display,
                    serverUrl = serverUrl,
                    onClick = { if (series != null) onSeriesClick(series) else onItemClick(entry) },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

private enum class UpNextTarget {
    RESUME,
    NEXT,
    LATEST,
    FIRST,
}

@Composable
fun PodcastUpNextCard(
    upNext: PodcastUpNext,
    resumeProgress: MediaProgress?,
    isSerial: Boolean,
    onResume: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLatest: () -> Unit,
    onPlayFirst: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestEpisode = upNext.latest ?: return
    val firstEpisode = upNext.first
    val nextEpisode = upNext.nextUnplayed
    val primary =
        when {
            upNext.resume != null -> UpNextTarget.RESUME
            nextEpisode == null ->
                if (isSerial && firstEpisode != null) UpNextTarget.FIRST else UpNextTarget.LATEST

            nextEpisode.id == latestEpisode.id -> UpNextTarget.LATEST
            nextEpisode.id == firstEpisode?.id -> UpNextTarget.FIRST
            else -> UpNextTarget.NEXT
        }
    val featured =
        when (primary) {
            UpNextTarget.RESUME -> upNext.resume
            UpNextTarget.NEXT -> nextEpisode
            UpNextTarget.FIRST -> firstEpisode
            UpNextTarget.LATEST -> latestEpisode
        } ?: return
    val locale = LocalLocale.current.platformLocale
    val dateFormat = remember(locale) { localizedDateFormat(locale, DateSkeleton.MONTH_DAY_YEAR) }

    val label =
        stringResource(
            when (primary) {
                UpNextTarget.RESUME -> R.string.abs_continue_listening_label
                UpNextTarget.NEXT -> R.string.abs_next_unplayed_label
                UpNextTarget.LATEST -> R.string.abs_latest_episode_label
                UpNextTarget.FIRST -> R.string.abs_first_episode_label
            }
        )
    val meta =
        if (primary == UpNextTarget.RESUME && resumeProgress != null) {
            stringResource(
                R.string.abs_time_left_fmt,
                formatListenTime(resumeProgress.duration - resumeProgress.currentTime),
            )
        } else {
            listOfNotNull(
                    featured.publishedAt?.let { dateFormat.format(Date(it)) },
                    featured.duration?.let { formatListenTime(it) },
                )
                .joinToString(" · ")
        }
    val typeLabel =
        when {
            PodcastEpisodeOrder.isTrailer(featured) ->
                stringResource(R.string.abs_episode_type_trailer)

            featured.episodeType.equals("bonus", ignoreCase = true) ->
                stringResource(R.string.abs_episode_type_bonus)

            else -> null
        }
    val summary =
        remember(featured.id, featured.subtitle, featured.description) {
            featured.subtitle?.takeIf { it.isNotBlank() }
                ?: featured.description
                    ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString() }
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
        }

    val secondaryCandidates =
        if (isSerial) listOf(UpNextTarget.LATEST, UpNextTarget.FIRST)
        else listOf(UpNextTarget.FIRST, UpNextTarget.LATEST)
    val secondary = secondaryCandidates.firstOrNull { target ->
        val episode =
            when (target) {
                UpNextTarget.LATEST -> latestEpisode
                UpNextTarget.FIRST -> firstEpisode
                else -> null
            }
        target != primary && episode != null && episode.id != featured.id
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label.uppercase(locale),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                typeLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier =
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = featured.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick =
                        when (primary) {
                            UpNextTarget.RESUME -> onResume
                            UpNextTarget.NEXT -> onPlayNext
                            UpNextTarget.LATEST -> onPlayLatest
                            UpNextTarget.FIRST -> onPlayFirst
                        },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_play_filled),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text =
                            stringResource(
                                when (primary) {
                                    UpNextTarget.RESUME -> R.string.player_resume
                                    UpNextTarget.NEXT -> R.string.action_play
                                    UpNextTarget.LATEST -> R.string.abs_play_latest
                                    UpNextTarget.FIRST -> R.string.abs_from_episode_one
                                }
                            ),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                secondary?.let { target ->
                    OutlinedButton(
                        onClick = if (target == UpNextTarget.FIRST) onPlayFirst else onPlayLatest,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    if (target == UpNextTarget.FIRST) R.string.abs_from_episode_one
                                    else R.string.abs_latest_short
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

internal fun formatListenTime(seconds: Double): String {
    val totalMinutes = (seconds.coerceAtLeast(0.0) / 60).toLong()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
