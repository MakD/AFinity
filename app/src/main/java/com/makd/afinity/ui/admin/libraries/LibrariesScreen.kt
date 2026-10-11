package com.makd.afinity.ui.admin.libraries

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminPill
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import org.jellyfin.sdk.model.api.CollectionTypeOptions

@Composable
fun LibrariesScreen(
    onBackClick: () -> Unit,
    onOpenLibrary: (name: String) -> Unit,
    onAddLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibrariesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    LibrariesContent(
        state = state,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onOpenLibrary = onOpenLibrary,
        onAddLibrary = onAddLibrary,
        modifier = modifier,
    )
}

@Composable
private fun LibrariesContent(
    state: LibrariesUiState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenLibrary: (name: String) -> Unit,
    onAddLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdminScaffold(
        title = stringResource(R.string.libraries_title),
        onBackClick = onBackClick,
        modifier = modifier,
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            ) {
                if (libraries.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.libraries_admin_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                        )
                    }
                } else {
                    item(key = "libraries") {
                        SettingsGroup {
                            libraries.forEachIndexed { index, library ->
                                if (index > 0) SettingsDivider()
                                LibraryRow(
                                    library = library,
                                    onClick = { onOpenLibrary(library.name) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(library: AdminLibraryRow, onClick: () -> Unit) {
    val scanLabel =
        when (library.scanState) {
            LibraryScanState.ACTIVE -> stringResource(R.string.library_scanning)
            LibraryScanState.QUEUED -> stringResource(R.string.library_scan_queued)
            LibraryScanState.IDLE -> null
        }
    val scanPill: (@Composable () -> Unit)? =
        if (scanLabel != null) {
            {
                AdminPill(
                    text = scanLabel,
                    emphasised = library.scanState == LibraryScanState.ACTIVE,
                )
            }
        } else {
            null
        }
    SettingsItem(
        icon = painterResource(id = library.type.iconRes()),
        title = library.name,
        subtitle = library.locations.joinToString(", ").ifEmpty { null },
        onClick = onClick,
        trailing = scanPill,
    )
}

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
