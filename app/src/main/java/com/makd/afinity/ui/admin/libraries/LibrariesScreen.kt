package com.makd.afinity.ui.admin.libraries

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.GhostLibrary
import com.makd.afinity.data.models.admin.LibraryRemoval
import com.makd.afinity.data.models.admin.LibraryRemovalStage
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminPill
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.components.adminContentBottomPadding
import com.makd.afinity.ui.components.AfinityActionMenu
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.DetailMenuAction
import com.makd.afinity.ui.theme.CardDimensions
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.CollectionTypeOptions

internal val SCAN_POLL_INTERVAL = 3.seconds
private const val PERCENT = 100

@Composable
fun LibrariesScreen(
    onBackClick: () -> Unit,
    onOpenLibrary: (name: String) -> Unit,
    onAddLibrary: () -> Unit,
    onEditImage: (itemId: UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibrariesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isScanningAll by viewModel.isScanningAll.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val removal by viewModel.removal.collectAsStateWithLifecycle()
    val refreshQueuedMessage = stringResource(R.string.admin_refresh_queued)
    val commandFailedMessage = stringResource(R.string.session_command_failed)
    val removedMessage = stringResource(R.string.library_removed)
    val leftoverCleanedMessage = stringResource(R.string.library_removed_cleaned)
    val leftoverRemainsMessage = stringResource(R.string.library_removal_leftover)
    val removalCancelledMessage = stringResource(R.string.library_removal_cancelled)
    val removalBusyMessage = stringResource(R.string.library_removal_busy)
    val lifecycleOwner = LocalLifecycleOwner.current
    val isScanActive = isScanningAll || state.scanningCount > 0

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    LaunchedEffect(isScanActive, lifecycleOwner) {
        if (!isScanActive) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(SCAN_POLL_INTERVAL)
                viewModel.pollScanStatus()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                when (message) {
                    LibrariesMessage.REFRESH_QUEUED -> refreshQueuedMessage
                    LibrariesMessage.COMMAND_FAILED -> commandFailedMessage
                    LibrariesMessage.LIBRARY_REMOVED -> removedMessage
                    LibrariesMessage.LEFTOVER_CLEANED -> leftoverCleanedMessage
                    LibrariesMessage.LEFTOVER_REMAINS -> leftoverRemainsMessage
                    LibrariesMessage.REMOVAL_CANCELLED -> removalCancelledMessage
                    LibrariesMessage.REMOVAL_BUSY -> removalBusyMessage
                }
            )
        }
    }

    LibrariesContent(
        state = state,
        removal = removal,
        isScanningAll = isScanningAll,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onOpenLibrary = onOpenLibrary,
        onAddLibrary = onAddLibrary,
        onEditImage = onEditImage,
        onScanAll = viewModel::scanAll,
        onScanQueued = viewModel::onScanQueued,
        onRename = viewModel::rename,
        onRemove = viewModel::remove,
        onCancelRemoval = viewModel::cancelRemoval,
        onCleanUp = viewModel::cleanUp,
        modifier = modifier,
    )
}

@Composable
private fun LibrariesContent(
    state: LibrariesUiState,
    removal: LibraryRemoval?,
    isScanningAll: Boolean,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenLibrary: (name: String) -> Unit,
    onAddLibrary: () -> Unit,
    onEditImage: (itemId: UUID) -> Unit,
    onScanAll: () -> Unit,
    onScanQueued: () -> Unit,
    onRename: (name: String, newName: String) -> Unit,
    onRemove: (name: String) -> Unit,
    onCancelRemoval: () -> Unit,
    onCleanUp: (GhostLibrary) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var removeTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var scanTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val libraryNames = state.libraries.orEmpty().map { it.name }

    scanTarget
        ?.let { name -> state.libraries.orEmpty().firstOrNull { it.name == name } }
        ?.let { library ->
            library.itemId?.let { itemId ->
                ScanLibraryDialog(
                    itemId = itemId,
                    name = library.name,
                    type = library.type,
                    onQueued = onScanQueued,
                    onDismiss = { scanTarget = null },
                )
            }
        }

    renameTarget
        ?.takeIf { it in libraryNames }
        ?.let { name ->
            RenameLibraryDialog(
                currentName = name,
                onConfirm = { newName ->
                    onRename(name, newName)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null },
            )
        }

    removeTarget
        ?.takeIf { it in libraryNames }
        ?.let { name ->
            RemoveLibraryDialog(
                name = name,
                onConfirm = {
                    onRemove(name)
                    removeTarget = null
                },
                onDismiss = { removeTarget = null },
            )
        }

    AdminScaffold(
        title = stringResource(R.string.libraries_title),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (state.libraries != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddLibrary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.library_add))
                }
            }
        },
    ) { contentPadding ->
        AdminLoadContent(
            data = state.libraries,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { libraries ->
            LazyVerticalGrid(
                columns = CardDimensions.landscapeGridCells(followCardSize = false),
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = adminContentBottomPadding(hasFloatingButton = true),
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    LibrariesHeader(
                        total = libraries.size,
                        scanning = state.scanningCount,
                        isScanningAll = isScanningAll,
                        canScan = libraries.isNotEmpty(),
                        onScanAll = onScanAll,
                    )
                }
                removal?.let { current ->
                    item(key = "removal", span = { GridItemSpan(maxLineSpan) }) {
                        RemovalBanner(removal = current, onCancel = onCancelRemoval)
                    }
                }
                state.ghosts
                    .filterNot { it.name.equals(removal?.name, ignoreCase = true) }
                    .forEach { ghost ->
                        item(key = "ghost_${ghost.name}", span = { GridItemSpan(maxLineSpan) }) {
                            GhostLibraryCard(
                                ghost = ghost,
                                enabled = removal == null,
                                onCleanUp = { onCleanUp(ghost) },
                            )
                        }
                    }
                if (libraries.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.libraries_admin_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }
                items(items = libraries, key = { it.key }) { library ->
                    AdminLibraryCard(
                        library = library,
                        enabled = !state.isBusy && removal == null,
                        onClick = { onOpenLibrary(library.name) },
                        onScan = { if (library.itemId != null) scanTarget = library.name },
                        onEditImage = { library.itemId?.let(onEditImage) },
                        onRename = { renameTarget = library.name },
                        onRemove = { removeTarget = library.name },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrariesHeader(
    total: Int,
    scanning: Int,
    isScanningAll: Boolean,
    canScan: Boolean,
    onScanAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdminPill(text = stringResource(R.string.libraries_count_fmt, total))
            if (scanning > 0) {
                AdminPill(
                    text = stringResource(R.string.libraries_scanning_fmt, scanning),
                    emphasised = true,
                )
            }
        }
        FilledTonalButton(
            onClick = onScanAll,
            enabled = canScan && !isScanningAll,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.heightIn(min = 36.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text =
                    if (isScanningAll) stringResource(R.string.library_scanning)
                    else stringResource(R.string.libraries_scan_all)
            )
        }
    }
}

@Composable
private fun RemovalBanner(removal: LibraryRemoval, onCancel: () -> Unit) {
    val text =
        when (removal.stage) {
            LibraryRemovalStage.WAITING_FOR_SCAN ->
                stringResource(R.string.library_removal_waiting, removal.name)
            LibraryRemovalStage.REMOVING ->
                stringResource(R.string.library_removal_removing, removal.name)
            LibraryRemovalStage.CHECKING ->
                stringResource(R.string.library_removal_checking, removal.name)
            LibraryRemovalStage.CLEANING_UP ->
                stringResource(R.string.library_removal_cleaning, removal.name)
        }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (removal.stage == LibraryRemovalStage.WAITING_FOR_SCAN) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                }
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

@Composable
private fun GhostLibraryCard(ghost: GhostLibrary, enabled: Boolean, onCleanUp: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.library_ghost_title, ghost.name),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.library_ghost_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onCleanUp, enabled = enabled) {
                Text(
                    text = stringResource(R.string.library_ghost_clean_up),
                    color =
                        if (enabled) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}

@Composable
private fun AdminLibraryCard(
    library: AdminLibraryRow,
    enabled: Boolean,
    onClick: () -> Unit,
    onScan: () -> Unit,
    onEditImage: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val isScanning = library.scanState != LibraryScanState.IDLE
    val status =
        when (library.scanState) {
            LibraryScanState.ACTIVE ->
                library.scanProgress?.let { progress ->
                    stringResource(
                        R.string.library_scanning_progress_fmt,
                        (progress * PERCENT).roundToInt(),
                    )
                } ?: stringResource(R.string.library_scanning)
            LibraryScanState.QUEUED -> stringResource(R.string.library_scan_queued)
            LibraryScanState.IDLE ->
                stringResource(
                    R.string.library_type_folders_fmt,
                    stringResource(library.type.labelRes()),
                    library.locations.size,
                )
        }

    Column(
        modifier =
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryCover(library = library)
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = status,
                    style =
                        if (isScanning) {
                            MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                    color =
                        if (isScanning) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LibraryMenu(
                enabled = enabled,
                hasItem = library.itemId != null,
                onScan = onScan,
                onEditImage = onEditImage,
                onRename = onRename,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun LibraryCover(library: AdminLibraryRow) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE)) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (library.imageUrl != null) {
                    AsyncImage(
                        imageUrl = library.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        painter = painterResource(id = library.type.iconRes()),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
                if (library.scanState != LibraryScanState.IDLE) {
                    val barModifier =
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
                    val progress = library.scanProgress
                    if (library.scanState == LibraryScanState.ACTIVE && progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = barModifier)
                    } else {
                        LinearProgressIndicator(modifier = barModifier)
                    }
                }
            }
        }
        if (library.imageUrl != null) {
            Icon(
                painter = painterResource(id = library.type.iconRes()),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(20.dp),
            )
        }
    }
}

@Composable
private fun LibraryMenu(
    enabled: Boolean,
    hasItem: Boolean,
    onScan: () -> Unit,
    onEditImage: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp),
            enabled = enabled,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_dots_vertical),
                contentDescription = stringResource(R.string.cd_library_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        AfinityActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            actions =
                buildList {
                    if (hasItem) {
                        add(
                            DetailMenuAction(
                                label = stringResource(R.string.library_scan),
                                iconRes = R.drawable.ic_refresh,
                                onClick = onScan,
                            )
                        )
                        add(
                            DetailMenuAction(
                                label = stringResource(R.string.library_edit_image),
                                iconRes = R.drawable.ic_photo_edit,
                                onClick = onEditImage,
                            )
                        )
                    }
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.action_rename),
                            iconRes = R.drawable.ic_edit,
                            onClick = onRename,
                        )
                    )
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.action_remove),
                            iconRes = R.drawable.ic_delete,
                            onClick = onRemove,
                            destructive = true,
                        )
                    )
                },
        )
    }
}

@DrawableRes
private fun CollectionTypeOptions?.iconRes(): Int =
    when (this) {
        CollectionTypeOptions.MOVIES -> R.drawable.ic_movie
        CollectionTypeOptions.TVSHOWS -> R.drawable.ic_tv
        CollectionTypeOptions.MUSIC -> R.drawable.ic_music
        CollectionTypeOptions.MUSICVIDEOS -> R.drawable.ic_music_video
        CollectionTypeOptions.HOMEVIDEOS -> R.drawable.ic_video
        CollectionTypeOptions.BOXSETS -> R.drawable.ic_collection
        CollectionTypeOptions.BOOKS -> R.drawable.ic_book
        CollectionTypeOptions.MIXED,
        null -> R.drawable.ic_mixed
    }

@StringRes
private fun CollectionTypeOptions?.labelRes(): Int =
    when (this) {
        CollectionTypeOptions.MOVIES -> R.string.library_type_movies
        CollectionTypeOptions.TVSHOWS -> R.string.library_type_shows
        CollectionTypeOptions.MUSIC -> R.string.library_type_music
        CollectionTypeOptions.MUSICVIDEOS -> R.string.library_type_music_videos
        CollectionTypeOptions.HOMEVIDEOS -> R.string.library_type_home_videos
        CollectionTypeOptions.BOXSETS -> R.string.library_type_collections
        CollectionTypeOptions.BOOKS -> R.string.library_type_books
        CollectionTypeOptions.MIXED,
        null -> R.string.library_type_mixed
    }
