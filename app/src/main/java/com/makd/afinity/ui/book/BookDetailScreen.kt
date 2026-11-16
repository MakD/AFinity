package com.makd.afinity.ui.book

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.BookFormat
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.components.DetailActionRow
import com.makd.afinity.ui.components.DetailCoverImage
import com.makd.afinity.ui.components.DetailCoverTitle
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.DownloadConfirm
import com.makd.afinity.ui.components.DownloadConfirmDialog
import com.makd.afinity.ui.components.FavoriteToggleButton
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.WatchedToggleButton
import com.makd.afinity.ui.components.backdropChainOf
import com.makd.afinity.ui.components.formatTimeLeftSeconds
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.components.matches
import com.makd.afinity.ui.components.rememberDownloadVolumes
import com.makd.afinity.ui.components.singleDownloadConfirm
import com.makd.afinity.ui.components.windowHeightDp
import com.makd.afinity.ui.item.components.DownloadProgressIndicator
import com.makd.afinity.ui.item.components.StorageLocationDialog
import com.makd.afinity.ui.item.components.shared.CreditsSection
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle
import com.makd.afinity.ui.item.components.shared.OverviewSection
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import com.makd.afinity.ui.theme.isDarkSurface
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlin.math.roundToInt
import org.jellyfin.sdk.model.api.PersonKind

private val PortraitCoverWidth = 200.dp
private val PortraitCoverHeight = 300.dp
private val LandscapeCoverMinHeight = 140.dp
private const val LandscapeCoverHeightFraction = 0.5f
private const val CoverAspect = 2f / 3f

@Composable
fun BookDetailScreen(
    onHomeClick: () -> Unit,
    onPersonClick: (UUID) -> Unit,
    onBookClick: (AfinityBook) -> Unit,
    onReadClick: (AfinityBook) -> Unit,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val secondsLeft by viewModel.secondsLeft.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)
    val isLandscape = isLandscapeWindow()

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
                val body: LazyListScope.() -> Unit = {
                    bookDetailBody(
                        book = book,
                        seriesBooks = uiState.seriesBooks,
                        seriesStartIndex = uiState.seriesStartIndex,
                        onBookClick = onBookClick,
                        onPersonClick = if (isOffline) null else onPersonClick,
                        widthSizeClass = widthSizeClass,
                    )
                }
                if (isLandscape) {
                    val coverHeight =
                        (windowHeightDp() * LandscapeCoverHeightFraction).coerceIn(
                            LandscapeCoverMinHeight,
                            PortraitCoverHeight,
                        )
                    LandscapeDetailLayout(
                        backdropStyle = BackdropStyle.BlurredCover,
                        backdrop = backdrop,
                        listState = lazyListState,
                        arrangement = LandscapeDetailArrangement.Split,
                        pane = {
                            BookHeader(
                                book = book,
                                coverWidth = coverHeight * CoverAspect,
                                coverHeight = coverHeight,
                                secondsLeft = secondsLeft,
                            )
                        },
                        items = {
                            item(key = "actions") {
                                BookActionRow(
                                    book = book,
                                    onRead = { onReadClick(book) },
                                    onToggleRead = viewModel::toggleRead,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    modifier = Modifier.padding(horizontal = DetailGutter),
                                )
                            }
                            body()
                        },
                    )
                } else {
                    PortraitDetailLayout(
                        hero = PortraitHero.Cover(backdrop),
                        listState = lazyListState,
                        header = {
                            BookHeader(
                                book = book,
                                coverWidth = PortraitCoverWidth,
                                coverHeight = PortraitCoverHeight,
                                secondsLeft = secondsLeft,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            BookActionRow(
                                book = book,
                                onRead = { onReadClick(book) },
                                onToggleRead = viewModel::toggleRead,
                                onToggleFavorite = viewModel::toggleFavorite,
                            )
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
private fun BookHeader(book: AfinityBook, coverWidth: Dp, coverHeight: Dp, secondsLeft: Double?) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DetailCoverImage(
            imageUrl = book.images.primary?.toString(),
            contentDescription = stringResource(R.string.cd_book_cover),
            blurHash = book.images.primaryImageBlurHash,
            width = coverWidth,
            height = coverHeight,
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

        seriesLine(book)?.let { series ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = series,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        BookMetaLine(book, secondsLeft)

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
private fun seriesLine(book: AfinityBook): String? =
    seriesLine(book.seriesName, book.seriesIndex, book.format)

@Composable
internal fun seriesLine(seriesName: String?, seriesIndex: Int?, format: BookFormat): String? {
    val series = seriesName ?: return null
    val index = seriesIndex ?: return series
    return if (format == BookFormat.COMIC) {
        stringResource(R.string.book_series_issue_fmt, series, index)
    } else {
        stringResource(R.string.book_series_book_fmt, series, index)
    }
}

@Composable
private fun formatLabel(format: BookFormat): String? =
    when (format) {
        BookFormat.EPUB -> stringResource(R.string.book_format_epub)
        BookFormat.PDF -> stringResource(R.string.book_format_pdf)
        BookFormat.COMIC -> stringResource(R.string.book_format_comic)
        BookFormat.KINDLE -> stringResource(R.string.book_format_kindle)
        BookFormat.UNKNOWN -> null
    }

@Composable
private fun BookMetaLine(book: AfinityBook, secondsLeft: Double?) {
    val parts =
        listOfNotNull(
            formatLabel(book.format),
            book.pageCount?.let { pluralStringResource(R.plurals.book_pages_fmt, it, it) }
                ?: book.productionYear?.toString(),
        )
    val progress = book.readingProgress
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
                    text = stringResource(R.string.book_progress_read),
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
                    text = progressText(book, progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold,
                )
                if (secondsLeft != null) {
                    MetaSeparator(show = true)
                    Text(
                        text = formatTimeLeftSeconds(secondsLeft),
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
private fun progressText(book: AfinityBook, progress: Float): String {
    val pages = book.pageCount
    return if (pages != null) {
        val page =
            (book.playbackPositionTicks / AfinityBook.TICKS_PER_PAGE)
                .toInt()
                .plus(1)
                .coerceIn(
                    1,
                    pages,
                )
        stringResource(R.string.book_progress_page_fmt, page, pages)
    } else {
        val percent = (progress * 100).roundToInt().coerceIn(1, 100)
        stringResource(R.string.book_progress_percent_fmt, percent)
    }
}

@Composable
private fun BookActionRow(
    book: AfinityBook,
    onRead: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookDetailViewModel = hiltViewModel(),
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
                if (downloadInfo?.matches(confirm) == true) viewModel.cancelOrDeleteDownload()
            },
            onDismiss = { pendingConfirm = null },
        )
    }

    val readable = book.format != BookFormat.KINDLE
    DetailActionRow(
        onPlay = onRead,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        playbackEnabled = readable,
        playIconRes = R.drawable.ic_book,
        playContentDescription = stringResource(R.string.cd_read_book),
    ) {
        if (!isOffline) {
            WatchedToggleButton(isPlayed = book.played, onClick = onToggleRead)
            FavoriteToggleButton(isFavorite = book.favorite, onClick = onToggleFavorite)
        }
        if (readable && (book.canDownload && allowedByServer || downloadInfo != null)) {
            DownloadProgressIndicator(
                downloadInfo = downloadInfo,
                onDownloadClick = { viewModel.startDownload() },
                onPauseClick = viewModel::pauseDownload,
                onResumeClick = viewModel::resumeDownload,
                onCancelClick = { pendingConfirm = downloadInfo?.singleDownloadConfirm(book.name) },
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

private fun LazyListScope.bookDetailBody(
    book: AfinityBook,
    seriesBooks: List<AfinityBook>,
    seriesStartIndex: Int,
    onBookClick: (AfinityBook) -> Unit,
    onPersonClick: ((UUID) -> Unit)?,
    widthSizeClass: WindowWidthSizeClass,
) {
    val overview = plainOverview(book.overview)
    if (overview.isNotEmpty()) {
        detailItem("overview", DetailGutter) { OverviewSection(overview = overview) }
    }

    val seriesName = book.seriesName
    if (seriesName != null && seriesBooks.isNotEmpty()) {
        detailItem("series", DetailGutter) {
            SeriesSection(
                seriesName = seriesName,
                books = seriesBooks,
                startIndex = seriesStartIndex,
                onBookClick = onBookClick,
                widthSizeClass = widthSizeClass,
            )
        }
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

    detailItem("details", DetailGutter) { BookDetailsSection(book) }
}

@Composable
private fun SeriesSection(
    seriesName: String,
    books: List<AfinityBook>,
    startIndex: Int,
    onBookClick: (AfinityBook) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val cardWidth = widthSizeClass.portraitWidth
    val rowHeight =
        CardDimensions.rowHeight(
            cardWidth,
            CardDimensions.ASPECT_RATIO_PORTRAIT,
            titleHeight = CardDimensions.titleLines(2),
        )
    val rowState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = startIndex.coerceIn(0, books.lastIndex),
        )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionTitle(text = stringResource(R.string.book_more_in_series_fmt, seriesName))
        CardRow(modifier = Modifier.height(rowHeight), state = rowState) {
            items(books, key = { it.id }) { item ->
                MediaItemCard(item = item, onClick = { onBookClick(item) }, cardWidth = cardWidth)
            }
        }
    }
}

internal fun plainOverview(raw: String): String {
    if (raw.isBlank()) return ""
    if ('<' !in raw) return raw.trim()
    return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT)
        .toString()
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

@Composable
private fun personKindLabel(person: AfinityPerson): String =
    when (person.type) {
        PersonKind.AUTHOR -> stringResource(R.string.person_kind_author)
        PersonKind.WRITER -> stringResource(R.string.person_kind_writer)
        PersonKind.ILLUSTRATOR -> stringResource(R.string.person_kind_illustrator)
        PersonKind.PENCILLER -> stringResource(R.string.person_kind_penciller)
        PersonKind.INKER -> stringResource(R.string.person_kind_inker)
        PersonKind.COLORIST -> stringResource(R.string.person_kind_colorist)
        PersonKind.LETTERER -> stringResource(R.string.person_kind_letterer)
        PersonKind.COVER_ARTIST -> stringResource(R.string.person_kind_cover_artist)
        PersonKind.EDITOR -> stringResource(R.string.person_kind_editor)
        PersonKind.TRANSLATOR -> stringResource(R.string.person_kind_translator)
        else -> person.role
    }

@Composable
private fun BookDetailsSection(book: AfinityBook) {
    val releaseFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
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
            formatLabel(book.format)?.let { stringResource(R.string.book_details_format) to it },
            book.pageCount?.let { stringResource(R.string.book_details_pages) to it.toString() },
        )
    if (rows.isEmpty() && book.tags.isEmpty()) return

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
                            ),
                        ) {
                            append(value)
                        }
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (book.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                book.tags.forEach { tag ->
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
