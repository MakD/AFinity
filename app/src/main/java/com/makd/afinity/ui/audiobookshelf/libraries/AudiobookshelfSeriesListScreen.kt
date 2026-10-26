package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AppBarProfile
import com.makd.afinity.ui.main.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfSeriesListScreen(
    onNavigateToItem: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    mainUiState: MainUiState,
    widthSizeClass: WindowWidthSizeClass,
    viewModel: AudiobookshelfSeriesListViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val allSeries by viewModel.allSeries.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = { AfinityTopAppBarTitle(stringResource(R.string.abs_tab_series)) },
                backgroundOpacity = backgroundOpacity,
                onSearchClick = onSearchClick,
                profile =
                    AppBarProfile(
                        onClick = onProfileClick,
                        name = mainUiState.userName,
                        imageUrl = mainUiState.userProfileImageUrl,
                    ),
            )
        }
    ) { innerPadding ->
        AudiobookshelfSeriesTab(
            seriesList = allSeries,
            serverUrl = config?.serverUrl,
            onItemClick = { item -> onNavigateToItem(item.id) },
            isLoading = isLoading,
            widthSizeClass = widthSizeClass,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
