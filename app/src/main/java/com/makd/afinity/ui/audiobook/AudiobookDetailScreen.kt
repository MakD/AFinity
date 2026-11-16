package com.makd.afinity.ui.audiobook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.timeline
import com.makd.afinity.ui.audiobookshelf.item.components.chapterWindowItems
import com.makd.afinity.ui.book.plainOverview
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.DetailActionRow
import com.makd.afinity.ui.components.DetailCoverImage
import com.makd.afinity.ui.components.DetailCoverSize
import com.makd.afinity.ui.components.DetailCoverTitle
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.DownloadConfirm
import com.makd.afinity.ui.components.DownloadConfirmDialog
import com.makd.afinity.ui.components.FavoriteToggleButton
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.WatchedToggleButton
import com.makd.afinity.ui.components.backdropChainOf
import com.makd.afinity.ui.components.formatRuntimeSeconds
import com.makd.afinity.ui.components.formatRuntimeTicks
import com.makd.afinity.ui.components.formatTimeLeftTicks
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.components.rememberDownloadVolumes
import com.makd.afinity.ui.components.windowHeightDp
import com.makd.afinity.ui.item.components.DownloadProgressIndicator
import com.makd.afinity.ui.item.components.StorageLocationDialog
import com.makd.afinity.ui.item.components.shared.CreditsSection
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle
import com.makd.afinity.ui.item.components.shared.OverviewSection
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.theme.isDarkSurface
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import org.jellyfin.sdk.model.api.PersonKind

private val LandscapeCoverMinHeight = 140.dp
private const val LandscapeCoverHeightFraction = 0.5f

@Composable
fun AudiobookDetailScreen(
    onHomeClick: () -> Unit,
    onPersonClick: (UUID) -> Unit,
    onPlayClick: (AfinityAudiobook, Double?) -> Unit,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: AudiobookDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)
    val isLandscape = isLandscapeWindow()
    var showAllChapters by rememberSaveable { mutableStateOf(false) }
    var showListenedChapters by rememberSaveable { mutableStateOf(false) }

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = {},
                onHomeClick = onHomeClick,
                backgroundOpacity = backgroundOpacity,
            )
        },
        mode = AfinityScreenMode.Hero,
        scrollOpacity = { topBarOpacity },
    ) { _ ->
        val book = uiState.book
        when {
            book != null -> {
                val coverUrl = book.images.primary?.toString()
                val backdrop = backdropChainOf(coverUrl, book.images.primaryImageBlurHash)
                val chapters = rememberTimeline(book)
                val totalChapterLength =
                    chapters.lastOrNull()?.end?.takeIf { it > 0 }?.let { formatRuntimeSeconds(it) }
                val chaptersTrailing =
                    listOfNotNull(chapters.size.toString(), totalChapterLength).joinToString(" · ")
                val actions: @Composable (Modifier) -> Unit = { modifier ->
                    AudiobookActionRow(
                        book = book,
                        onPlay = { onPlayClick(book, null) },
                        onTogglePlayed = viewModel::togglePlayed,
                        onToggleFavorite = viewModel::toggleFavorite,
                        modifier = modifier,
                    )
                }
                val body: LazyListScope.() -> Unit = {
                    audiobookDetailBody(
                        book = book,
                        chapters = chapters,
                        chaptersTrailing = chaptersTrailing,
                        showAllChapters = showAllChapters,
                        onShowAllChange = { showAll ->
                            showAllChapters = showAll
                            if (!showAll) showListenedChapters = false
                        },
                        showListenedChapters = showListenedChapters,
                        onShowListenedChange = { showListenedChapters = it },
                        onChapterClick = { chapter -> onPlayClick(book, chapter.start) },
                        onPersonClick = if (isOffline) null else onPersonClick,
                        widthSizeClass = widthSizeClass,
                    )
                }
                if (isLandscape) {
                    val coverSize =
                        (windowHeightDp() * LandscapeCoverHeightFraction).coerceIn(
                            LandscapeCoverMinHeight,
                            DetailCoverSize,
                        )
                    LandscapeDetailLayout(
                        backdropStyle = BackdropStyle.BlurredCover,
                        backdrop = backdrop,
                        listState = lazyListState,
                        arrangement = LandscapeDetailArrangement.Split,
                        pane = { AudiobookHeader(book = book, coverSize = coverSize) },
                        items = {
                            item(key = "actions") {
                                actions(Modifier.padding(horizontal = DetailGutter))
                            }
                            body()
                        },
                    )
                } else {
                    PortraitDetailLayout(
                        hero = PortraitHero.Cover(backdrop),
                        listState = lazyListState,
                        header = {
                            AudiobookHeader(book = book, coverSize = DetailCoverSize)
                            Spacer(modifier = Modifier.height(16.dp))
                            actions(Modifier)
                        },
                    ) {
                        body()
                    }
                }
            }
            uiState.isLoading -> FullScreenLoading()
            else ->
                FullScreenError(
                    message = uiState.errorRes?.let { stringResource(it) },
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = viewModel::retry,
                )
        }
    }
}

@Composable
private fun rememberTimeline(book: AfinityAudiobook): List<BookChapter> {
    val resources = LocalResources.current
    return remember(book.id, book.parts, book.chapters, book.runtimeTicks, resources) {
        book.timeline().map { chapter ->
            if (chapter.title.isBlank()) {
                chapter.copy(
                    title = resources.getString(R.string.chapter_number_fmt, chapter.id + 1)
                )
            } else {
                chapter
            }
        }
    }
}

@Composable
private fun AudiobookHeader(book: AfinityAudiobook, coverSize: Dp) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DetailCoverImage(
            imageUrl = book.images.primary?.toString(),
            contentDescription = stringResource(R.string.cd_audiobook_cover),
            blurHash = book.images.primaryImageBlurHash,
            width = coverSize,
            height = coverSize,
        )

        DetailCoverTitle(
            title = book.name.ifBlank { stringResource(R.string.unknown_title) },
            subtitle =
                book.authors
                    .map { it.name }
                    .distinct()
                    .takeIf { it.isNotEmpty() }
                    ?.let { stringResource(R.string.abs_by_author_fmt, it.joinToString(", ")) },
        )

        val narrators = book.narrators.map { it.name }.distinct()
        if (narrators.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.abs_narrated_by_fmt, narrators.joinToString(", ")),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        AudiobookMetaLine(book)

        val genres = book.genres.take(2)
        if (genres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                genres.forEach { genre ->
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
    }
}

@Composable
private fun AudiobookMetaLine(book: AfinityAudiobook) {
    val parts =
        listOfNotNull(
            book.runtimeTicks.takeIf { it > 0 }?.let { formatRuntimeTicks(it) },
            book.productionYear?.toString(),
        )
    val progress = book.listeningProgress
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            book.played -> {
                MetaSeparator(show = parts.isNotEmpty())
                val finishedColor = if (isDarkSurface()) Color(0xFF81C784) else Color(0xFF1B6E22)
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    tint = finishedColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.abs_finished),
                    style = MaterialTheme.typography.labelMedium,
                    color = finishedColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            progress != null && book.playbackPositionTicks > 0 -> {
                MetaSeparator(show = parts.isNotEmpty())
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(12.dp),
                    color = Color(0xFFFFC107),
                    strokeWidth = 2.dp,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text =
                        stringResource(
                            R.string.audiobook_progress_percent_fmt,
                            (progress * 100).roundToInt().coerceIn(1, 100),
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold,
                )
                val ticksLeft = book.runtimeTicks - book.playbackPositionTicks
                if (ticksLeft > 0) {
                    MetaSeparator(show = true)
                    Text(
                        text = formatTimeLeftTicks(ticksLeft),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaSeparator(show: Boolean) {
    if (!show) return
    Text(
        text = " · ",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AudiobookActionRow(
    book: AfinityAudiobook,
    onPlay: () -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudiobookDetailViewModel = hiltViewModel(),
) {
    val downloadInfo by viewModel.downloadInfo.collectAsStateWithLifecycle()
    val allowedByServer by viewModel.isDownloadAllowedByServer.collectAsStateWithLifecycle()
    val allowedOnNetwork by viewModel.canDownloadOnNetwork.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val downloadVolumes = rememberDownloadVolumes()
    var pendingConfirm by remember { mutableStateOf<DownloadConfirm?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var selectedVolumeId by remember { mutableStateOf<String?>(null) }

    if (showLocationDialog) {
        StorageLocationDialog(
            volumes = downloadVolumes.volumes,
            selectedVolumeId = selectedVolumeId,
            onVolumeSelected = { selectedVolumeId = it },
            onConfirm = { viewModel.startDownload(selectedVolumeId) },
            onDismiss = { showLocationDialog = false },
            showQuality = false,
        )
    }

    pendingConfirm?.let { confirm ->
        DownloadConfirmDialog(
            confirm = confirm,
            onConfirm = {
                pendingConfirm = null
                viewModel.cancelOrDeleteDownload()
            },
            onDismiss = { pendingConfirm = null },
        )
    }

    DetailActionRow(
        onPlay = onPlay,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        playbackEnabled = book.canPlay,
        playContentDescription = stringResource(R.string.cd_play),
    ) {
        if (!isOffline) {
            WatchedToggleButton(isPlayed = book.played, onClick = onTogglePlayed)
            FavoriteToggleButton(isFavorite = book.favorite, onClick = onToggleFavorite)
        }
        if (book.canDownload && allowedByServer || downloadInfo != null) {
            DownloadProgressIndicator(
                downloadInfo = downloadInfo,
                onDownloadClick = { viewModel.startDownload() },
                onPauseClick = viewModel::pauseDownload,
                onResumeClick = viewModel::resumeDownload,
                onCancelClick = { pendingConfirm = viewModel.downloadConfirm(book.name) },
                canDownload = allowedOnNetwork,
                onDownloadLongClick = {
                    if (downloadVolumes.hasChoice) {
                        selectedVolumeId = downloadVolumes.defaultSelection
                        showLocationDialog = true
                    } else {
                        viewModel.startDownload()
                    }
                },
            )
        }
    }
}

private fun LazyListScope.audiobookDetailBody(
    book: AfinityAudiobook,
    chapters: List<BookChapter>,
    chaptersTrailing: String,
    showAllChapters: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    showListenedChapters: Boolean,
    onShowListenedChange: (Boolean) -> Unit,
    onChapterClick: (BookChapter) -> Unit,
    onPersonClick: ((UUID) -> Unit)?,
    widthSizeClass: WindowWidthSizeClass,
) {
    val overview = plainOverview(book.overview)
    if (overview.isNotEmpty()) {
        detailItem("overview", DetailGutter) { OverviewSection(overview = overview) }
    }

    if (chapters.isNotEmpty()) {
        chapterWindowItems(
            chapters = chapters,
            currentPosition = book.positionSeconds(),
            showAll = showAllChapters,
            onShowAllChange = onShowAllChange,
            showListened = showListenedChapters,
            onShowListenedChange = onShowListenedChange,
            onChapterClick = onChapterClick,
            headerTrailing = chaptersTrailing,
        )
    }

    val credits = book.people.distinctBy { it.id to it.type }
    if (credits.isNotEmpty()) {
        detailItem("credits", DetailGutter) {
            CreditsSection(
                title = stringResource(R.string.book_credits_title),
                people = credits,
                onPersonClick = onPersonClick,
                widthSizeClass = widthSizeClass,
                roleText = { person -> personKindLabel(person) },
            )
        }
    }

    detailItem("details", DetailGutter) { AudiobookDetailsSection(book) }
}

@Composable
private fun personKindLabel(person: AfinityPerson): String =
    when (person.type) {
        PersonKind.AUTHOR -> stringResource(R.string.person_kind_author)
        PersonKind.NARRATOR -> stringResource(R.string.person_kind_narrator)
        else -> person.role
    }

@Composable
private fun partsLabel(book: AfinityAudiobook): String? {
    val available = book.playableParts.size
    val total = book.bookPartCount
    return when {
        total != null && total > available ->
            stringResource(R.string.audiobook_parts_downloaded_fmt, available, total)
        book.isGrouped -> available.toString()
        else -> null
    }
}

@Composable
private fun AudiobookDetailsSection(book: AfinityAudiobook) {
    val releaseFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    val containers =
        book.playableParts
            .mapNotNull { it.container?.substringBefore(',')?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .joinToString(", ") { it.uppercase(Locale.ROOT) }
    val rows =
        listOfNotNull(
            book.publisher
                ?.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.abs_publisher) to it },
            book.premiereDate
                ?.takeIf { it.year > 1 }
                ?.let {
                    stringResource(R.string.book_details_released) to it.format(releaseFormatter)
                },
            book.runtimeTicks
                .takeIf { it > 0 }
                ?.let {
                    stringResource(R.string.audiobook_details_length) to formatRuntimeTicks(it)
                },
            partsLabel(book)?.let { stringResource(R.string.audiobook_details_parts) to it },
            containers
                .takeIf { it.isNotEmpty() }
                ?.let { stringResource(R.string.book_details_format) to it },
        )
    if (rows.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        DetailSectionTitle(text = stringResource(R.string.abs_details))
        Spacer(modifier = Modifier.height(12.dp))
        rows.forEach { (label, value) ->
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("$label: ")
                        }
                        withStyle(
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
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
