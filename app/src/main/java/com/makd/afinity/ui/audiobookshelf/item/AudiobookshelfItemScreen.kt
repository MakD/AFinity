package com.makd.afinity.ui.audiobookshelf.item

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.audiobookshelf.EpisodeSort
import com.makd.afinity.data.models.audiobookshelf.EpisodeSortKey
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import com.makd.afinity.data.models.audiobookshelf.coverUrl
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.ui.audiobookshelf.item.components.AbsSectionTitle
import com.makd.afinity.ui.audiobookshelf.item.components.AuthorCard
import com.makd.afinity.ui.audiobookshelf.item.components.IncludedInSeriesSection
import com.makd.afinity.ui.audiobookshelf.item.components.ItemActionRow
import com.makd.afinity.ui.audiobookshelf.item.components.ItemDetailsSection
import com.makd.afinity.ui.audiobookshelf.item.components.ItemHeaderContent
import com.makd.afinity.ui.audiobookshelf.item.components.ListExpandButton
import com.makd.afinity.ui.audiobookshelf.item.components.ListeningSection
import com.makd.afinity.ui.audiobookshelf.item.components.NarratorsSection
import com.makd.afinity.ui.audiobookshelf.item.components.PodcastUpNextCard
import com.makd.afinity.ui.audiobookshelf.item.components.RecommendationRowSection
import com.makd.afinity.ui.audiobookshelf.item.components.chapterWindowItems
import com.makd.afinity.ui.audiobookshelf.item.components.episodeListItems
import com.makd.afinity.ui.audiobookshelf.item.components.formatListenTime
import com.makd.afinity.ui.audiobookshelf.item.components.hasItemActions
import com.makd.afinity.ui.audiobookshelf.withAbsWidth
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.RadioOptionRow
import com.makd.afinity.ui.components.backdropChainOf
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.components.mediainfo.PodcastEpisodeMediaInfoSheet
import com.makd.afinity.ui.item.components.shared.DetailSectionGap
import com.makd.afinity.ui.item.components.shared.LocalDetailLayout
import com.makd.afinity.ui.item.components.shared.OverviewSection
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import com.makd.afinity.ui.utils.rememberTopBarOpacity

private const val EPISODE_PREVIEW_COUNT = 5

@get:StringRes
private val EpisodeSortKey.labelRes: Int
    get() =
        when (this) {
            EpisodeSortKey.PUB_DATE -> R.string.abs_sort_pub_date
            EpisodeSortKey.TITLE -> R.string.abs_sort_title
            EpisodeSortKey.SEASON -> R.string.abs_sort_season
            EpisodeSortKey.EPISODE -> R.string.abs_sort_episode
            EpisodeSortKey.FILENAME -> R.string.abs_sort_filename
        }

@Composable
fun AudiobookshelfItemScreen(
    onNavigateToPlayer: (String, String?, Double?, String?, Boolean) -> Unit,
    onNavigateToSeries: (seriesId: String, libraryId: String, seriesName: String) -> Unit =
        { _, _, _ ->
        },
    onNavigateToItem: (itemId: String) -> Unit = {},
    onNavigateHome: () -> Unit = {},
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: AudiobookshelfItemViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item by viewModel.item.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val episodeProgressMap by viewModel.episodeProgressMap.collectAsStateWithLifecycle()
    val downloadInfo by viewModel.downloadInfo.collectAsStateWithLifecycle()
    val episodeDownloadMap by viewModel.episodeDownloadMap.collectAsStateWithLifecycle()
    val nowPlayingEpisodeId by viewModel.nowPlayingEpisodeId.collectAsStateWithLifecycle()
    val isThisItemPlaying by viewModel.isThisItemPlaying.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()
    val audibleRating by viewModel.audibleRating.collectAsStateWithLifecycle()
    val authorDetails by viewModel.authorDetails.collectAsStateWithLifecycle()
    val listening by viewModel.listening.collectAsStateWithLifecycle()
    val recommendationRows by viewModel.recommendationRows.collectAsStateWithLifecycle()
    val sortedEpisodes by viewModel.sortedEpisodes.collectAsStateWithLifecycle()
    val episodeSort by viewModel.episodeSort.collectAsStateWithLifecycle()
    val upNext by viewModel.podcastUpNext.collectAsStateWithLifecycle()
    val detailLayout by viewModel.detailLayout.collectAsStateWithLifecycle()

    val isPodcast = item?.mediaType?.lowercase() == "podcast"
    val isLandscape = isLandscapeWindow()

    var showAllChapters by rememberSaveable { mutableStateOf(false) }
    var showListenedChapters by rememberSaveable { mutableStateOf(false) }
    var showAllEpisodes by rememberSaveable { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    var expandedEpisodeId by remember { mutableStateOf<String?>(null) }
    var mediaInfoEpisode by remember { mutableStateOf<PodcastEpisode?>(null) }

    val mediaInfoItem = item
    val shownEpisode = mediaInfoEpisode
    if (mediaInfoItem != null && shownEpisode != null) {
        PodcastEpisodeMediaInfoSheet(
            item = mediaInfoItem,
            episode = shownEpisode,
            imageUrl =
                config
                    ?.serverUrl
                    ?.takeIf { mediaInfoItem.media.coverPath != null }
                    ?.let { mediaInfoItem.coverUrl(it, 200) },
            onDismiss = { mediaInfoEpisode = null },
        )
    }

    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)

    val resumePositionOf: (PodcastEpisode) -> Double? = { episode ->
        episodeProgressMap[episode.id]?.takeIf { !it.isFinished && it.currentTime > 0 }?.currentTime
    }
    val playEpisode: (PodcastEpisode, Double?, EpisodeSort?, Boolean) -> Unit =
        { episode, startPosition, queueOrder, includePlayed ->
            onNavigateToPlayer(
                viewModel.itemId,
                episode.id,
                startPosition,
                queueOrder?.param,
                includePlayed,
            )
        }
    val isSerial = item.isSerialPodcast()
    val playResume: () -> Unit = {
        upNext.resume?.let { playEpisode(it, resumePositionOf(it), episodeSort, false) }
    }
    val playNextUnplayed: () -> Unit = {
        upNext.nextUnplayed?.let {
            playEpisode(
                it,
                resumePositionOf(it),
                if (isSerial) episodeSort.ascendingOrder() else EpisodeSort.Default,
                false,
            )
        }
    }
    val playLatest: () -> Unit = {
        upNext.latest?.let { playEpisode(it, resumePositionOf(it), EpisodeSort.Default, false) }
    }
    val playFirst: () -> Unit = {
        upNext.first?.let { playEpisode(it, 0.0, episodeSort.ascendingOrder(), true) }
    }

    CompositionLocalProvider(LocalDetailLayout provides (detailLayout ?: DetailLayout.CLASSIC)) {
        AfinityScreen(
            topBar = { backgroundOpacity ->
                AfinityTopAppBar(
                    title = {},
                    onHomeClick = onNavigateHome,
                    backgroundOpacity = backgroundOpacity,
                )
            },
            mode = AfinityScreenMode.Hero,
            scrollOpacity = { topBarOpacity },
        ) {
            when {
                uiState.isLoading || detailLayout == null -> {
                    FullScreenLoading()
                }

                item == null -> {
                    FullScreenError(message = uiState.error)
                }

                item != null -> {
                    val currentItem = item!!
                    val metadata = currentItem.media.metadata

                    val onPlay: (() -> Unit)? =
                        if (isPodcast) null
                        else ({ onNavigateToPlayer(viewModel.itemId, null, null, null, false) })

                    val narrators =
                        metadata.narrators?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                            ?: metadata.narratorName
                                ?.split(",")
                                ?.map { it.trim() }
                                ?.filter { it.isNotEmpty() }
                                .orEmpty()
                    val authorName = metadata.authors?.firstOrNull()?.name ?: metadata.authorName
                    val chaptersTrailing =
                        listOfNotNull(
                                uiState.chapters.size.toString(),
                                currentItem.media.duration?.let { formatListenTime(it) },
                            )
                            .joinToString(" · ")
                    val showNetworkSections = !isPodcast && !isOffline

                    val detailBody: LazyListScope.() -> Unit = {
                        if (isPodcast && upNext.latest != null) {
                            detailItem("up_next", DetailGutter) {
                                PodcastUpNextCard(
                                    upNext = upNext,
                                    resumeProgress =
                                        upNext.resume?.let { episodeProgressMap[it.id] },
                                    isSerial = isSerial,
                                    onResume = playResume,
                                    onPlayNext = playNextUnplayed,
                                    onPlayLatest = playLatest,
                                    onPlayFirst = playFirst,
                                )
                            }
                        }

                        metadata.description?.let { description ->
                            detailItem("synopsis", DetailGutter) {
                                OverviewSection(overview = description)
                            }
                        }

                        if (uiState.seriesDetails.isNotEmpty()) {
                            detailItem("series", DetailGutter) {
                                IncludedInSeriesSection(
                                    seriesList = uiState.seriesDetails,
                                    serverUrl = config?.serverUrl,
                                    onSeriesClick = { seriesId, seriesName ->
                                        onNavigateToSeries(
                                            seriesId,
                                            currentItem.libraryId,
                                            seriesName,
                                        )
                                    },
                                )
                            }
                        }

                        if (narrators.isNotEmpty()) {
                            detailItem("narrators", DetailGutter) {
                                NarratorsSection(narrators = narrators)
                            }
                        }

                        if (showNetworkSections && authorName != null) {
                            detailItem("author", DetailGutter) {
                                LaunchedEffect(Unit) { viewModel.ensureAuthor() }
                                AuthorCard(
                                    authorName = authorName,
                                    details = authorDetails,
                                    serverUrl = config?.serverUrl,
                                )
                            }
                        }

                        detailItem("details", DetailGutter) {
                            ItemDetailsSection(item = currentItem)
                        }

                        if (!isPodcast && uiState.chapters.isNotEmpty()) {
                            chapterWindowItems(
                                chapters = uiState.chapters,
                                currentPosition = progress?.currentTime,
                                showAll = showAllChapters,
                                onShowAllChange = { showAll ->
                                    showAllChapters = showAll
                                    if (!showAll) showListenedChapters = false
                                },
                                showListened = showListenedChapters,
                                onShowListenedChange = { showListenedChapters = it },
                                onChapterClick = { chapter ->
                                    onNavigateToPlayer(
                                        viewModel.itemId,
                                        null,
                                        chapter.start,
                                        null,
                                        false,
                                    )
                                },
                                headerTrailing = chaptersTrailing,
                            )
                        }

                        if (isPodcast && uiState.episodes.isNotEmpty()) {
                            item(key = "episodes_header") {
                                EpisodesHeader(
                                    count = sortedEpisodes.size,
                                    onSortClick = { showSortDialog = true },
                                )
                            }
                            episodeListItems(
                                episodes =
                                    if (showAllEpisodes) sortedEpisodes
                                    else sortedEpisodes.take(EPISODE_PREVIEW_COUNT),
                                onEpisodePlay = { episode ->
                                    playEpisode(
                                        episode,
                                        resumePositionOf(episode),
                                        episodeSort,
                                        false,
                                    )
                                },
                                expandedEpisodeId = expandedEpisodeId,
                                onExpandEpisode = { expandedEpisodeId = it },
                                episodeProgressMap = episodeProgressMap,
                                episodeDownloadMap = episodeDownloadMap,
                                onEpisodeDownload =
                                    if (canDownload) ({ viewModel.startDownload(it) }) else null,
                                onEpisodeCancelDownload = { viewModel.cancelDownload(it) },
                                onEpisodeDeleteDownload = { viewModel.deleteDownload(it) },
                                onEpisodeToggleFinished = { viewModel.toggleEpisodeFinished(it) },
                                onEpisodeMediaInfo = { mediaInfoEpisode = it },
                                nowPlayingEpisodeId = nowPlayingEpisodeId,
                                fadeLastItem =
                                    !showAllEpisodes && sortedEpisodes.size > EPISODE_PREVIEW_COUNT,
                            )
                            if (sortedEpisodes.size > EPISODE_PREVIEW_COUNT) {
                                item(key = "episodes_toggle") {
                                    ListExpandButton(
                                        expanded = showAllEpisodes,
                                        onToggle = { showAllEpisodes = !showAllEpisodes },
                                    )
                                }
                            }
                        }

                        if (showNetworkSections) {
                            detailItem("listening", DetailGutter) {
                                LaunchedEffect(Unit) { viewModel.ensureListening() }
                                listening?.let { summary -> ListeningSection(summary = summary) }
                            }
                            detailItem("recommendations", DetailGutter) {
                                LaunchedEffect(Unit) { viewModel.ensureRecommendations() }
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(DetailSectionGap)
                                ) {
                                    recommendationRows.forEach { row ->
                                        RecommendationRowSection(
                                            row = row,
                                            serverUrl = config?.serverUrl,
                                            cardWidth = widthSizeClass.portraitWidth,
                                            onItemClick = { onNavigateToItem(it.id) },
                                            onSeriesClick = { series ->
                                                onNavigateToSeries(
                                                    series.id,
                                                    currentItem.libraryId,
                                                    series.name,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val coverUrl =
                        if (
                            downloadInfo?.status == AbsDownloadStatus.COMPLETED &&
                                downloadInfo?.localDirPath != null
                        ) {
                            "file://${downloadInfo?.localDirPath}/cover.jpg"
                        } else if (
                            config?.serverUrl != null && currentItem.media.coverPath != null
                        ) {
                            currentItem.coverUrl(config?.serverUrl ?: "")
                        } else null

                    if (isLandscape) {
                        LandscapeDetailLayout(
                            backdropStyle = BackdropStyle.BlurredCover,
                            backdrop = backdropChainOf(coverUrl?.withAbsWidth(600)),
                            listState = lazyListState,
                            arrangement = LandscapeDetailArrangement.Split,
                            pane = {
                                ItemHeaderContent(
                                    item = currentItem,
                                    progress = progress,
                                    coverUrl = coverUrl,
                                    onPlay = onPlay,
                                    downloadInfo = if (!isPodcast) downloadInfo else null,
                                    onDownload =
                                        if (!isPodcast && canDownload)
                                            ({ viewModel.startDownload() })
                                        else null,
                                    onCancelDownload =
                                        if (!isPodcast) ({ viewModel.cancelDownload() }) else null,
                                    onDeleteDownload =
                                        if (!isPodcast) ({ viewModel.deleteDownload() }) else null,
                                    audibleRating = if (!isPodcast) audibleRating else null,
                                    onToggleFinished =
                                        if (!isPodcast) ({ viewModel.toggleItemFinished() })
                                        else null,
                                    toggleFinishedEnabled = !isThisItemPlaying,
                                    showActions = false,
                                )
                            },
                            items = {
                                val headerOnDownload =
                                    if (!isPodcast && canDownload) ({ viewModel.startDownload() })
                                    else null
                                val headerOnToggleFinished =
                                    if (!isPodcast) ({ viewModel.toggleItemFinished() }) else null
                                if (
                                    hasItemActions(
                                        currentItem,
                                        onPlay,
                                        headerOnToggleFinished,
                                        headerOnDownload,
                                    )
                                ) {
                                    item(key = "actions") {
                                        ItemActionRow(
                                            item = currentItem,
                                            progress = progress,
                                            coverUrl = coverUrl,
                                            onPlay = onPlay,
                                            modifier = Modifier.padding(horizontal = DetailGutter),
                                            downloadInfo = if (!isPodcast) downloadInfo else null,
                                            onDownload = headerOnDownload,
                                            onCancelDownload =
                                                if (!isPodcast) ({ viewModel.cancelDownload() })
                                                else null,
                                            onDeleteDownload =
                                                if (!isPodcast) ({ viewModel.deleteDownload() })
                                                else null,
                                            onToggleFinished = headerOnToggleFinished,
                                            toggleFinishedEnabled = !isThisItemPlaying,
                                        )
                                    }
                                }
                                detailBody()
                            },
                        )
                    } else {
                        PortraitDetailLayout(
                            hero = PortraitHero.Cover(backdropChainOf(coverUrl?.withAbsWidth(600))),
                            listState = lazyListState,
                            header = {
                                ItemHeaderContent(
                                    item = currentItem,
                                    progress = progress,
                                    coverUrl = coverUrl,
                                    onPlay = onPlay,
                                    downloadInfo = if (!isPodcast) downloadInfo else null,
                                    onDownload =
                                        if (!isPodcast && canDownload)
                                            ({ viewModel.startDownload() })
                                        else null,
                                    onCancelDownload =
                                        if (!isPodcast) ({ viewModel.cancelDownload() }) else null,
                                    onDeleteDownload =
                                        if (!isPodcast) ({ viewModel.deleteDownload() }) else null,
                                    audibleRating = if (!isPodcast) audibleRating else null,
                                    onToggleFinished =
                                        if (!isPodcast) ({ viewModel.toggleItemFinished() })
                                        else null,
                                    toggleFinishedEnabled = !isThisItemPlaying,
                                )
                            },
                        ) {
                            detailBody()
                        }
                    }
                }

                uiState.error != null -> {
                    Text(
                        text = stringResource(R.string.abs_error_load_item),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(16.dp)
                            .padding(WindowInsets.navigationBars.asPaddingValues()),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                ) {
                    Text(
                        text = uiState.error ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }

    if (showSortDialog) {
        EpisodeSortDialog(
            current = episodeSort,
            onDismiss = { showSortDialog = false },
            onSortSelected = { sort ->
                viewModel.setEpisodeSort(sort)
                showSortDialog = false
            },
        )
    }
}

@Composable
private fun EpisodesHeader(count: Int, onSortClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbsSectionTitle(
            text = stringResource(R.string.season_episodes_title),
            trailing = count.toString(),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSortClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrows_sort),
                contentDescription = stringResource(R.string.cd_abs_sort),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeSortDialog(
    current: EpisodeSort,
    onDismiss: () -> Unit,
    onSortSelected: (EpisodeSort) -> Unit,
) {
    var isAscending by remember { mutableStateOf(current.ascending) }
    var selectedSort by remember { mutableStateOf(current.key) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.abs_sort_episodes_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isAscending,
                        onClick = { isAscending = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.sort_ascending))
                    }

                    SegmentedButton(
                        selected = !isAscending,
                        onClick = { isAscending = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.sort_descending))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EpisodeSortKey.entries.forEach { option ->
                        RadioOptionRow(
                            label = stringResource(option.labelRes),
                            selected = selectedSort == option,
                            onClick = { selectedSort = option },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSortSelected(EpisodeSort(selectedSort, isAscending)) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
