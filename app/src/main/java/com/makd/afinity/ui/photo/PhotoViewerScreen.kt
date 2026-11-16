@file:OptIn(ExperimentalMaterial3Api::class)

package com.makd.afinity.ui.photo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
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
import coil3.request.ImageRequest
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityPhoto
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AfinityBackButton
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.player.utils.PlayerSystemBarsController
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val ORIGINAL_ZOOM_THRESHOLD = 1.5f
private const val ZOOMED_EPSILON = 1.01f
private const val DISMISS_FRACTION = 0.15f
private const val SLIDESHOW_INTERVAL_MS = 5_000L
private const val ORIGINAL_MAX_PX = 4096
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
                    screenUrl = item.screenUrl(screenPx),
                    originalUrl = item.originalUrl(canDownload),
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

@Composable
private fun ZoomablePhoto(
    photo: AfinityPhoto,
    screenUrl: String?,
    originalUrl: String?,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dismissDrag by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var wantOriginal by remember { mutableStateOf(false) }
    val zoomed = scale > ZOOMED_EPSILON

    LaunchedEffect(zoomed) { onZoomChanged(zoomed) }
    LaunchedEffect(scale >= ORIGINAL_ZOOM_THRESHOLD) {
        if (scale >= ORIGINAL_ZOOM_THRESHOLD) wantOriginal = true
    }

    fun clamp(value: Offset, atScale: Float): Offset {
        val maxX = size.width * (atScale - 1f) / 2f
        val maxY = size.height * (atScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tap ->
                            if (scale > ZOOMED_EPSILON) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                scale = DOUBLE_TAP_SCALE
                                offset = clamp((center - tap) * (DOUBLE_TAP_SCALE - 1f), scale)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var decided = false
                        var dismissing = false
                        var totalX = 0f
                        var totalY = 0f
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val newScale =
                                    (scale * event.calculateZoom()).coerceIn(1f, MAX_SCALE)
                                val applied = newScale / scale
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val centroid = event.calculateCentroid(useCurrent = true)
                                offset =
                                    clamp(
                                        (centroid - center) * (1f - applied) +
                                            offset * applied +
                                            event.calculatePan(),
                                        newScale,
                                    )
                                scale = newScale
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                decided = true
                            } else if (pressed.size == 1) {
                                val change = pressed.first()
                                val delta = change.positionChange()
                                if (scale > ZOOMED_EPSILON) {
                                    offset = clamp(offset + delta, scale)
                                    if (delta != Offset.Zero) change.consume()
                                } else if (!decided) {
                                    totalX += delta.x
                                    totalY += delta.y
                                    val slop = viewConfiguration.touchSlop
                                    if (abs(totalX) > slop || abs(totalY) > slop) {
                                        decided = true
                                        dismissing = totalY > 0 && abs(totalY) > abs(totalX) * 1.5f
                                    }
                                }
                                if (dismissing) {
                                    dismissDrag = (dismissDrag + delta.y).coerceAtLeast(0f)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (dismissing) {
                            if (dismissDrag > size.height * DISMISS_FRACTION) onDismiss()
                            else dismissDrag = 0f
                        }
                        if (scale <= ZOOMED_EPSILON) {
                            scale = 1f
                            offset = Offset.Zero
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y + dismissDrag
                    alpha =
                        if (size.height > 0) 1f - (dismissDrag / size.height).coerceIn(0f, 0.6f)
                        else 1f
                },
    ) {
        AsyncImage(
            model = screenUrl,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (wantOriginal && originalUrl != null) {
            val context = LocalContext.current
            AsyncImage(
                model =
                    remember(originalUrl) {
                        ImageRequest.Builder(context)
                            .data(originalUrl)
                            .size(ORIGINAL_MAX_PX, ORIGINAL_MAX_PX)
                            .build()
                    },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
    return base.toUri().buildUpon().appendQueryParameter("maxWidth", "$widthPx").build().toString()
}

private fun AfinityPhoto.originalUrl(canDownload: Boolean): String? {
    val primary = images.primaryImageUrl?.toUri() ?: return null
    return if (canDownload) {
        primary
            .buildUpon()
            .encodedPath(primary.encodedPath?.substringBefore("/Images/") + "/Download")
            .clearQuery()
            .build()
            .toString()
    } else {
        primary.buildUpon().appendQueryParameter("quality", "100").build().toString()
    }
}
