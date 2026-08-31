package com.makd.afinity.ui.item.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.ui.components.FavoriteToggleButton
import com.makd.afinity.ui.components.ModalSideSheet
import com.makd.afinity.ui.components.WatchedToggleButton
import com.makd.afinity.ui.components.WatchlistToggleButton
import com.makd.afinity.ui.components.formatRuntimeTicks
import com.makd.afinity.ui.components.isWideWindow
import com.makd.afinity.ui.components.rememberRatingMetadataScale
import com.makd.afinity.ui.item.components.shared.AdminAction
import com.makd.afinity.ui.item.components.shared.CastRibbon
import com.makd.afinity.ui.item.components.shared.EpisodeFrame
import com.makd.afinity.ui.item.components.shared.MediaLanguageFlagsCompact
import com.makd.afinity.ui.item.components.shared.MediaStreamBadges
import com.makd.afinity.ui.item.components.shared.PeopleDialog
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import com.makd.afinity.ui.item.components.shared.PlaybackSelectionButton
import com.makd.afinity.util.DateSkeleton
import com.makd.afinity.util.localizedDateFormat
import com.makd.afinity.util.rememberStorageLocationProvider
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailOverlay(
    episode: AfinityEpisode,
    isInWatchlist: Boolean,
    downloadInfo: DownloadInfo?,
    onDismiss: () -> Unit,
    onPlayClick: (AfinityEpisode, PlaybackSelection) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onToggleWatched: () -> Unit,
    onDownloadClick: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    isDownloadAllowedByServer: Boolean = true,
    canDownloadOnNetwork: Boolean = true,
    onDownloadLongClick: (() -> Unit)? = null,
    onGoToSeries: (() -> Unit)? = null,
    isAdmin: Boolean = false,
    onAdminAction: (AdminAction) -> Unit = {},
    onPersonClick: ((UUID) -> Unit)? = null,
) {
    val context = LocalContext.current
    val isWide = isWideWindow()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showPeopleDialog by remember { mutableStateOf(false) }
    val ratingScale = rememberRatingMetadataScale()
    val storageLocationProvider = rememberStorageLocationProvider()
    val downloadUnavailable =
        remember(downloadInfo?.itemId, downloadInfo?.status, downloadInfo?.storageVolumeId) {
            val info = downloadInfo
            info != null &&
                info.status == DownloadStatus.COMPLETED &&
                info.storageVolumeId !in storageLocationProvider.mountedVolumeIds()
        }

    val sheetBody =
        @Composable { insets: Modifier, topPadding: Dp ->
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .then(insets)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = topPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val isUnaired =
                    remember(episode.premiereDate) {
                        episode.premiereDate?.isAfter(java.time.LocalDateTime.now()) == true
                    }

                EpisodeFrame(episode = episode, isUnaired = isUnaired)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = episode.seriesName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = episode.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!episode.missing) {
                    PlaybackSelectionButton(
                        item = episode,
                        buttonText =
                            if (episode.playbackPositionTicks > 0)
                                stringResource(R.string.episode_resume)
                            else stringResource(R.string.episode_play),
                        buttonIcon = painterResource(id = R.drawable.ic_player_play_filled),
                        onPlayClick = { selection -> onPlayClick(episode, selection) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var needsSeparator = false

                    episode.premiereDate?.let { date ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isUnaired || episode.missing) {
                                Box(
                                    modifier =
                                        Modifier.background(
                                                if (isUnaired) Color(0xFF2E7D32).copy(alpha = 0.9f)
                                                else Color.Red.copy(alpha = 0.8f),
                                                RoundedCornerShape(4.dp),
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text =
                                            if (isUnaired) stringResource(R.string.episode_upcoming)
                                            else stringResource(R.string.episode_missing),
                                        color = Color.White,
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp,
                                            ),
                                    )
                                }
                                MetadataDot()
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_calendar),
                                    contentDescription = stringResource(R.string.cd_air_date),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }

                            val formattedDate =
                                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                            Text(
                                text = if (isUnaired) "Airs on $formattedDate" else formattedDate,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        needsSeparator = true
                    }

                    if (episode.runtimeTicks > 0) {
                        if (needsSeparator) MetadataDot()

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_clock),
                                contentDescription = stringResource(R.string.cd_duration),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = formatRuntimeTicks(episode.runtimeTicks),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        needsSeparator = true
                    }

                    if ((episode.partCount ?: 0) > 1) {
                        if (needsSeparator) MetadataDot()
                        Text(
                            text =
                                pluralStringResource(
                                    R.plurals.meta_parts_fmt,
                                    episode.partCount!!,
                                    episode.partCount,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        needsSeparator = true
                    }

                    if (LocalShowRatings.current) {
                        episode.communityRating?.let { rating ->
                            if (needsSeparator) MetadataDot()

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_community_rating),
                                    contentDescription = stringResource(R.string.cd_imdb),
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(ratingScale.rtIconSize),
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f", rating),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (episode.sources.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement =
                            Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MediaStreamBadges(source = episode.sources.firstOrNull())
                    }
                }

                if (episode.runtimeTicks > 0) {
                    val remainingTicks =
                        (episode.runtimeTicks - episode.playbackPositionTicks).coerceAtLeast(0L)
                    if (remainingTicks > 0) {
                        val totalMs = remainingTicks / 10_000L
                        val endTimeStr = getFormattedEndTime(context, totalMs)
                        Text(
                            text = stringResource(R.string.meta_ends_at, endTimeStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (episode.overview.isNotBlank()) {
                    Text(
                        text = episode.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        onGoToSeries?.let { goToSeries ->
                            IconButton(onClick = goToSeries) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_info),
                                    contentDescription = stringResource(R.string.cd_go_to_series),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }

                        WatchlistToggleButton(
                            isInWatchlist = isInWatchlist,
                            onClick = onToggleWatchlist,
                        )

                        FavoriteToggleButton(
                            isFavorite = episode.favorite,
                            onClick = onToggleFavorite,
                        )

                        if (!episode.missing) {
                            WatchedToggleButton(
                                isPlayed = episode.played,
                                onClick = onToggleWatched,
                            )
                        }

                        if (!episode.missing && isDownloadAllowedByServer) {
                            DownloadProgressIndicator(
                                downloadInfo = downloadInfo,
                                onDownloadClick = onDownloadClick,
                                onPauseClick = onPauseDownload,
                                onResumeClick = onResumeDownload,
                                onCancelClick = onCancelDownload,
                                canDownload = canDownloadOnNetwork,
                                isUnavailable = downloadUnavailable,
                                onDownloadLongClick = onDownloadLongClick,
                            )
                        }

                        if (isAdmin) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_options),
                                        contentDescription =
                                            stringResource(R.string.cd_admin_manage),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.admin_action_edit_metadata)
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter =
                                                    painterResource(id = R.drawable.ic_edit_circle),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onAdminAction(AdminAction.EditMetadata)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.admin_action_edit_images))
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter =
                                                    painterResource(
                                                        id = R.drawable.ic_photo_search
                                                    ),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onAdminAction(AdminAction.EditImages)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.admin_action_refresh_metadata
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter =
                                                    painterResource(id = R.drawable.ic_refresh),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onAdminAction(AdminAction.Refresh)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.admin_action_delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter =
                                                    painterResource(id = R.drawable.ic_delete),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onAdminAction(AdminAction.Delete)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                CastRibbon(
                    item = episode,
                    onSeeAllClick = { showPeopleDialog = true },
                    onPersonClick = onPersonClick,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

                MediaLanguageFlagsCompact(item = episode)
            }
        }

    if (isWide) {
        ModalSideSheet(onDismissRequest = onDismiss, title = episode.name) {
            sheetBody(
                Modifier.statusBarsPadding()
                    .navigationBarsPadding()
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                    ),
                24.dp,
            )
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier =
                Modifier.windowInsetsPadding(
                    WindowInsets.systemBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal)
                ),
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            dragHandle = {
                Box(
                    modifier =
                        Modifier.windowInsetsPadding(
                                WindowInsets.systemBars
                                    .union(WindowInsets.displayCutout)
                                    .only(WindowInsetsSides.Top)
                            )
                            .padding(vertical = 10.dp)
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                )
            },
        ) {
            sheetBody(Modifier.navigationBarsPadding(), 0.dp)
        }
    }

    if (showPeopleDialog) {
        PeopleDialog(
            item = episode,
            subtitle = episode.name,
            onPersonClick =
                onPersonClick?.let { handler ->
                    { personId: UUID ->
                        showPeopleDialog = false
                        handler(personId)
                    }
                },
            onDismiss = { showPeopleDialog = false },
        )
    }
}

@Composable
private fun MetadataDot() {
    Text(
        text = "•",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
}

private fun getFormattedEndTime(context: Context, totalMs: Long): String {
    val endDate = Date(System.currentTimeMillis() + totalMs)
    val twentyFourHoursMs = 24 * 60 * 60 * 1000L

    return if (totalMs > twentyFourHoursMs) {
        val is24Hour = DateFormat.is24HourFormat(context)
        val skeleton = DateSkeleton.withTime(DateSkeleton.WEEKDAY_MONTH_DAY, is24Hour)
        localizedDateFormat(Locale.getDefault(), skeleton).format(endDate)
    } else {
        DateFormat.getTimeFormat(context).format(endDate)
    }
}
