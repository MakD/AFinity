package com.makd.afinity.ui.admin.images

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ItemImage
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.EmptyState
import com.makd.afinity.ui.components.MediaCountBadge
import com.makd.afinity.ui.components.rememberRatingMetadataScale
import com.makd.afinity.util.formatFileSize
import java.util.Locale
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val LOGO = "Logo"
private val CardShape = RoundedCornerShape(16.dp)
private val TileShape = RoundedCornerShape(12.dp)

private enum class ImageShape(val ratio: Float, val minCell: Dp, val thumbWidth: Dp) {
    PORTRAIT(2f / 3f, 104.dp, 96.dp),
    SQUARE(1f, 104.dp, 96.dp),
    LANDSCAPE(16f / 9f, 160.dp, 136.dp),
    BANNER(1000f / 185f, 280.dp, 136.dp),
}

private fun imageShape(imageType: String, images: List<ItemImage>): ImageShape =
    when (imageType) {
        "Backdrop",
        "Thumb",
        "Art",
        "Logo",
        "Screenshot",
        "Menu",
        "Chapter" -> ImageShape.LANDSCAPE

        "Banner" -> ImageShape.BANNER
        "Disc" -> ImageShape.SQUARE
        else -> {
            val sample = images.firstOrNull { it.width > 0 && it.height > 0 }
            val ratio = sample?.let { it.width.toFloat() / it.height }
            when {
                ratio == null -> ImageShape.PORTRAIT
                ratio > 1.3f -> ImageShape.LANDSCAPE
                ratio > 0.85f -> ImageShape.SQUARE
                else -> ImageShape.PORTRAIT
            }
        }
    }

private fun resolutionText(image: ItemImage): String? =
    if (image.width > 0 && image.height > 0) "${image.width} × ${image.height}" else null

private fun ratingText(image: ItemImage, rating: Double): String =
    if (image.ratingIsLikes) rating.toInt().toString() else String.format(Locale.US, "%.1f", rating)

private fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).ifBlank { code }

@Composable
private fun imageTypeLabel(imageType: String): String =
    when (imageType) {
        "Primary" -> stringResource(R.string.admin_image_type_primary)
        "Backdrop" -> stringResource(R.string.admin_image_type_backdrop)
        "Logo" -> stringResource(R.string.admin_image_type_logo)
        "Thumb" -> stringResource(R.string.admin_image_type_thumb)
        "Banner" -> stringResource(R.string.admin_image_type_banner)
        "Art" -> stringResource(R.string.admin_image_type_art)
        "Disc" -> stringResource(R.string.admin_image_type_disc)
        else -> imageType
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditImagesScreen(
    onNavigateUp: () -> Unit,
    onChangeMade: () -> Unit = {},
    viewModel: EditImagesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnChangeMade by rememberUpdatedState(onChangeMade)
    val gridState = rememberLazyGridState()
    var imageToDelete by remember { mutableStateOf<ItemImage?>(null) }
    var previewImage by remember { mutableStateOf<ItemImage?>(null) }

    val imagePicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
            uri: Uri? ->
            if (uri != null) viewModel.uploadImage(uri)
        }

    val shape =
        remember(uiState.selectedType, uiState.currentImages, uiState.candidates) {
            imageShape(uiState.selectedType, uiState.currentImages + uiState.candidates)
        }

    val message = uiState.message
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            if (message.isSuccess) currentOnChangeMade()
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(uiState.selectedType) { gridState.scrollToItem(0) }

    imageToDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { imageToDelete = null },
            title = {
                Text(
                    stringResource(R.string.admin_delete_image_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    stringResource(R.string.admin_delete_image_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteImage(image)
                        imageToDelete = null
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { imageToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    previewImage?.let { image ->
        ImagePreviewSheet(
            image = image,
            shape = shape,
            replacesCurrent = !uiState.allowsMultiple && uiState.currentImages.isNotEmpty(),
            addsToList = uiState.allowsMultiple,
            enabled = !uiState.busy,
            onConfirm = {
                previewImage = null
                viewModel.applyRemoteImage(image)
            },
            onRemove = {
                previewImage = null
                imageToDelete = image
            },
            onDismiss = { previewImage = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.admin_edit_images_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, snackbar = { AFinitySnackbar(it) }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.types.size > 1) {
                ImageTypeTabs(
                    types = uiState.types,
                    selectedType = uiState.selectedType,
                    counts = uiState.typeCounts,
                    onSelect = viewModel::selectType,
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = shape.minCell),
                    state = gridState,
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 16.dp + LocalPlayerOffset.current,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CurrentSection(
                            uiState = uiState,
                            shape = shape,
                            onUpload = { imagePicker.launch("image/*") },
                            onPreview = { previewImage = it },
                            onDelete = { imageToDelete = it },
                            onMove = viewModel::moveImage,
                            onRetry = viewModel::loadServerImages,
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ProviderFilters(
                            uiState = uiState,
                            onToggleLanguages = {
                                viewModel.setIncludeAllLanguages(!uiState.includeAllLanguages)
                            },
                            onSelectProvider = viewModel::selectProvider,
                            onSelectSort = viewModel::selectSort,
                        )
                    }

                    when {
                        uiState.remoteLoading ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                        uiState.remoteFailed ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionMessage(
                                    icon = painterResource(R.drawable.ic_cloud_off),
                                    title = stringResource(R.string.admin_images_load_failed_title),
                                    message =
                                        stringResource(R.string.admin_images_load_failed_message),
                                    actionText = stringResource(R.string.action_retry),
                                    onAction = viewModel::loadRemoteImages,
                                )
                            }

                        uiState.candidates.isEmpty() ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                if (uiState.includeAllLanguages) {
                                    SectionMessage(
                                        icon = painterResource(R.drawable.ic_photo_search),
                                        title = stringResource(R.string.admin_images_empty_title),
                                        message =
                                            stringResource(R.string.admin_images_empty_message),
                                    )
                                } else {
                                    SectionMessage(
                                        icon = painterResource(R.drawable.ic_photo_search),
                                        title = stringResource(R.string.admin_images_empty_title),
                                        message =
                                            stringResource(
                                                R.string.admin_images_empty_language_message
                                            ),
                                        actionText =
                                            stringResource(
                                                R.string.admin_images_show_all_languages
                                            ),
                                        onAction = { viewModel.setIncludeAllLanguages(true) },
                                    )
                                }
                            }

                        else ->
                            items(uiState.candidates) { image ->
                                CandidateTile(
                                    image = image,
                                    shape = shape,
                                    showLanguage = uiState.includeAllLanguages,
                                    enabled = !uiState.busy,
                                    onClick = { previewImage = image },
                                )
                            }
                    }
                }

                if (uiState.busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageTypeTabs(
    types: List<String>,
    selectedType: String,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit,
) {
    val selectedIndex = types.indexOf(selectedType).coerceAtLeast(0)
    SecondaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        edgePadding = 8.dp,
        divider = {},
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedIndex),
                color = MaterialTheme.colorScheme.primary,
                height = 3.dp,
            )
        },
    ) {
        types.forEachIndexed { index, type ->
            val count = counts[type] ?: 0
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(type) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            imageTypeLabel(type),
                            style =
                                if (selectedIndex == index) MaterialTheme.typography.titleSmall
                                else MaterialTheme.typography.bodyMedium,
                        )
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier.clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CurrentSection(
    uiState: EditImagesUiState,
    shape: ImageShape,
    onUpload: () -> Unit,
    onPreview: (ItemImage) -> Unit,
    onDelete: (ItemImage) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRetry: () -> Unit,
) {
    val images = uiState.currentImages
    val showStrip =
        uiState.allowsMultiple &&
            images.isNotEmpty() &&
            !uiState.serverLoading &&
            !uiState.serverFailed

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(
                text = stringResource(R.string.admin_images_on_server),
                modifier = Modifier.weight(1f),
            )
            if (showStrip) {
                TextButton(onClick = onUpload, enabled = !uiState.busy) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.admin_upload_image))
                }
            }
        }

        when {
            uiState.serverLoading ->
                Box(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

            uiState.serverFailed -> LoadFailedCard(onRetry = onRetry)
            showStrip -> {
                CurrentImageStrip(
                    images = images,
                    shape = shape,
                    canReorder = uiState.canReorder && !uiState.busy,
                    enabled = !uiState.busy,
                    onPreview = onPreview,
                    onMove = onMove,
                )
                if (uiState.canReorder) {
                    Text(
                        text = stringResource(R.string.admin_images_reorder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }

            else ->
                CurrentImageCard(
                    image = images.firstOrNull(),
                    shape = shape,
                    enabled = !uiState.busy,
                    onUpload = onUpload,
                    onPreview = onPreview,
                    onDelete = onDelete,
                )
        }
    }
}

@Composable
private fun LoadFailedCard(onRetry: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.admin_images_load_failed_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun CurrentImageCard(
    image: ItemImage?,
    shape: ImageShape,
    enabled: Boolean,
    onUpload: () -> Unit,
    onPreview: (ItemImage) -> Unit,
    onDelete: (ItemImage) -> Unit,
) {
    val cardModifier =
        Modifier.fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    val onThumbnailClick = { if (image != null) onPreview(image) else onUpload() }

    if (shape == ImageShape.BANNER) {
        Column(modifier = cardModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CurrentThumbnail(
                image = image,
                shape = shape,
                enabled = enabled,
                onClick = onThumbnailClick,
                modifier = Modifier.fillMaxWidth(),
            )
            CurrentDetails(
                image = image,
                enabled = enabled,
                onUpload = onUpload,
                onDelete = onDelete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Row(
            modifier = cardModifier,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurrentThumbnail(
                image = image,
                shape = shape,
                enabled = enabled,
                onClick = onThumbnailClick,
                modifier = Modifier.width(shape.thumbWidth),
            )
            CurrentDetails(
                image = image,
                enabled = enabled,
                onUpload = onUpload,
                onDelete = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CurrentThumbnail(
    image: ItemImage?,
    shape: ImageShape,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(shape.ratio)
                .clip(TileShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Artwork(url = image.url, fit = image.imageType == LOGO)
        } else {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.admin_upload_image),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CurrentDetails(
    image: ItemImage?,
    enabled: Boolean,
    onUpload: () -> Unit,
    onDelete: (ItemImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            if (image == null) {
                Text(
                    text = stringResource(R.string.admin_images_none_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.admin_images_none_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                resolutionText(image)?.let { resolution ->
                    Text(
                        text = resolution,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (image.fileSize > 0) {
                    Text(
                        text = formatFileSize(context, image.fileSize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = onUpload,
                enabled = enabled,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.admin_upload_image),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (image != null) {
                IconButton(onClick = { onDelete(image) }, enabled = enabled) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.action_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentImageStrip(
    images: List<ItemImage>,
    shape: ImageShape,
    canReorder: Boolean,
    enabled: Boolean,
    onPreview: (ItemImage) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var ordered by remember(images) { mutableStateOf(images) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTo by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            if (dragFrom == null) dragFrom = from.index
            dragTo = to.index
            ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
    val tileWidth = if (shape.ratio > 1f) 176.dp else 120.dp

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(ordered, key = { _, image -> image.url ?: image.hashCode() }) { index, image ->
            ReorderableItem(reorderState, key = image.url ?: image.hashCode()) { isDragging ->
                val elevation by
                    animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag_elev")
                Column(
                    modifier =
                        Modifier.width(tileWidth)
                            .longPressDraggableHandle(
                                enabled = canReorder,
                                onDragStopped = {
                                    val from = dragFrom
                                    val to = dragTo
                                    dragFrom = null
                                    dragTo = null
                                    if (from != null && to != null && from != to) onMove(from, to)
                                },
                            ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .aspectRatio(shape.ratio)
                                .shadow(elevation, TileShape)
                                .clip(TileShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(enabled = enabled) { onPreview(image) }
                    ) {
                        Artwork(url = image.url, fit = false)
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier.padding(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = resolutionText(image).orEmpty(),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderFilters(
    uiState: EditImagesUiState,
    onToggleLanguages: () -> Unit,
    onSelectProvider: (String?) -> Unit,
    onSelectSort: (ImageSort) -> Unit,
) {
    val allProviders = stringResource(R.string.admin_images_all_providers)
    val sortEntries =
        listOf(
            stringResource(R.string.admin_images_sort_recommended) to ImageSort.RECOMMENDED,
            stringResource(R.string.admin_images_sort_largest) to ImageSort.LARGEST,
        )

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                text = stringResource(R.string.admin_images_from_providers),
                modifier = Modifier.weight(1f),
            )
            if (uiState.candidates.isNotEmpty()) {
                Text(
                    text = uiState.candidates.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = uiState.includeAllLanguages,
                onClick = onToggleLanguages,
                label = { Text(stringResource(R.string.admin_include_all_languages)) },
                leadingIcon =
                    if (uiState.includeAllLanguages) {
                        {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                shape = CircleShape,
                border = null,
                colors = imageChipColors(),
            )
            if (uiState.providers.size > 1) {
                DropdownChip(
                    label = uiState.provider ?: allProviders,
                    selected = uiState.provider != null,
                    entries =
                        listOf<Pair<String, String?>>(allProviders to null) +
                            uiState.providers.map { it to it },
                    isSelected = { it == uiState.provider },
                    onSelect = onSelectProvider,
                )
            }
            DropdownChip(
                label = sortEntries.first { it.second == uiState.sort }.first,
                selected = uiState.sort != ImageSort.RECOMMENDED,
                entries = sortEntries,
                isSelected = { it == uiState.sort },
                onSelect = onSelectSort,
            )
        }
    }
}

@Composable
private fun imageChipColors() =
    FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

@Composable
private fun <T> DropdownChip(
    label: String,
    selected: Boolean,
    entries: List<Pair<String, T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            shape = CircleShape,
            border = null,
            colors = imageChipColors(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            entries.forEach { (entryLabel, value) ->
                DropdownMenuItem(
                    text = { Text(text = entryLabel) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    leadingIcon =
                        if (isSelected(value)) {
                            {
                                Icon(
                                    painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                )
            }
        }
    }
}

@Composable
private fun CandidateTile(
    image: ItemImage,
    shape: ImageShape,
    showLanguage: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val ratingScale = rememberRatingMetadataScale()
    val metadataStyle =
        MaterialTheme.typography.bodySmall.copy(
            fontSize = MaterialTheme.typography.bodySmall.fontSize * ratingScale.textScale
        )
    val resolution = resolutionText(image)
    val provider = image.providerName?.takeIf { it.isNotBlank() }
    val language = image.language?.takeIf { it.isNotBlank() }?.uppercase()
    val rating = image.communityRating?.takeIf { it > 0.0 }
    val subtitle = provider.takeIf { resolution != null }

    Column {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(shape.ratio)
                    .clip(TileShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(enabled = enabled, onClick = onClick)
        ) {
            Artwork(url = image.url, fit = image.imageType == LOGO)
            if (showLanguage && language != null) {
                MediaCountBadge(
                    text = language,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = resolution ?: provider.orEmpty(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = metadataStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (subtitle != null && rating != null) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rating != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (image.ratingIsLikes) {
                        Icon(
                            painterResource(R.drawable.ic_favorite_filled),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(ratingScale.rtIconSize),
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_community_rating),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(ratingScale.rtIconSize),
                        )
                    }
                    Text(
                        text = ratingText(image, rating),
                        style = metadataStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun Artwork(url: String?, fit: Boolean, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            modifier = modifier.fillMaxSize().padding(if (fit) 12.dp else 0.dp),
        )
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_broken_image),
                contentDescription = stringResource(R.string.cd_admin_no_image),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun SectionMessage(
    icon: Painter,
    title: String,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = icon,
            title = title,
            message = message,
            badgeSize = 72.dp,
            iconSize = 32.dp,
        )
        if (actionText != null && onAction != null) {
            Button(
                onClick = onAction,
                colors =
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(text = actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePreviewSheet(
    image: ItemImage,
    shape: ImageShape,
    replacesCurrent: Boolean,
    addsToList: Boolean,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ratio =
        if (image.width > 0 && image.height > 0) {
            (image.width.toFloat() / image.height).coerceIn(0.4f, 6f)
        } else shape.ratio
    val isPortrait = ratio < 1f
    val fit = image.imageType == LOGO

    val providerLabel = stringResource(R.string.admin_image_fact_provider)
    val resolutionLabel = stringResource(R.string.admin_image_fact_resolution)
    val ratingLabel = stringResource(R.string.admin_image_fact_rating)
    val likesLabel = stringResource(R.string.admin_image_fact_likes)
    val languageLabel = stringResource(R.string.admin_image_fact_language)
    val sizeLabel = stringResource(R.string.admin_image_fact_size)
    val facts =
        remember(image) {
            buildList {
                image.providerName?.let { add(providerLabel to it) }
                resolutionText(image)?.let { add(resolutionLabel to it) }
                image.communityRating
                    ?.takeIf { it > 0.0 }
                    ?.let { rating ->
                        if (image.ratingIsLikes) {
                            add(likesLabel to ratingText(image, rating))
                        } else {
                            val votes = image.voteCount?.takeIf { it > 0 }?.let { " ($it)" }
                            add(ratingLabel to ratingText(image, rating) + votes.orEmpty())
                        }
                    }
                image.language
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(languageLabel to languageName(it)) }
                if (image.fileSize > 0) add(sizeLabel to formatFileSize(context, image.fileSize))
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier =
                    Modifier.widthIn(max = if (isPortrait) 280.dp else 560.dp)
                        .fillMaxWidth(if (isPortrait) 0.7f else 1f)
                        .aspectRatio(ratio)
                        .clip(CardShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Artwork(url = image.url, fit = fit)
                if (image.previewUrl != null && image.previewUrl != image.url) {
                    Artwork(url = image.previewUrl, fit = fit)
                }
            }

            if (facts.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    facts.chunked(2).forEach { rowFacts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            rowFacts.forEach { (label, value) ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            if (rowFacts.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (image.isServerImage) {
                Button(
                    onClick = onRemove,
                    enabled = enabled,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(stringResource(R.string.action_remove))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (replacesCurrent) {
                        Text(
                            text = stringResource(R.string.admin_image_replaces_current),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = enabled && image.remoteUrl != null,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(
                            stringResource(
                                if (addsToList) R.string.admin_image_add
                                else R.string.admin_image_use
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}
