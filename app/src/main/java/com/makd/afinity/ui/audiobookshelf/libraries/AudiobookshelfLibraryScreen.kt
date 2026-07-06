package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.audiobookshelf.libraries.components.AudiobookCard
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AlphabetScroller
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.main.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfLibraryScreen(
    onNavigateToItem: (String) -> Unit,
    navController: NavController,
    mainUiState: MainUiState,
    viewModel: AudiobookshelfLibraryViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val displayItems by viewModel.displayItems.collectAsStateWithLifecycle()
    val selectedLetter by viewModel.selectedLetter.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = viewModel.libraryName,
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoading -> FullScreenLoading()

                displayItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.abs_no_items_in_library),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 16.dp + playerOffset,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(displayItems, key = { it.id }) { item ->
                                AudiobookCard(
                                    item = item,
                                    serverUrl = config?.serverUrl,
                                    onClick = { onNavigateToItem(item.id) },
                                )
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AlphabetScroller(
                                selectedLetter = selectedLetter,
                                onLetterSelected = viewModel::onLetterSelected,
                                modifier =
                                    Modifier.background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        shape = MaterialTheme.shapes.small,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
