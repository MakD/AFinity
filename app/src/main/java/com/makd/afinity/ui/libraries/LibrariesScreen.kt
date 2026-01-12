package com.makd.afinity.ui.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions.gridMinSize
import kotlin.math.min

@Composable
fun LibrariesScreen(
    mainUiState: MainUiState,
    onLibraryClick: (AfinityCollection) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: LibrariesViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass
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
                    columns = GridCells.Adaptive(widthSizeClass.gridMinSize),
                    state = lazyGridState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 180.dp,
                        bottom = 16.dp
                    ),
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

        AfinityTopAppBar(
            title = {
                Text(
                    text = "Libraries",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            backgroundOpacity = topBarOpacity,
            onSearchClick = {
                val route = Destination.createSearchRoute()
                navController.navigate(route)
            },
            onProfileClick = onProfileClick,
            userProfileImageUrl = mainUiState.userProfileImageUrl
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick() }),
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
                    AsyncImage(
                        imageUrl = library.images.primaryImageUrl
                            ?: library.images.backdropImageUrl,
                        contentDescription = library.name,
                        blurHash = library.images.primaryBlurHash
                            ?: library.images.backdropBlurHash,
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
                        .padding(6.dp)
                ) {
                    Icon(
                        painter = getLibraryIcon(library.type),
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

@Composable
private fun getLibraryIcon(type: CollectionType): Painter {
    return when (type) {
        CollectionType.Movies -> painterResource(id = R.drawable.ic_movie)
        CollectionType.TvShows -> painterResource(id = R.drawable.ic_tv)
        CollectionType.Music -> painterResource(id = R.drawable.ic_music)
        CollectionType.Books -> painterResource(id = R.drawable.ic_books)
        CollectionType.HomeVideos -> painterResource(id = R.drawable.ic_music_video)
        CollectionType.Playlists -> painterResource(id = R.drawable.ic_playlist)
        CollectionType.LiveTv -> painterResource(id = R.drawable.ic_live_tv)
        CollectionType.BoxSets -> painterResource(id = R.drawable.ic_collections_bookmark)
        CollectionType.Mixed -> painterResource(id = R.drawable.ic_mixed)
        CollectionType.Unknown -> painterResource(id = R.drawable.ic_folder)
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