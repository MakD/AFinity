package com.makd.afinity.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.RequestConfirmationDialog
import com.makd.afinity.ui.theme.CardDimensions.gridMinSize
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    onGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isJellyseerrAuthenticated by viewModel.isJellyseerrAuthenticated.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        SearchTopBar(
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onBackClick = onBackClick,
            focusRequester = focusRequester,
            onSearch = {
                keyboardController?.hide()
                if (uiState.isJellyseerrSearchMode) {
                    viewModel.performJellyseerrSearch()
                } else {
                    viewModel.performSearch()
                }
            }
        )

        if (uiState.libraries.isNotEmpty() || isJellyseerrAuthenticated) {
            HorizontalLibraryFilters(
                libraries = uiState.libraries,
                selectedLibrary = uiState.selectedLibrary,
                isJellyseerrAuthenticated = isJellyseerrAuthenticated,
                isJellyseerrSearchMode = uiState.isJellyseerrSearchMode,
                onLibrarySelected = viewModel::selectLibrary,
                onJellyseerrSearchSelected = viewModel::selectJellyseerrSearchMode,
                onJellyfinSearchSelected = viewModel::selectJellyfinSearchMode
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            when {
                uiState.searchQuery.isEmpty() && !uiState.isSearching && !uiState.isJellyseerrSearching -> {
                    SearchHomeContent(
                        genres = uiState.genres,
                        onGenreClick = onGenreClick,
                        widthSizeClass = widthSizeClass
                    )
                }

                uiState.isSearching || uiState.isJellyseerrSearching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.isJellyseerrSearchMode && uiState.jellyseerrSearchResults.isNotEmpty() -> {
                    JellyseerrSearchResultsContent(
                        results = uiState.jellyseerrSearchResults,
                        onRequestClick = { item ->
                            item.getMediaType()?.let { mediaType ->
                                viewModel.showRequestDialog(
                                    tmdbId = item.id,
                                    mediaType = mediaType,
                                    title = item.getDisplayTitle(),
                                    posterUrl = item.getPosterUrl(),
                                    availableSeasons = 0,
                                    existingStatus = item.getDisplayStatus()
                                )
                            }
                        }
                    )
                }

                uiState.searchResults.isNotEmpty() -> {
                    SearchResultsContent(
                        results = uiState.searchResults,
                        onItemClick = onItemClick
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No results found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (uiState.showRequestDialog && uiState.pendingRequest != null) {
            RequestConfirmationDialog(
                mediaTitle = uiState.pendingRequest!!.title,
                mediaPosterUrl = uiState.pendingRequest!!.posterUrl,
                mediaType = uiState.pendingRequest!!.mediaType,
                availableSeasons = uiState.pendingRequest!!.availableSeasons,
                selectedSeasons = uiState.selectedSeasons,
                onSeasonsChange = { viewModel.setSelectedSeasons(it) },
                disabledSeasons = uiState.disabledSeasons,
                existingStatus = uiState.pendingRequest!!.existingStatus,
                isLoading = uiState.isCreatingRequest,
                onConfirm = { viewModel.confirmRequest() },
                onDismiss = { viewModel.dismissRequestDialog() },
                mediaBackdropUrl = uiState.pendingRequest!!.backdropUrl,
                mediaTagline = uiState.pendingRequest!!.tagline,
                mediaOverview = uiState.pendingRequest!!.overview,
                releaseDate = uiState.pendingRequest!!.releaseDate,
                runtime = uiState.pendingRequest!!.runtime,
                voteAverage = uiState.pendingRequest!!.voteAverage,
                certification = uiState.pendingRequest!!.certification,
                originalLanguage = uiState.pendingRequest!!.originalLanguage,
                director = uiState.pendingRequest!!.director,
                genres = uiState.pendingRequest!!.genres,
                ratingsCombined = uiState.pendingRequest!!.ratingsCombined
            )
        }
    }
}

@Composable
private fun SearchTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    focusRequester: FocusRequester,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_left),
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = "Search movies and TV shows",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_clear),
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
private fun HorizontalLibraryFilters(
    libraries: List<AfinityCollection>,
    selectedLibrary: AfinityCollection?,
    isJellyseerrAuthenticated: Boolean,
    isJellyseerrSearchMode: Boolean,
    onLibrarySelected: (AfinityCollection?) -> Unit,
    onJellyseerrSearchSelected: () -> Unit,
    onJellyfinSearchSelected: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        item {
            LibraryFilterChip(
                text = "All",
                isSelected = !isJellyseerrSearchMode && selectedLibrary == null,
                onClick = {
                    onJellyfinSearchSelected()
                    onLibrarySelected(null)
                },
                showCheckIcon = true
            )
        }

        if (isJellyseerrAuthenticated) {
            item {
                LibraryFilterChip(
                    text = "Request",
                    isSelected = isJellyseerrSearchMode,
                    onClick = onJellyseerrSearchSelected,
                    showCheckIcon = false
                )
            }
        }

        items(libraries) { library ->
            LibraryFilterChip(
                text = library.name,
                isSelected = !isJellyseerrSearchMode && selectedLibrary?.id == library.id,
                onClick = {
                    onJellyfinSearchSelected()
                    onLibrarySelected(library)
                },
                showCheckIcon = false
            )
        }
    }
}

@Composable
private fun SearchHomeContent(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    widthSizeClass: WindowWidthSizeClass
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Explore Genres",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(widthSizeClass.gridMinSize),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(genres) { genre ->
                GenreCard(
                    genre = genre,
                    onClick = { onGenreClick(genre) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    results: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit
) {
    LocalSoftwareKeyboardController.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "${results.size} results found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(results) { item ->
            SearchResultItem(
                item = item,
                onClick = { onItemClick(item) }
            )
        }
    }
}

@Composable
private fun LibraryFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    showCheckIcon: Boolean = false
) {
    FilterChip(
        onClick = onClick,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium
            )
        },
        selected = isSelected,
        leadingIcon = if (isSelected && showCheckIcon) {
            {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun GenreCard(
    genre: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = getGenreIcon(genre),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = genre,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    item: AfinityItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    AsyncImage(
                        imageUrl = item.images.primaryImageUrl,
                        contentDescription = item.name,
                        blurHash = item.images.primaryBlurHash,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (item.played) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = "Watched",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = when (item) {
                            is AfinityMovie -> "Movie"
                            is AfinityShow -> "TV Show"
                            else -> "Media"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when (item) {
                        is AfinityMovie -> item.productionYear
                        is AfinityShow -> item.productionYear
                        else -> null
                    }?.let { year ->
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    when (item) {
                        is AfinityMovie -> item.communityRating
                        is AfinityShow -> item.communityRating
                        else -> null
                    }?.let { rating ->
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_imdb_logo),
                            contentDescription = "IMDB",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", rating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (item is AfinityMovie) {
                        item.criticRating?.let { rtRating ->
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                painter = painterResource(
                                    id = if (rtRating > 60) {
                                        R.drawable.ic_rotten_tomato_fresh
                                    } else {
                                        R.drawable.ic_rotten_tomato_rotten
                                    }
                                ),
                                contentDescription = "Rotten Tomatoes",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${rtRating.toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item.overview?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun JellyseerrSearchResultsContent(
    results: List<SearchResultItem>,
    onRequestClick: (SearchResultItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "${results.size} results found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(results) { item ->
            JellyseerrSearchResultItem(
                item = item,
                onRequestClick = { onRequestClick(item) }
            )
        }
    }
}

@Composable
private fun JellyseerrSearchResultItem(
    item: SearchResultItem,
    onRequestClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                AsyncImage(
                    imageUrl = item.getPosterUrl(),
                    contentDescription = item.getDisplayTitle(),
                    blurHash = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.getDisplayTitle(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.getMediaType()?.name ?: "UNKNOWN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    item.releaseDate?.let { releaseDate ->
                        if (releaseDate.length >= 4) {
                            val year = releaseDate.take(4)
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = year,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item.overview?.let { overview ->
                    if (overview.isNotBlank()) {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val status = item.getDisplayStatus()
            val canRequest = !item.hasExistingRequest() || status == MediaStatus.PARTIALLY_AVAILABLE

            if (item.hasExistingRequest() && status != null && status != MediaStatus.PARTIALLY_AVAILABLE) {
                Surface(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    shape = RoundedCornerShape(16.dp),
                    color = when (status) {
                        MediaStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                        MediaStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                        MediaStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                        MediaStatus.PARTIALLY_AVAILABLE -> MaterialTheme.colorScheme.secondary
                        MediaStatus.AVAILABLE -> MaterialTheme.colorScheme.secondary
                        MediaStatus.DELETED -> MaterialTheme.colorScheme.error
                    }
                ) {
                    Text(
                        text = MediaStatus.getDisplayName(status),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (status) {
                            MediaStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                            MediaStatus.PENDING -> MaterialTheme.colorScheme.onTertiary
                            MediaStatus.PROCESSING -> MaterialTheme.colorScheme.onPrimary
                            MediaStatus.PARTIALLY_AVAILABLE -> MaterialTheme.colorScheme.onSecondary
                            MediaStatus.AVAILABLE -> MaterialTheme.colorScheme.onSecondary
                            MediaStatus.DELETED -> MaterialTheme.colorScheme.onError
                        }
                    )
                }
            }

            if (canRequest) {
                IconButton(
                    onClick = onRequestClick,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Request",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun getGenreIcon(genre: String): Painter {
    return when (genre.lowercase()) {
        "action" -> painterResource(id = R.drawable.ic_boom)
        "comedy" -> painterResource(id = R.drawable.ic_comedy)
        "drama" -> painterResource(id = R.drawable.ic_theater)
        "horror" -> painterResource(id = R.drawable.ic_horror)
        "thriller" -> painterResource(id = R.drawable.ic_bolt)
        "romance" -> painterResource(id = R.drawable.ic_favorite)
        "sci-fi", "science fiction" -> painterResource(id = R.drawable.ic_alien)
        "fantasy" -> painterResource(id = R.drawable.ic_auto_awesome)
        "documentary" -> painterResource(id = R.drawable.ic_article)
        "animation" -> painterResource(id = R.drawable.ic_animation)
        "family" -> painterResource(id = R.drawable.ic_family)
        "adventure" -> painterResource(id = R.drawable.ic_adventure)
        "crime" -> painterResource(id = R.drawable.ic_security)
        "mystery" -> painterResource(id = R.drawable.ic_mystery)
        "western" -> painterResource(id = R.drawable.ic_cactus)
        "war" -> painterResource(id = R.drawable.ic_war)
        "music" -> painterResource(id = R.drawable.ic_music_heart)
        "sport" -> painterResource(id = R.drawable.ic_sports)
        "biography" -> painterResource(id = R.drawable.ic_person_heart)
        "history" -> painterResource(id = R.drawable.ic_history)
        else -> painterResource(id = R.drawable.ic_movie)
    }
}