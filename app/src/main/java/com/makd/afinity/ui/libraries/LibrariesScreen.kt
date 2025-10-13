package com.makd.afinity.ui.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import kotlin.math.min
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.theme.rememberGridMinColumnSize

@Composable
fun LibrariesScreen(
    onLibraryClick: (AfinityCollection) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: LibrariesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyGridState = rememberLazyGridState()

    val topBarOpacity by remember {
        derivedStateOf {
            val scrollOffset = lazyGridState.firstVisibleItemScrollOffset
            val maxScroll = 200f
            min(scrollOffset / maxScroll, 1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isLoading && uiState.libraries.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                ErrorMessage(
                    message = uiState.error!!,
                    modifier = Modifier.fillMaxSize()
                )
            }

            uiState.libraries.isEmpty() -> {
                EmptyLibrariesMessage(
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(rememberGridMinColumnSize()),
                    state = lazyGridState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 180.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = sortLibraries(uiState.libraries),
                        key = { library -> library.id }
                    ) { library ->
                        LibraryCard(
                            library = library,
                            onClick = {
                                viewModel.onLibraryClick(library)
                                onLibraryClick(library)
                            }
                        )
                    }
                }
            }
        }

        LibrariesTopBar(
            onSearchClick = { /* TODO: Implement search */ },
            onProfileClick = onProfileClick,
            userProfileImageUrl = uiState.userProfileImageUrl,
            backgroundOpacity = topBarOpacity,
            navController = navController
        )
    }
}

@Composable
private fun LibraryCard(
    library: AfinityCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OptimizedAsyncImage(
                        imageUrl = library.images.primaryImageUrl ?: library.images.backdropImageUrl,
                        contentDescription = library.name,
                        blurHash = library.images.primaryBlurHash ?: library.images.backdropBlurHash,
                        targetWidth = 160.dp,
                        targetHeight = 90.dp,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                }
            }

            if (library.type != CollectionType.Unknown) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = getLibraryIcon(library.type),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrariesTopBar(
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    userProfileImageUrl: String? = null,
    backgroundOpacity: Float = 0f,
    navController: NavController
) {
    TopAppBar(
        title = {
            Text(
                text = "Your Libraries",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        actions = {
            Button(
                onClick = {
                    val route = Destination.createSearchRoute()
                    navController.navigate(route)
                },
                modifier = Modifier
                    .height(48.dp)
                    .width(120.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Search",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (userProfileImageUrl != null) {
                        OptimizedAsyncImage(
                            imageUrl = userProfileImageUrl,
                            contentDescription = "Profile",
                            targetWidth = 48.dp,
                            targetHeight = 48.dp,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = backgroundOpacity)
        )
    )
}

@Composable
private fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Data will refresh automatically",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyLibrariesMessage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "No libraries found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your Jellyfin server doesn't have any libraries configured yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun getLibraryIcon(type: CollectionType): ImageVector {
    return when (type) {
        CollectionType.Movies -> Icons.Filled.Movie
        CollectionType.TvShows -> Icons.Filled.Tv
        CollectionType.Music -> Icons.Filled.LibraryMusic
        CollectionType.Books -> Icons.Filled.MenuBook
        CollectionType.HomeVideos -> Icons.Filled.VideoLibrary
        CollectionType.Playlists -> Icons.Filled.PlaylistPlay
        CollectionType.LiveTv -> Icons.Filled.LiveTv
        CollectionType.BoxSets -> Icons.Filled.CollectionsBookmark
        CollectionType.Mixed -> Icons.Filled.Apps
        CollectionType.Unknown -> Icons.Filled.Folder
    }
}

private fun getLibraryColor(type: CollectionType): Color {
    return when (type) {
        CollectionType.Movies -> Color(0xFF1976D2)
        CollectionType.TvShows -> Color(0xFF388E3C)
        CollectionType.Music -> Color(0xFF7B1FA2)
        CollectionType.Books -> Color(0xFFD32F2F)
        CollectionType.HomeVideos -> Color(0xFFF57C00)
        CollectionType.Playlists -> Color(0xFF455A64)
        CollectionType.LiveTv -> Color(0xFFE91E63)
        CollectionType.BoxSets -> Color(0xFF5D4037)
        CollectionType.Mixed -> Color(0xFF424242)
        CollectionType.Unknown -> Color(0xFF616161)
    }
}

private fun getLibraryTypeDisplayName(type: CollectionType): String {
    return when (type) {
        CollectionType.Movies -> "Movies"
        CollectionType.TvShows -> "TV Shows"
        CollectionType.Music -> "Music"
        CollectionType.Books -> "Books"
        CollectionType.HomeVideos -> "Home Videos"
        CollectionType.Playlists -> "Playlists"
        CollectionType.LiveTv -> "Live TV"
        CollectionType.BoxSets -> "Collections"
        CollectionType.Mixed -> "Mixed"
        CollectionType.Unknown -> "Library"
    }
}

private fun sortLibraries(libraries: List<AfinityCollection>): List<AfinityCollection> {
    val sortOrder = mapOf(
        CollectionType.BoxSets to 1,
        CollectionType.Movies to 2,
        CollectionType.TvShows to 3,
        CollectionType.Music to 4,
        CollectionType.HomeVideos to 5,
        CollectionType.Books to 6,
        CollectionType.Playlists to 7,
        CollectionType.LiveTv to 8,
        CollectionType.Mixed to 9,
        CollectionType.Unknown to 10
    )

    return libraries.sortedBy { library ->
        sortOrder[library.type] ?: 999
    }
}