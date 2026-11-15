package com.makd.afinity.ui.reader.epub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.BookAnnotation
import com.makd.afinity.data.models.media.BookAnnotationKind
import com.makd.afinity.data.models.media.HighlightColor
import com.makd.afinity.ui.components.formatTimeLeftSeconds
import com.makd.afinity.ui.player.utils.PlayerSystemBarsController
import com.makd.afinity.ui.reader.EDGE_TAP_FRACTION
import com.makd.afinity.ui.reader.KeepScreenOn
import com.makd.afinity.ui.reader.ReaderBackButton
import com.makd.afinity.ui.reader.ReaderBookmarkButton
import com.makd.afinity.ui.reader.ReaderBottomPanel
import com.makd.afinity.ui.reader.ReaderBubbleLabel
import com.makd.afinity.ui.reader.ReaderChromeTheme
import com.makd.afinity.ui.reader.ReaderFailure
import com.makd.afinity.ui.reader.ReaderNextButton
import com.makd.afinity.ui.reader.ReaderProgressLabels
import com.makd.afinity.ui.reader.ReaderScrubber
import com.makd.afinity.ui.reader.ReaderSearchButton
import com.makd.afinity.ui.reader.ReaderSearchResultBar
import com.makd.afinity.ui.reader.ReaderSearchSheet
import com.makd.afinity.ui.reader.ReaderStatus
import com.makd.afinity.ui.reader.ReaderToolButton
import com.makd.afinity.ui.reader.ReaderToolIcon
import com.makd.afinity.ui.reader.ReaderToolPill
import com.makd.afinity.ui.reader.ReaderToolText
import com.makd.afinity.ui.reader.ReaderTopBar
import com.makd.afinity.ui.reader.UpNextBanner
import com.makd.afinity.ui.theme.isDarkSurface
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

private val EDGE_TAP_MIN = 48.dp
private const val HIGHLIGHT_GROUP = "highlights"
private const val SEARCH_GROUP = "search"
private const val SEARCH_DECORATION_ID = "search-hit"
private const val MENU_HIGHLIGHT = 1
private const val MENU_COPY = 2
private const val MENU_SHARE = 3

private enum class EpubSheet {
    CONTENTS,
    DISPLAY,
}

private class SelectionMenu(
    private val highlightLabel: String,
    private val copyLabel: String,
    private val shareLabel: String,
) : ActionMode.Callback {
    var onAction: (Int, () -> Unit) -> Unit = { _, finish -> finish() }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_HIGHLIGHT, 0, highlightLabel)
        menu.add(Menu.NONE, MENU_COPY, 1, copyLabel)
        menu.add(Menu.NONE, MENU_SHARE, 2, shareLabel)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        onAction(item.itemId) { mode.finish() }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = Unit
}

internal fun copyText(context: Context, label: String, text: String) {
    context
        .getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

@OptIn(ExperimentalReadiumApi::class)
internal fun EpubPreferences.resolved(appDark: Boolean): EpubPreferences =
    if (theme != null) this else copy(theme = if (appDark) Theme.DARK else Theme.LIGHT)

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubReaderScreen(
    onBack: () -> Unit,
    onOpenBook: (AfinityBook) -> Unit,
    viewModel: EpubReaderViewModel = hiltViewModel(),
) {
    val nextBook by viewModel.nextBook.collectAsStateWithLifecycle()
    var upNextDismissed by rememberSaveable { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val canSearch by viewModel.canSearch.collectAsStateWithLifecycle()
    val millisPerPosition by viewModel.millisPerPosition.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<EpubSheet?>(null) }
    var tappedHighlightId by rememberSaveable { mutableStateOf<String?>(null) }
    val appDark = isDarkSurface()
    val preferences = uiState.preferences.resolved(appDark)

    PlayerSystemBarsController(isControlsVisible = chromeVisible)
    KeepScreenOn()
    BackHandler(onBack = onBack)

    val theme = preferences.theme ?: Theme.LIGHT
    Box(modifier = Modifier.fillMaxSize().background(Color(theme.backgroundColor))) {
        when (val load = uiState.load) {
            is EpubLoadState.Downloading -> {
                val context = LocalContext.current
                val total = load.totalBytes
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
            }
            EpubLoadState.Opening ->
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    ReaderStatus(text = stringResource(R.string.reader_opening), progress = null)
                    ReaderBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
                }
            is EpubLoadState.Failed ->
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    ReaderFailure(error = load.error, onRetry = viewModel::retry, onBack = onBack)
                    ReaderBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
                }
            is EpubLoadState.Ready -> {
                val activity = LocalActivity.current as FragmentActivity
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val selectionMenu =
                    remember(load) {
                        SelectionMenu(
                            highlightLabel = context.getString(R.string.reader_highlight),
                            copyLabel = context.getString(R.string.reader_copy),
                            shareLabel = context.getString(R.string.reader_share),
                        )
                    }
                remember(load) {
                    EpubNavigatorFactory(load.publication)
                        .createFragmentFactory(
                            initialLocator = load.initialLocator,
                            initialPreferences = load.initialPreferences.resolved(appDark),
                            configuration =
                                EpubNavigatorFragment.Configuration(
                                    selectionActionModeCallback = selectionMenu
                                ),
                        )
                        .also { activity.supportFragmentManager.fragmentFactory = it }
                }
                var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
                AndroidFragment<EpubNavigatorFragment>(
                    modifier = Modifier.fillMaxSize(),
                    onUpdate = { navigator = it },
                )
                navigator?.let { nav ->
                    NavigatorBindings(
                        navigator = nav,
                        preferences = preferences,
                        onTapCenter = { chromeVisible = !chromeVisible },
                        onLocatorChanged = viewModel::onLocatorChanged,
                    )
                    HighlightBindings(
                        navigator = nav,
                        annotations = annotations,
                        onHighlightTap = { id -> tappedHighlightId = id },
                    )
                    val searchTint = MaterialTheme.colorScheme.primary.toArgb()
                    LaunchedEffect(nav, search.selected, search.hits) {
                        val hit = search.selected?.let { search.hits.getOrNull(it) }
                        val locator =
                            hit?.locatorJson?.let {
                                runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
                            }
                        nav.applyDecorations(
                            listOfNotNull(
                                locator?.let {
                                    Decoration(
                                        id = SEARCH_DECORATION_ID,
                                        locator = it,
                                        style =
                                            Decoration.Style.Underline(
                                                tint = searchTint,
                                                isActive = true,
                                            ),
                                    )
                                }
                            ),
                            SEARCH_GROUP,
                        )
                    }
                    val title = uiState.title
                    SideEffect {
                        selectionMenu.onAction = { action, finish ->
                            scope.launch {
                                val selection = nav.currentSelection()
                                val text = selection?.locator?.text?.highlight?.trim()
                                if (selection != null && !text.isNullOrEmpty()) {
                                    when (action) {
                                        MENU_HIGHLIGHT -> viewModel.addHighlight(selection.locator)
                                        MENU_COPY -> copyText(context, title, text)
                                        MENU_SHARE -> shareText(context, text)
                                    }
                                }
                                nav.clearSelection()
                                finish()
                            }
                        }
                    }
                }
                val bookmarked =
                    remember(annotations, uiState.progression) {
                        viewModel.bookmarkAt(uiState.progression, annotations) != null
                    }
                val goToHit: (Int) -> Unit = { index ->
                    viewModel.selectSearchHit(index)?.let { navigator?.go(it) }
                    searchOpen = false
                    chromeVisible = false
                }
                ReaderChromeTheme {
                    nextBook?.let { next ->
                        UpNextBanner(
                            visible =
                                !upNextDismissed &&
                                    !chromeVisible &&
                                    !search.barVisible &&
                                    uiState.progression >= EPUB_FINISHED_PROGRESSION,
                            next = next,
                            onRead = { onOpenBook(next) },
                            onDismiss = { upNextDismissed = true },
                        )
                    }
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
                EpubChrome(
                    visible = chromeVisible,
                    uiState = uiState,
                    preferences = preferences,
                    bookmarked = bookmarked,
                    onToggleBookmark = viewModel::toggleBookmark,
                    onSearchClick = if (canSearch) ({ searchOpen = true }) else null,
                    onNextClick = nextBook?.let { next -> { onOpenBook(next) } },
                    millisPerPosition = millisPerPosition,
                    onBack = onBack,
                    onSeek = { target ->
                        val nav = navigator
                        if (nav != null) viewModel.seek(target) { locator -> nav.go(locator) }
                    },
                    onOpenContents = { sheet = EpubSheet.CONTENTS },
                    onOpenDisplay = { sheet = EpubSheet.DISPLAY },
                    onPreferencesChange = viewModel::updatePreferences,
                )
                when (sheet) {
                    EpubSheet.CONTENTS ->
                        EpubContentsSheet(
                            title = uiState.title,
                            toc = uiState.toc,
                            progression = uiState.progression,
                            chapterTitle = uiState.chapterTitle,
                            annotations = annotations,
                            onEntryClick = { entry ->
                                sheet = null
                                chromeVisible = false
                                navigator?.go(entry.link)
                            },
                            onAnnotationClick = { annotation ->
                                val locator =
                                    annotation.locatorJson?.let {
                                        runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
                                    }
                                sheet = null
                                chromeVisible = false
                                if (locator != null) navigator?.go(locator)
                            },
                            onAnnotationDelete = { annotation ->
                                viewModel.deleteAnnotation(annotation.id)
                            },
                            onDismiss = { sheet = null },
                        )
                    EpubSheet.DISPLAY ->
                        EpubDisplaySheet(
                            preferences = uiState.preferences,
                            onPreferencesChange = viewModel::updatePreferences,
                            onDismiss = { sheet = null },
                        )
                    null -> Unit
                }
                val tapped = annotations.firstOrNull { it.id == tappedHighlightId }
                if (tapped != null) {
                    HighlightActionsSheet(
                        highlight = tapped,
                        onColor = { color -> viewModel.setHighlightColor(tapped.id, color) },
                        onCopy = {
                            tapped.text?.let { copyText(context, uiState.title, it) }
                            tappedHighlightId = null
                        },
                        onDelete = {
                            viewModel.deleteAnnotation(tapped.id)
                            tappedHighlightId = null
                        },
                        onDismiss = { tappedHighlightId = null },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun HighlightBindings(
    navigator: EpubNavigatorFragment,
    annotations: List<BookAnnotation>,
    onHighlightTap: (String) -> Unit,
) {
    val currentOnTap by rememberUpdatedState(onHighlightTap)
    DisposableEffect(navigator) {
        val listener =
            object : DecorableNavigator.Listener {
                override fun onDecorationActivated(
                    event: DecorableNavigator.OnActivatedEvent
                ): Boolean {
                    currentOnTap(event.decoration.id)
                    return true
                }
            }
        navigator.addDecorationListener(HIGHLIGHT_GROUP, listener)
        onDispose { navigator.removeDecorationListener(listener) }
    }
    LaunchedEffect(navigator, annotations) {
        val decorations =
            annotations
                .filter { it.kind == BookAnnotationKind.HIGHLIGHT }
                .mapNotNull { annotation ->
                    val locator =
                        annotation.locatorJson?.let {
                            runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
                        } ?: return@mapNotNull null
                    Decoration(
                        id = annotation.id,
                        locator = locator,
                        style =
                            Decoration.Style.Highlight(
                                tint = (annotation.color ?: HighlightColor.YELLOW).argb
                            ),
                    )
                }
        navigator.applyDecorations(decorations, HIGHLIGHT_GROUP)
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun NavigatorBindings(
    navigator: EpubNavigatorFragment,
    preferences: EpubPreferences,
    onTapCenter: () -> Unit,
    onLocatorChanged: (Locator) -> Unit,
) {
    val currentOnTap by rememberUpdatedState(onTapCenter)
    val minEdgePx = with(LocalDensity.current) { EDGE_TAP_MIN.toPx() }.toDouble()
    DisposableEffect(navigator, minEdgePx) {
        val edges =
            DirectionalNavigationAdapter(
                navigator,
                horizontalEdgeThresholdPercent = EDGE_TAP_FRACTION.toDouble(),
                minimumHorizontalEdgeSize = minEdgePx,
                animatedTransition = true,
            )
        val center =
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    currentOnTap()
                    return true
                }
            }
        navigator.addInputListener(edges)
        navigator.addInputListener(center)
        onDispose {
            navigator.removeInputListener(edges)
            navigator.removeInputListener(center)
        }
    }
    LaunchedEffect(navigator) { navigator.currentLocator.collect { onLocatorChanged(it) } }
    LaunchedEffect(navigator, preferences) { navigator.submitPreferences(preferences) }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun EpubChrome(
    visible: Boolean,
    uiState: EpubReaderUiState,
    preferences: EpubPreferences,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onSearchClick: (() -> Unit)?,
    onNextClick: (() -> Unit)?,
    millisPerPosition: Double,
    onBack: () -> Unit,
    onSeek: (Double) -> Unit,
    onOpenContents: () -> Unit,
    onOpenDisplay: () -> Unit,
    onPreferencesChange: ((EpubPreferences) -> EpubPreferences) -> Unit,
) {
    val toc = uiState.toc
    val progression = uiState.progression
    val currentIndex = toc.indexAt(progression, uiState.chapterTitle)
    val current = toc.getOrNull(currentIndex)
    val chapterTitle = uiState.chapterTitle?.takeIf { it.isNotBlank() } ?: current?.title
    val chapterFraction = toc.chapterFraction(currentIndex, progression)

    ReaderChromeTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ReaderTopBar(
                visible = visible,
                title = uiState.title,
                subtitle = uiState.authors,
                onBack = onBack,
            ) {
                if (onNextClick != null) ReaderNextButton(onClick = onNextClick)
                if (onSearchClick != null) ReaderSearchButton(onClick = onSearchClick)
                ReaderBookmarkButton(bookmarked = bookmarked, onClick = onToggleBookmark)
            }
            ReaderBottomPanel(visible = visible) {
                val part = current?.part?.takeIf { it != chapterTitle }
                if (part != null) {
                    Text(
                        text = part,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (chapterTitle != null) {
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                }
                ReaderScrubber(
                    fraction = progression.toFloat(),
                    onSeek = { onSeek(it.toDouble()) },
                    boundaries = uiState.boundaries,
                ) { fraction ->
                    val percent = (fraction * 100).roundToInt()
                    val title = toc.getOrNull(toc.indexAt(fraction.toDouble()))?.title
                    ReaderBubbleLabel(
                        text =
                            if (title != null) {
                                stringResource(R.string.reader_seek_bubble_fmt, title, percent)
                            } else {
                                stringResource(R.string.reader_progress_percent_fmt, percent)
                            }
                    )
                }
                val count = uiState.positionCount
                val secondsPerPosition = millisPerPosition / 1000.0
                val bookLeft =
                    if (count > 0) {
                        " · " +
                            formatTimeLeftSeconds(
                                (1.0 - progression).coerceAtLeast(0.0) * count * secondsPerPosition
                            )
                    } else {
                        ""
                    }
                val chapterLeft =
                    if (count > 0 && chapterFraction != null) {
                        " · " +
                            formatTimeLeftSeconds(
                                (toc.chapterEnd(currentIndex) - progression).coerceAtLeast(0.0) *
                                    count *
                                    secondsPerPosition
                            )
                    } else {
                        ""
                    }
                ReaderProgressLabels(
                    start = {
                        Text(
                            text =
                                stringResource(
                                    R.string.reader_book_percent_fmt,
                                    (progression * 100).roundToInt(),
                                ) + bookLeft,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    end =
                        chapterFraction?.let {
                            stringResource(
                                R.string.reader_chapter_percent_fmt,
                                (it * 100).roundToInt(),
                            ) + chapterLeft
                        },
                )
                val scroll = preferences.scroll == true
                ReaderToolPill {
                    ReaderToolButton(
                        contentDescription = stringResource(R.string.reader_contents),
                        enabled = toc.isNotEmpty(),
                        onClick = onOpenContents,
                    ) {
                        ReaderToolIcon(R.drawable.ic_list_details)
                    }
                    ReaderToolButton(
                        contentDescription = stringResource(R.string.reader_mode_paged),
                        selected = !scroll,
                        onClick = { onPreferencesChange { it.copy(scroll = false) } },
                    ) {
                        ReaderToolIcon(R.drawable.ic_horizontal)
                    }
                    ReaderToolButton(
                        contentDescription = stringResource(R.string.reader_mode_scroll),
                        selected = scroll,
                        onClick = { onPreferencesChange { it.copy(scroll = true) } },
                    ) {
                        ReaderToolIcon(R.drawable.ic_vertical)
                    }
                    ReaderToolButton(
                        contentDescription = stringResource(R.string.reader_display),
                        onClick = onOpenDisplay,
                    ) {
                        ReaderToolText(stringResource(R.string.reader_display_glyph))
                    }
                }
            }
        }
    }
}
