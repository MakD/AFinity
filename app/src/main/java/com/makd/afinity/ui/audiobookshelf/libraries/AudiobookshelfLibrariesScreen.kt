package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.audiobookshelf.library.components.AudiobookCard
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.main.MainUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfLibrariesScreen(
    onNavigateToItem: (String) -> Unit,
    onNavigateToLogin: () -> Unit,
    navController: NavController,
    mainUiState: MainUiState,
    widthSizeClass: WindowWidthSizeClass,
    viewModel: AudiobookshelfLibrariesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val personalizedSections by viewModel.personalizedSections.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val allSeries by viewModel.allSeries.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()

    if (!isAuthenticated) {
        onNavigateToLogin()
        return
    }

    val tabCount = 2 + libraries.size
    val pagerState = rememberPagerState(pageCount = { tabCount })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage >= 2) {
            val libraryIndex = pagerState.currentPage - 2
            if (libraryIndex < libraries.size) {
                viewModel.loadLibraryItems(libraries[libraryIndex].id)
            }
        }
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
            if (libraries.isEmpty() && !uiState.isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            text = "No libraries found",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            NavigationChip(
                                selected = pagerState.currentPage == 0,
                                label = "Home",
                                iconResId = R.drawable.ic_home_filled,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(0)
                                    }
                                }
                            )
                        }
                        item {
                            NavigationChip(
                                selected = pagerState.currentPage == 1,
                                label = "Series",
                                iconResId = R.drawable.ic_books_filled,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                }
                            )
                        }
                        itemsIndexed(libraries) { index, library ->
                            NavigationChip(
                                selected = pagerState.currentPage == index + 2,
                                label = library.name,
                                iconResId = R.drawable.ic_headphones_filled,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index + 2)
                                    }
                                }
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> {
                                AudiobookshelfHomeTab(
                                    sections = personalizedSections,
                                    serverUrl = config?.serverUrl,
                                    onItemClick = { item -> onNavigateToItem(item.id) },
                                    isLoading = uiState.isLoadingPersonalized,
                                    widthSizeClass = widthSizeClass
                                )
                            }

                            1 -> {
                                AudiobookshelfSeriesTab(
                                    seriesList = allSeries,
                                    serverUrl = config?.serverUrl,
                                    onItemClick = { item -> onNavigateToItem(item.id) },
                                    isLoading = uiState.isLoadingSeries,
                                    widthSizeClass = widthSizeClass
                                )
                            }

                            else -> {
                                val libraryIndex = page - 2
                                if (libraryIndex < libraries.size) {
                                    val library = libraries[libraryIndex]
                                    val items = libraryItems[library.id]

                                    if (items == null) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    } else if (items.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No items in library",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(minSize = 140.dp),
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(items, key = { it.id }) { item ->
                                                AudiobookCard(
                                                    item = item,
                                                    serverUrl = config?.serverUrl,
                                                    onClick = { onNavigateToItem(item.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationChip(
    selected: Boolean,
    label: String,
    iconResId: Int,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        shape = CircleShape,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(
                alpha = 0.3f
            ),
            selectedBorderColor = Color.Transparent
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}
