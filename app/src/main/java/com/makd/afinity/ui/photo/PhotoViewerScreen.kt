@file:OptIn(ExperimentalMaterial3Api::class)

package com.makd.afinity.ui.photo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityPhoto
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AfinityBackButton
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LocalOriginalImages
import com.makd.afinity.ui.components.cachedImageKey
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.player.utils.PlayerSystemBarsController
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val ZOOMED_FRACTION = 0.01f
private const val DISMISS_FRACTION = 0.15f
private const val SLIDESHOW_INTERVAL_MS = 5_000L
private const val SCREEN_QUALITY = 80
private const val DETAIL_CAP_PX = 2688
private const val DETAIL_CAP_QUALITY = 85
private val SnackbarAboveChrome = 72.dp

@Composable
fun PhotoViewerScreen(onBack: () -> Unit, viewModel: PhotoViewerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    PlayerSystemBarsController(isControlsVisible = chromeVisible)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val messageRes = event.messageRes()
            if (messageRes != null) {
                snackbarHostState.currentSnackbarData?.dismiss()
                launch { snackbarHostState.showSnackbar(context.getString(messageRes)) }
            } else {
                handleEvent(context, event)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            uiState.isLoading -> FullScreenLoading()
            uiState.error || uiState.items.isEmpty() ->
                FullScreenError(
                    message = stringResource(R.string.photo_viewer_load_failed),
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = { viewModel.load() },
                )
            else ->
                PhotoPager(
                    items = uiState.items,
                    startIndex = uiState.startIndex,
                    canDownload = canDownload,
                    chromeVisible = chromeVisible,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onBack = onBack,
                    viewModel = viewModel,
                )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            snackbar = { AFinitySnackbar(it) },
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = SnackbarAboveChrome),
        )
    }
}

@StringRes
private fun PhotoViewerEvent.messageRes(): Int? =
    when (this) {
        PhotoViewerEvent.SaveStarted -> R.string.photo_saving
        PhotoViewerEvent.Saved -> R.string.photo_saved
        PhotoViewerEvent.SaveFailed -> R.string.photo_save_failed
        PhotoViewerEvent.ActionFailed -> R.string.photo_action_failed
        else -> null
    }

private fun handleEvent(context: Context, event: PhotoViewerEvent) {
    when (event) {
        is PhotoViewerEvent.Share -> {
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = event.mimeType
                    putExtra(Intent.EXTRA_STREAM, event.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(send, null))
        }
        PhotoViewerEvent.SaveStarted,
        PhotoViewerEvent.Saved,
        PhotoViewerEvent.SaveFailed,
        PhotoViewerEvent.ActionFailed -> Unit
        is PhotoViewerEvent.PlayVideo ->
            PlayerLauncher.launch(
                context = context,
                itemId = event.itemId,
                mediaSourceId = event.mediaSourceId,
                startPositionMs = event.startPositionMs,
            )
    }
}

@Composable
private fun PhotoPager(
    items: List<AfinityItem>,
    startIndex: Int,
    canDownload: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onBack: () -> Unit,
    viewModel: PhotoViewerViewModel,
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var zoomedPage by remember { mutableStateOf<Int?>(null) }
    var slideshow by rememberSaveable { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<AfinityPhoto?>(null) }
    val screenPx = LocalWindowInfo.current.containerSize.width
    val originalImages = LocalOriginalImages.current

    LaunchedEffect(slideshow, pagerState.settledPage) {
        if (!slideshow) return@LaunchedEffect
        delay(SLIDESHOW_INTERVAL_MS)
        val next = pagerState.settledPage + 1
        if (next < items.size) pagerState.animateScrollToPage(next) else slideshow = false
    }

    HorizontalPager(
        state = pagerState,
        key = { items[it].id },
        beyondViewportPageCount = 1,
        userScrollEnabled = zoomedPage != pagerState.currentPage,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        when (val item = items[page]) {
            is AfinityPhoto ->
                ZoomablePhoto(
                    photo = item,
                    page = page,
                    pagerState = pagerState,
                    sources =
                        remember(item.id, screenPx, canDownload, originalImages) {
                            item.photoSources(screenPx, canDownload, originalImages)
                        },
                    onTap = onToggleChrome,
                    onZoomChanged = { zoomed ->
                        zoomedPage =
                            when {
                                zoomed -> page
                                zoomedPage == page -> null
                                else -> zoomedPage
                            }
                    },
                    onDismiss = onBack,
                )
            else ->
                VideoPage(
                    item = item,
                    screenUrl = item.screenUrl(screenPx),
                    onTap = onToggleChrome,
                    onPlay = {
                        slideshow = false
                        viewModel.playVideo(item)
                    },
                )
        }
    }

    val current = items.getOrNull(pagerState.currentPage)
    AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut()) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(modifier = Modifier.fillMaxSize()) {
                TopChrome(
                    title = current?.name.orEmpty(),
                    position =
                        stringResource(
                            R.string.photo_position_fmt,
                            pagerState.currentPage + 1,
                            items.size,
                        ),
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                if (current != null) {
                    BottomChrome(
                        item = current,
                        canDownload = canDownload,
                        slideshow = slideshow,
                        onToggleFavorite = { viewModel.toggleFavorite(current) },
                        onInfo = { (current as? AfinityPhoto)?.let { infoFor = it } },
                        onToggleSlideshow = { slideshow = !slideshow },
                        onShare = { (current as? AfinityPhoto)?.let(viewModel::sharePhoto) },
                        onSave = { (current as? AfinityPhoto)?.let(viewModel::savePhoto) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    infoFor?.let { photo ->
        ModalBottomSheet(onDismissRequest = { infoFor = null }) { PhotoInfo(photo = photo) }
    }
}

private data class PhotoSources(
    val primaryUrl: String?,
    val screenUrl: String?,
    val detailUrl: String?,
    val detailFirst: Boolean,
)

private fun AfinityPhoto.photoSources(
    screenPx: Int,
    canDownload: Boolean,
    originalImages: Boolean,
): PhotoSources =
    PhotoSources(
        primaryUrl = images.primaryImageUrl,
        screenUrl = screenUrl(screenPx),
        detailUrl = detailUrl(canDownload),
        detailFirst = originalImages && canDownload,
    )

@Composable
private fun ZoomablePhoto(
    photo: AfinityPhoto,
    page: Int,
    pagerState: PagerState,
    sources: PhotoSources,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = MAX_SCALE))
    val imageState = rememberZoomableImageState(zoomableState)
    val zoomed by remember {
        computedStateOf { (zoomableState.zoomFraction ?: 0f) > ZOOMED_FRACTION }
    }
    val settled = pagerState.settledPage == page
    var detailReady by remember(sources.detailUrl) { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var dismissDrag by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(zoomed) { onZoomChanged(zoomed) }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != page) zoomableState.resetZoom(animationSpec = SnapSpec())
    }
    LaunchedEffect(sources.detailUrl, settled, viewport) {
        val url = sources.detailUrl
        if (
            url == null ||
                sources.detailFirst ||
                detailReady ||
                !settled ||
                viewport == IntSize.Zero
        ) {
            return@LaunchedEffect
        }
        val result =
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(viewport.width, viewport.height)
                    .precision(Precision.INEXACT)
                    .build(),
            )
        if (result is SuccessResult) detailReady = true
    }
    LaunchedEffect(zoomableState) { zoomableState.keepCenterAcrossImageSwaps() }

    val thumbnailKey = remember(sources.primaryUrl) { cachedImageKey(context, sources.primaryUrl) }
    val screenRequest =
        remember(sources.screenUrl, thumbnailKey) {
            ImageRequest.Builder(context)
                .data(sources.screenUrl)
                .placeholderMemoryCacheKey(thumbnailKey)
                .build()
        }
    val detailRequest =
        remember(sources.detailUrl, sources.detailFirst, thumbnailKey) {
            sources.detailUrl?.let { url ->
                ImageRequest.Builder(context)
                    .data(url)
                    .placeholderMemoryCacheKey(
                        if (sources.detailFirst) thumbnailKey else sources.screenUrl,
                    )
                    .build()
            }
        }
    val request =
        if ((sources.detailFirst || detailReady) && detailRequest != null) detailRequest
        else screenRequest

    Box(
        modifier =
            Modifier.fillMaxSize()
                .onSizeChanged { viewport = it }
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = !zoomed,
                    state =
                        rememberDraggableState { delta ->
                            dismissDrag = (dismissDrag + delta).coerceAtLeast(0f)
                        },
                    onDragStopped = {
                        val height = viewport.height
                        if (height > 0 && dismissDrag > height * DISMISS_FRACTION) onDismiss()
                        else dismissDrag = 0f
                    },
                )
                .graphicsLayer {
                    val height = viewport.height
                    translationY = dismissDrag
                    alpha = if (height > 0) 1f - (dismissDrag / height).coerceIn(0f, 0.6f) else 1f
                },
    ) {
        ZoomableAsyncImage(
            model = request,
            contentDescription = photo.name,
            state = imageState,
            contentScale = ContentScale.Fit,
            onClick = { onTap() },
            onDoubleClick = DoubleClickToZoomListener.cycle(maxZoomFactor = DOUBLE_TAP_SCALE),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private class ViewAnchor(val contentSize: Size, val drawn: Rect, val viewportCenter: Offset) {
    val centerFraction: Offset
        get() =
            Offset(
                (viewportCenter.x - drawn.left) / drawn.width,
                (viewportCenter.y - drawn.top) / drawn.height,
            )

    fun pointAt(fraction: Offset): Offset =
        Offset(drawn.left + fraction.x * drawn.width, drawn.top + fraction.y * drawn.height)

    fun sameContentAs(other: ViewAnchor): Boolean =
        abs(contentSize.width - other.contentSize.width) <= 1f &&
            abs(contentSize.height - other.contentSize.height) <= 1f
}

@OptIn(ExperimentalTelephotoApi::class)
private fun ZoomableState.viewAnchor(): ViewAnchor? {
    val scale = contentTransformation.takeIf { it.isSpecified }?.scale ?: return null
    if (zoomFraction == null || scale.scaleX <= 0f || scale.scaleY <= 0f) return null
    return with(coordinateSystem) {
        val drawn = contentBounds(clipToViewport = false).rectIn(CoordinateSpace.Viewport)
        if (drawn.isEmpty || viewportSize.isEmpty()) null
        else
            ViewAnchor(
                contentSize = Size(drawn.width / scale.scaleX, drawn.height / scale.scaleY),
                drawn = drawn,
                viewportCenter = viewportSize.center,
            )
    }
}

private suspend fun ZoomableState.keepCenterAcrossImageSwaps() {
    var last: ViewAnchor? = null
    snapshotFlow { viewAnchor() }
        .filterNotNull()
        .collect { current ->
            val previous = last
            last = current
            if (previous == null || previous.sameContentAs(current)) return@collect
            if ((zoomFraction ?: 0f) <= ZOOMED_FRACTION) return@collect
            val delta = current.viewportCenter - current.pointAt(previous.centerFraction)
            if (delta.getDistance() > 1f) panBy(delta, SnapSpec())
        }
}

@Composable
private fun VideoPage(
    item: AfinityItem,
    screenUrl: String?,
    onTap: () -> Unit,
    onPlay: () -> Unit,
) {
    Box(
        modifier =
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = screenUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(onClick = onPlay, shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_player_play_filled),
                contentDescription = stringResource(R.string.photo_action_play),
                tint = Color.White,
                modifier = Modifier.padding(18.dp).size(32.dp),
            )
        }
    }
}

@Composable
private fun TopChrome(
    title: String,
    position: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                    ),
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AfinityBackButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = position,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun BottomChrome(
    item: AfinityItem,
    canDownload: Boolean,
    slideshow: Boolean,
    onToggleFavorite: () -> Unit,
    onInfo: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPhoto = item is AfinityPhoto
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    ),
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleFavorite) {
            Icon(
                painter =
                    painterResource(
                        id =
                            if (item.favorite) R.drawable.ic_favorite_filled
                            else R.drawable.ic_favorite,
                    ),
                contentDescription = stringResource(R.string.filter_favorites),
            )
        }
        if (isPhoto) {
            IconButton(onClick = onInfo) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_info_outlined),
                    contentDescription = stringResource(R.string.photo_action_info),
                )
            }
        }
        IconButton(onClick = onToggleSlideshow) {
            Icon(
                painter =
                    painterResource(
                        id =
                            if (slideshow) R.drawable.ic_player_pause_filled
                            else R.drawable.ic_player_play_filled,
                    ),
                contentDescription =
                    stringResource(
                        if (slideshow) R.string.photo_action_slideshow_stop
                        else R.string.photo_action_slideshow_start,
                    ),
            )
        }
        if (isPhoto && canDownload) {
            IconButton(onClick = onShare) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_share),
                    contentDescription = stringResource(R.string.photo_action_share),
                )
            }
            IconButton(onClick = onSave) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_download),
                    contentDescription = stringResource(R.string.photo_action_save),
                )
            }
        }
    }
}

@Composable
private fun PhotoInfo(photo: AfinityPhoto) {
    val context = LocalContext.current
    val exif = photo.exif
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = photo.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        if (photo.overview.isNotBlank()) {
            Text(text = photo.overview, style = MaterialTheme.typography.bodyMedium)
        }
        photo.dateTaken?.let {
            InfoLine(stringResource(R.string.photo_info_taken), it.format(dateFormatter))
        }
        listOfNotNull(exif.cameraMake, exif.cameraModel)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
            ?.let { InfoLine(stringResource(R.string.photo_info_camera), it) }
        exposureLine(photo)?.let { InfoLine(stringResource(R.string.photo_info_exposure), it) }
        if (photo.width != null && photo.height != null) {
            InfoLine(
                stringResource(R.string.photo_info_resolution),
                stringResource(R.string.photo_info_resolution_fmt, photo.width, photo.height),
            )
        }
        photo.album?.let { InfoLine(stringResource(R.string.photo_info_album), it) }
        photo.fileName?.let { InfoLine(stringResource(R.string.photo_info_file), it) }
        if (exif.hasLocation) {
            val lat = exif.latitude ?: 0.0
            val lon = exif.longitude ?: 0.0
            val coordinates = String.format(Locale.US, "%.5f, %.5f", lat, lon)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoLine(stringResource(R.string.photo_info_location), coordinates)
                TextButton(
                    onClick = {
                        val geo = "geo:$lat,$lon?q=$lat,$lon".toUri()
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, geo))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(
                                    context,
                                    R.string.photo_action_failed,
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_map_pin),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.photo_info_open_map))
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun exposureLine(photo: AfinityPhoto): String? {
    val exif = photo.exif
    val parts = buildList {
        exif.exposureTime
            ?.takeIf { it > 0 }
            ?.let { seconds ->
                add(
                    if (seconds < 1) "1/${(1 / seconds).roundToInt()} s"
                    else String.format(Locale.US, "%.1f s", seconds),
                )
            }
        exif.aperture?.takeIf { it > 0 }?.let { add(String.format(Locale.US, "f/%.1f", it)) }
        exif.focalLength?.takeIf { it > 0 }?.let { add(String.format(Locale.US, "%.0f mm", it)) }
        exif.isoSpeedRating?.takeIf { it > 0 }?.let { add("ISO $it") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun AfinityItem.screenUrl(widthPx: Int): String? {
    val base = images.primaryImageUrl ?: return null
    if (widthPx <= 0) return base
    return base
        .toUri()
        .buildUpon()
        .appendQueryParameter("maxWidth", "$widthPx")
        .appendQueryParameter("quality", "$SCREEN_QUALITY")
        .build()
        .toString()
}

private fun AfinityPhoto.detailUrl(canDownload: Boolean): String? {
    val primary = images.primaryImageUrl?.toUri() ?: return null
    return if (canDownload) {
        primary
            .buildUpon()
            .encodedPath(primary.encodedPath?.substringBefore("/Images/") + "/Download")
            .clearQuery()
            .build()
            .toString()
    } else {
        primary
            .buildUpon()
            .appendQueryParameter("maxWidth", "$DETAIL_CAP_PX")
            .appendQueryParameter("quality", "$DETAIL_CAP_QUALITY")
            .build()
            .toString()
    }
}
