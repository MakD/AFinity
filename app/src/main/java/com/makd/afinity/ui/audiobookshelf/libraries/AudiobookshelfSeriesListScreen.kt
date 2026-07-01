package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.main.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfSeriesListScreen(
    onNavigateToItem: (String) -> Unit,
    navController: NavController,
    mainUiState: MainUiState,
    widthSizeClass: WindowWidthSizeClass,
    viewModel: AudiobookshelfSeriesListViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val allSeries by viewModel.allSeries.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.abs_tab_series),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                backgroundOpacity = { 1f },
                onSearchClick = { navController.navigate(Destination.createSearchRoute()) },
                onProfileClick = { navController.navigate(Destination.createSettingsRoute()) },
                userProfileImageUrl = mainUiState.userProfileImageUrl,
                userName = mainUiState.userName,
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
