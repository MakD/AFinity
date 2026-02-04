package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.makd.afinity.ui.components.AfinityTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.data.repository.ItemWithProgress
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.audiobookshelf.libraries.components.LibraryCard
import com.makd.afinity.ui.main.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfLibrariesScreen(
    onNavigateToLibrary: (String) -> Unit,
    onNavigateToItem: (String) -> Unit,
    onNavigateToLogin: () -> Unit,
    navController: NavController,
    mainUiState: MainUiState,
    viewModel: AudiobookshelfLibrariesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val inProgressItems by viewModel.inProgressItems.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()

    if (!isAuthenticated) {
        onNavigateToLogin()
        return
    }

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = "Audiobooks",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                onSearchClick = {
                    navController.navigate(Destination.createSearchRoute())
                },
                onProfileClick = {
                    navController.navigate(Destination.createSettingsRoute())
                },
                userProfileImageUrl = mainUiState.userProfileImageUrl
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (libraries.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (inProgressItems.isNotEmpty()) {
                        item {
                            Text(
                                text = "Continue Listening",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        item {
                            ContinueListeningRow(
                                items = inProgressItems,
                                onItemClick = { onNavigateToItem(it.item.id) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    item {
                        Text(
                            text = "Libraries",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(libraries, key = { it.id }) { library ->
                        LibraryCard(
                            library = library,
                            onClick = { onNavigateToLibrary(library.id) }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.error ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No libraries found",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pull to refresh",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContinueListeningRow(
    items: List<ItemWithProgress>,
    onItemClick: (ItemWithProgress) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.item.id }) { itemWithProgress ->
            ContinueListeningCard(
                item = itemWithProgress,
                onClick = { onItemClick(itemWithProgress) }
            )
        }
    }
}

@Composable
private fun ContinueListeningCard(
    item: ItemWithProgress,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(160.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = item.item.media.metadata.title ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )

            item.item.media.metadata.authorName?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            item.progress?.let { progress ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${(progress.progress * 100).toInt()}% complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
