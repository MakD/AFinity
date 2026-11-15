package com.makd.afinity.ui.reader

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.SnapSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.ui.book.seriesLine
import com.makd.afinity.ui.components.formatRuntimeSeconds
import com.makd.afinity.ui.player.utils.PlayerSystemBarsController
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

internal val ReaderTopInsets: WindowInsets
    @Composable
    get() =
        WindowInsets.statusBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

internal val ReaderBottomInsets: WindowInsets
    @Composable
    get() =
        WindowInsets.navigationBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

internal const val EDGE_TAP_FRACTION = 0.15f
private const val MAX_ZOOM = 4f
private val FilmstripThumbWidth = 52.dp
private val FilmstripThumbHeight = 78.dp
private val FilmstripHeight = 102.dp
private val PreviewWidth = 104.dp
private val PreviewHeight = 156.dp
private const val END_CARD_KEY = "up-next"
private const val PLACEHOLDER_ASPECT = 2f / 3f

private sealed interface PageLoad {
    data object Loading : PageLoad

    data class Loaded(val file: File) : PageLoad

    data object Failed : PageLoad
}

@Composable
fun PageReaderScreen(
    onBack: () -> Unit,
    onOpenBook: (AfinityBook) -> Unit,
    viewModel: PageReaderViewModel = hiltViewModel(),
) {
    val nextBook by viewModel.nextBook.collectAsStateWithLifecycle()
    val endCard: (@Composable () -> Unit)? = nextBook?.let { next ->
        { ReaderChromeTheme { UpNextPage(next = next, onRead = { onOpenBook(next) }) } }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarkedPages by viewModel.bookmarkedPages.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val canSearch by viewModel.canSearch.collectAsStateWithLifecycle()
    val millisPerPage by viewModel.millisPerPage.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var gridOpen by rememberSaveable { mutableStateOf(false) }
    var seekRequest by remember { mutableStateOf<Int?>(null) }
    val goToHit: (Int) -> Unit = { index ->
        viewModel.selectSearchHit(index)?.let { seekRequest = it }
        searchOpen = false
        chromeVisible = false
    }

    PlayerSystemBarsController(isControlsVisible = chromeVisible)
    KeepScreenOn()
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val load = uiState.load) {
            is ReaderLoadState.Downloading -> {
                val context = LocalContext.current
                val total = load.totalBytes
                ReaderStatus(
                    text =
                        if (total != null) {
                            stringResource(
                                R.string.reader_downloading_fmt,
                                Formatter.formatShortFileSize(context, load.downloadedBytes),
                                Formatter.formatShortFileSize(context, total),
                            )
                        } else {
                            stringResource(R.string.reader_downloading)
                        },
                    progress =
                        total?.let { (load.downloadedBytes.toFloat() / it).coerceIn(0f, 1f) },
                )
                ReaderBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
            }
            ReaderLoadState.Preparing -> {
                ReaderStatus(text = stringResource(R.string.reader_preparing), progress = null)
                ReaderBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
            }
            is ReaderLoadState.Failed -> {
                ReaderFailure(
                    error = load.error,
                    onRetry = viewModel::retry,
                    onBack = onBack,
                )
                ReaderBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
            }
            is ReaderLoadState.Ready -> {
                val onTapCenter = { chromeVisible = !chromeVisible }
                if (uiState.verticalScroll) {
                    ScrollReader(
                        pageCount = load.pageCount,
                        startPage = uiState.currentPage,
                        seekRequest = seekRequest,
                        onSeekHandled = { seekRequest = null },
                        onPageChanged = viewModel::onPageChanged,
                        onTap = onTapCenter,
                        loadPage = viewModel::pageFile,
                        endCard = endCard,
                    )
                } else {
                    PagedReader(
                        pageCount = load.pageCount,
                        startPage = uiState.currentPage,
                        rightToLeft = uiState.rightToLeft,
                        seekRequest = seekRequest,
                        onSeekHandled = { seekRequest = null },
                        onPageChanged = viewModel::onPageChanged,
                        onTapCenter = onTapCenter,
                        loadPage = viewModel::pageFile,
                        endCard = endCard,
                    )
                }
                ReaderChrome(
                    visible = chromeVisible,
                    title = uiState.title,
                    subtitle = seriesLine(uiState.seriesName, uiState.seriesIndex, uiState.format),
                    currentPage = uiState.currentPage,
                    pageCount = load.pageCount,
                    verticalScroll = uiState.verticalScroll,
                    rightToLeft = uiState.rightToLeft,
                    onBack = onBack,
                    onSeek = { seekRequest = it },
                    onVerticalScrollChange = viewModel::setVerticalScroll,
                    onRightToLeftChange = viewModel::setRightToLeft,
                    loadThumbnail = viewModel::thumbnailFile,
                    bookmarkedPages = bookmarkedPages,
                    onToggleBookmark = viewModel::toggleBookmark,
                    onSearchClick = if (canSearch) ({ searchOpen = true }) else null,
                    millisPerPage = millisPerPage,
                    onOpenPages = { gridOpen = true },
                    onNextClick = nextBook?.let { next -> { onOpenBook(next) } },
                )
                if (gridOpen) {
                    PageGridSheet(
                        pageCount = load.pageCount,
                        currentPage = uiState.currentPage,
                        bookmarkedPages = bookmarkedPages,
                        loadThumbnail = viewModel::thumbnailFile,
                        onPageClick = { page ->
                            seekRequest = page
                            gridOpen = false
                            chromeVisible = false
                        },
                        onDismiss = { gridOpen = false },
                    )
                }
                ReaderChromeTheme {
                    ReaderSearchResultBar(
                        visible = search.barVisible && !chromeVisible,
                        state = search,
                        onPrevious = { goToHit((search.selected ?: 0) - 1) },
                        onNext = { goToHit((search.selected ?: 0) + 1) },
                        onOpen = { searchOpen = true },
                        onClose = viewModel::closeSearchBar,
                    )
                }
                if (searchOpen) {
                    ReaderSearchSheet(
                        state = search,
                        onSearch = viewModel::search,
                        onHitClick = goToHit,
                        onDismiss = { searchOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun PagedReader(
    pageCount: Int,
    startPage: Int,
    rightToLeft: Boolean,
    seekRequest: Int?,
    onSeekHandled: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onTapCenter: () -> Unit,
    loadPage: suspend (Int) -> File?,
    endCard: (@Composable () -> Unit)?,
) {
    val totalPages = pageCount + if (endCard != null) 1 else 0
    val pagerState = rememberPagerState(initialPage = startPage) { totalPages }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onPageChanged(it) }
    }
    LaunchedEffect(seekRequest) {
        val target = seekRequest ?: return@LaunchedEffect
        pagerState.scrollToPage(target.coerceIn(0, pageCount - 1))
        onSeekHandled()
    }

    val onTap: (Float) -> Unit = { x ->
        val width = widthPx.toFloat()
        val edge = width * EDGE_TAP_FRACTION
        val step =
            when {
                width <= 0f -> 0
                x < edge -> if (rightToLeft) 1 else -1
                x > width - edge -> if (rightToLeft) -1 else 1
                else -> 0
            }
        if (step == 0) {
            onTapCenter()
        } else {
            val target = (pagerState.currentPage + step).coerceIn(0, totalPages - 1)
            scope.launch { pagerState.animateScrollToPage(target) }
        }
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = rightToLeft,
        beyondViewportPageCount = 1,
        key = { it },
        modifier = Modifier.fillMaxSize().onSizeChanged { widthPx = it.width },
    ) { page ->
        if (page >= pageCount && endCard != null) {
            endCard()
        } else {
            ZoomablePage(page = page, pagerState = pagerState, loadPage = loadPage, onTap = onTap)
        }
    }
}

@Composable
private fun ZoomablePage(
    page: Int,
    pagerState: PagerState,
    loadPage: suspend (Int) -> File?,
    onTap: (Float) -> Unit,
) {
    val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = MAX_ZOOM))
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != page) zoomableState.resetZoom(animationSpec = SnapSpec())
    }
    val pageLoad by
        produceState<PageLoad>(initialValue = PageLoad.Loading, page) {
            value = loadPage(page)?.let { PageLoad.Loaded(it) } ?: PageLoad.Failed
        }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = pageLoad) {
            PageLoad.Loading -> CircularProgressIndicator(color = Color.White)
            PageLoad.Failed -> PageFailed(onTap = onTap)
            is PageLoad.Loaded ->
                ZoomableAsyncImage(
                    model = state.file,
                    contentDescription = null,
                    state = rememberZoomableImageState(zoomableState),
                    contentScale = ContentScale.Fit,
                    onClick = { offset -> onTap(offset.x) },
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}

@Composable
private fun ScrollReader(
    pageCount: Int,
    startPage: Int,
    seekRequest: Int?,
    onSeekHandled: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    loadPage: suspend (Int) -> File?,
    endCard: (@Composable () -> Unit)?,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { onPageChanged(it) }
    }
    LaunchedEffect(seekRequest) {
        val target = seekRequest ?: return@LaunchedEffect
        listState.scrollToItem(target.coerceIn(0, pageCount - 1))
        onSeekHandled()
    }
    LazyColumn(
        state = listState,
        modifier =
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
    ) {
        items(count = pageCount, key = { it }) { page ->
            ScrollPage(page = page, loadPage = loadPage)
        }
        if (endCard != null) {
            item(key = END_CARD_KEY) { Box(modifier = Modifier.fillParentMaxSize()) { endCard() } }
        }
    }
}

@Composable
private fun ScrollPage(page: Int, loadPage: suspend (Int) -> File?) {
    val pageLoad by
        produceState<PageLoad>(initialValue = PageLoad.Loading, page) {
            value = loadPage(page)?.let { PageLoad.Loaded(it) } ?: PageLoad.Failed
        }
    when (val state = pageLoad) {
        PageLoad.Loading ->
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(PLACEHOLDER_ASPECT),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        PageLoad.Failed ->
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(PLACEHOLDER_ASPECT),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.reader_page_failed),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        is PageLoad.Loaded -> {
            val painter = rememberAsyncImagePainter(model = state.file)
            val painterState by painter.state.collectAsStateWithLifecycle()
            val ratio =
                (painterState as? AsyncImagePainter.State.Success)
                    ?.painter
                    ?.intrinsicSize
                    ?.takeIf { it.width > 0f && it.height > 0f }
                    ?.let { it.width / it.height } ?: PLACEHOLDER_ASPECT
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
            )
        }
    }
}

@Composable
private fun PageFailed(onTap: (Float) -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onTap = { offset -> onTap(offset.x) })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.reader_page_failed),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReaderChrome(
    visible: Boolean,
    title: String,
    subtitle: String?,
    currentPage: Int,
    pageCount: Int,
    verticalScroll: Boolean,
    rightToLeft: Boolean,
    onBack: () -> Unit,
    onSeek: (Int) -> Unit,
    onVerticalScrollChange: (Boolean) -> Unit,
    onRightToLeftChange: (Boolean) -> Unit,
    loadThumbnail: suspend (Int) -> File?,
    bookmarkedPages: Set<Int>,
    onToggleBookmark: () -> Unit,
    onSearchClick: (() -> Unit)?,
    millisPerPage: Double,
    onOpenPages: () -> Unit,
    onNextClick: (() -> Unit)?,
) {
    ReaderChromeTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ReaderTopBar(visible = visible, title = title, subtitle = subtitle, onBack = onBack) {
                if (onNextClick != null) ReaderNextButton(onClick = onNextClick)
                if (onSearchClick != null) ReaderSearchButton(onClick = onSearchClick)
                ReaderBookmarkButton(
                    bookmarked = currentPage in bookmarkedPages,
                    onClick = onToggleBookmark,
                )
            }
            ReaderBottomPanel(visible = visible) {
                val lastPage = (pageCount - 1).coerceAtLeast(0)
                val direction =
                    if (rightToLeft && !verticalScroll) LayoutDirection.Rtl
                    else LocalLayoutDirection.current
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    if (pageCount > 1) {
                        Filmstrip(
                            pageCount = pageCount,
                            currentPage = currentPage,
                            bookmarkedPages = bookmarkedPages,
                            onPageClick = onSeek,
                            loadThumbnail = loadThumbnail,
                        )
                        ReaderScrubber(
                            fraction = currentPage.toFloat() / lastPage,
                            onSeek = { onSeek((it * lastPage).roundToInt()) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { fraction ->
                            ScrubPreview(
                                page = (fraction * lastPage).roundToInt(),
                                loadThumbnail = loadThumbnail,
                            )
                        }
                    }
                }
                val left = lastPage - currentPage
                val pagesLeftText =
                    if (left > 0) {
                        pluralStringResource(R.plurals.reader_pages_left, left, left) +
                            " · " +
                            stringResource(
                                R.string.reader_about_fmt,
                                formatRuntimeSeconds(left * millisPerPage / 1000.0),
                            )
                    } else {
                        null
                    }
                ReaderProgressLabels(
                    start = {
                        Text(
                            text =
                                stringResource(
                                    R.string.reader_page_of_fmt,
                                    currentPage + 1,
                                    pageCount,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    end = pagesLeftText,
                )
                ReaderToolPill {
                    ReaderToolButton(
                        contentDescription = stringResource(R.string.reader_pages),
                        onClick = onOpenPages,
                    ) {
                        ReaderToolIcon(R.drawable.ic_layout_grid)
                    }
                    ReaderToolButton(
                        contentDescription = stringResource(R.string.reader_mode_paged),
                        selected = !verticalScroll,
                        onClick = { onVerticalScrollChange(false) },
                    ) {
                        ReaderToolIcon(R.drawable.ic_horizontal)
                    }
                    ReaderToolButton(
                        contentDescription = stringResource(R.string.reader_mode_scroll),
                        selected = verticalScroll,
                        onClick = { onVerticalScrollChange(true) },
                    ) {
                        ReaderToolIcon(R.drawable.ic_vertical)
                    }
                    ReaderToolButton(
                        contentDescription = stringResource(R.string.reader_direction_rtl),
                        selected = rightToLeft && !verticalScroll,
                        enabled = !verticalScroll,
                        onClick = { onRightToLeftChange(!rightToLeft) },
                    ) {
                        ReaderToolIcon(R.drawable.ic_switch_horizontal)
                    }
                }
            }
        }
    }
}

@Composable
private fun Filmstrip(
    pageCount: Int,
    currentPage: Int,
    bookmarkedPages: Set<Int>,
    onPageClick: (Int) -> Unit,
    loadThumbnail: suspend (Int) -> File?,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    val density = LocalDensity.current
    var stripWidth by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentPage, stripWidth) {
        if (stripWidth <= 0) return@LaunchedEffect
        val itemPx = with(density) { FilmstripThumbWidth.roundToPx() }
        listState.animateScrollToItem(currentPage, scrollOffset = -(stripWidth - itemPx) / 2)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier.fillMaxWidth().height(FilmstripHeight).onSizeChanged { stripWidth = it.width },
    ) {
        items(count = pageCount, key = { it }) { page ->
            FilmstripThumb(
                page = page,
                selected = page == currentPage,
                bookmarked = page in bookmarkedPages,
                onClick = { onPageClick(page) },
                loadThumbnail = loadThumbnail,
            )
        }
    }
}

@Composable
private fun FilmstripThumb(
    page: Int,
    selected: Boolean,
    bookmarked: Boolean,
    onClick: () -> Unit,
    loadThumbnail: suspend (Int) -> File?,
) {
    val colors = MaterialTheme.colorScheme
    val file by produceState<File?>(initialValue = null, page) { value = loadThumbnail(page) }
    val label = stringResource(R.string.reader_page_number_fmt, page + 1)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.width(FilmstripThumbWidth)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = label
                    this.selected = selected
                },
    ) {
        Box(
            modifier =
                Modifier.size(width = FilmstripThumbWidth, height = FilmstripThumbHeight)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, colors.primary, RoundedCornerShape(8.dp))
                                .padding(3.dp)
                        } else {
                            Modifier
                        }
                    )
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceContainerHigh)
        ) {
            file?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (selected) 1f else 0.7f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (bookmarked) PageRibbon(modifier = Modifier.align(Alignment.TopEnd))
        }
        Text(
            text = (page + 1).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun PageRibbon(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_bookmark_filled),
        contentDescription = stringResource(R.string.cd_reader_bookmarked),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(2.dp).size(16.dp),
    )
}

@Composable
private fun ScrubPreview(page: Int, loadThumbnail: suspend (Int) -> File?) {
    val colors = MaterialTheme.colorScheme
    val file by produceState<File?>(initialValue = null, page) { value = loadThumbnail(page) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier.size(width = PreviewWidth, height = PreviewHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainerHigh)
                    .border(2.dp, colors.primary, RoundedCornerShape(12.dp))
        ) {
            file?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        ReaderBubbleLabel(text = stringResource(R.string.reader_page_number_fmt, page + 1))
    }
}

@Composable
internal fun ReaderBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack,
        modifier =
            modifier
                .windowInsetsPadding(ReaderTopInsets)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_left),
            contentDescription = stringResource(R.string.cd_back),
            tint = Color.White,
        )
    }
}

@Composable
internal fun ReaderStatus(text: String, progress: Float?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ReaderFailure(error: ReaderError, onRetry: () -> Unit, onBack: () -> Unit) {
    val message =
        stringResource(
            when (error) {
                ReaderError.NOT_FOUND -> R.string.reader_error_not_found
                ReaderError.DOWNLOAD_NOT_ALLOWED -> R.string.reader_error_download_not_allowed
                ReaderError.UNSUPPORTED_FORMAT -> R.string.reader_error_unsupported
                ReaderError.NETWORK -> R.string.reader_error_network
                ReaderError.CORRUPT -> R.string.reader_error_corrupt
                ReaderError.NO_PAGES -> R.string.reader_error_no_pages
                ReaderError.PASSWORD_PROTECTED -> R.string.reader_error_password
            }
        )
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (error == ReaderError.NETWORK) {
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            Spacer(modifier = Modifier.height(8.dp))
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    }
}
