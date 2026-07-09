package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.settings.AudiobookshelfBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfLibrariesScreen(
    onNavigateToItem: (String) -> Unit,
    navController: NavController,
    mainUiState: MainUiState,
    widthSizeClass: WindowWidthSizeClass,
    viewModel: AudiobookshelfLibrariesViewModel = hiltViewModel(),
    onMenuClick: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val personalizedSections by viewModel.personalizedSections.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()

    if (!isAuthenticated) {
        var showLoginSheet by remember { mutableStateOf(true) }
        val loginSheetState = rememberModalBottomSheetState()

        if (showLoginSheet) {
            AudiobookshelfBottomSheet(
                onDismiss = { showLoginSheet = false },
                sheetState = loginSheetState,
            )
        }

        Scaffold(
            topBar = {
                AfinityTopAppBar(
                    title = {
                        Text(
                            text = "Audiobooks",
                            style =
                                MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    },
                    onMenuClick = onMenuClick,
                    onSearchClick = { navController.navigate(Destination.createSearchRoute()) },
                    onProfileClick = { navController.navigate(Destination.createSettingsRoute()) },
                    userProfileImageUrl = mainUiState.userProfileImageUrl,
                    userName = mainUiState.userName,
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.abs_connect_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Button(onClick = { showLoginSheet = true }) {
                        Text(stringResource(R.string.abs_connect_button))
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = "Audiobooks",
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                onMenuClick = onMenuClick,
                onSearchClick = { navController.navigate(Destination.createSearchRoute()) },
                onProfileClick = { navController.navigate(Destination.createSettingsRoute()) },
                userProfileImageUrl = mainUiState.userProfileImageUrl,
                userName = mainUiState.userName,
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (libraries.isEmpty() && uiState.isRefreshing) {
                FullScreenLoading()
            } else if (libraries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.abs_no_libraries_found),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                AudiobookshelfHomeTab(
                    sections = personalizedSections,
                    libraries = libraries,
                    serverUrl = config?.serverUrl,
                    onItemClick = { item -> onNavigateToItem(item.id) },
                    onBrowseSeries = {
                        navController.navigate(Destination.createAudiobookshelfSeriesListRoute())
                    },
                    onBrowseLibrary = { library ->
                        navController.navigate(
                            Destination.createAudiobookshelfLibraryRoute(library.id, library.name)
                        )
                    },
                    isLoading = uiState.isRefreshing && personalizedSections.isEmpty(),
                    widthSizeClass = widthSizeClass,
                )
            }

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                ) {
                    Text(
                        text = uiState.error ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}