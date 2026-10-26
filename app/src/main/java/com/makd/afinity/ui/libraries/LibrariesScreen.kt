package com.makd.afinity.ui.libraries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AppBarProfile
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LibraryCard
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun LibrariesScreen(
    mainUiState: MainUiState,
    onLibraryClick: (AfinityCollection) -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibrariesViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
    onMenuClick: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyGridState = rememberLazyGridState()
    val playerOffset = LocalPlayerOffset.current

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = { AfinityTopAppBarTitle(stringResource(R.string.libraries_title)) },
                backgroundOpacity = backgroundOpacity,
                onMenuClick = onMenuClick,
                onSearchClick = onSearchClick,
                profile =
                    AppBarProfile(
                        onClick = onProfileClick,
                        name = mainUiState.userName,
                        imageUrl = mainUiState.userProfileImageUrl,
                    ),
            )
        },
        modifier = modifier,
    ) {
        when {
            uiState.isLoading && uiState.libraries.isEmpty() -> {
                FullScreenLoading()
            }

            uiState.error != null -> {
                FullScreenError(message = uiState.error!!)
            }

            uiState.libraries.isEmpty() -> {
                FullScreenEmpty(
                    title = stringResource(R.string.libraries_empty_title),
                    message = stringResource(R.string.libraries_empty_message),
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = CardDimensions.tileGridCells(widthSizeClass),
                    state = lazyGridState,
                    contentPadding =
                        PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = 16.dp,
                            bottom = 16.dp + playerOffset,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier.fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                            ),
                ) {
                    items(
                        items = uiState.libraries,
                        key = { library -> library.id },
                    ) { library ->
                        LibraryCard(
                            library = library,
                            modifier = Modifier.fillMaxWidth(),
                            targetWidth = 160.dp,
                            targetHeight = 90.dp,
                            onClick = {
                                viewModel.onLibraryClick(library)
                                onLibraryClick(library)
                            },
                        )
                    }
                }
            }
        }
    }
}
