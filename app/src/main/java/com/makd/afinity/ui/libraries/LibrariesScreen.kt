package com.makd.afinity.ui.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LibraryCard
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions.gridMinSize
import com.makd.afinity.ui.utils.rememberTopBarOpacity

@Composable
fun LibrariesScreen(
    mainUiState: MainUiState,
    onLibraryClick: (AfinityCollection) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: LibrariesViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
    onMenuClick: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyGridState = rememberLazyGridState()

    val topBarOpacity by rememberTopBarOpacity(lazyGridState)

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                    columns = GridCells.Adaptive(widthSizeClass.gridMinSize),
                    state = lazyGridState,
                    contentPadding =
                        PaddingValues(start = 14.dp, end = 14.dp, top = 180.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier.fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                            ),
                ) {
                    items(
                        items = sortLibraries(uiState.libraries),
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

        AfinityTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.libraries_title),
                    style =
                        MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            backgroundOpacity = { topBarOpacity },
            onMenuClick = onMenuClick,
            onSearchClick = {
                val route = Destination.createSearchRoute()
                navController.navigate(route)
            },
            onProfileClick = onProfileClick,
            userProfileImageUrl = mainUiState.userProfileImageUrl,
            userName = mainUiState.userName,
        )
    }
}

private fun sortLibraries(libraries: List<AfinityCollection>): List<AfinityCollection> {
    val sortOrder =
        mapOf(
            CollectionType.BoxSets to 1,
            CollectionType.Movies to 2,
            CollectionType.TvShows to 3,
            CollectionType.Music to 4,
            CollectionType.HomeVideos to 5,
            CollectionType.Books to 6,
            CollectionType.Playlists to 7,
            CollectionType.LiveTv to 8,
            CollectionType.Mixed to 9,
            CollectionType.Unknown to 10,
        )

    return libraries.sortedBy { library -> sortOrder[library.type] ?: 999 }
}
