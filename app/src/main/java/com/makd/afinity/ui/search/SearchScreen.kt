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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.Permissions
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.hasPermission
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
    onAudiobookshelfItemClick: (String) -> Unit,
    onAudiobookshelfGenreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isJellyseerrAuthenticated by
        viewModel.isJellyseerrAuthenticated.collectAsStateWithLifecycle()
    val isAudiobookshelfAuthenticated by
        viewModel.isAudiobookshelfAuthenticated.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LocalFocusManager.current

    Column(
        modifier =
            modifier
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
                when {
                    uiState.isJellyseerrSearchMode -> viewModel.performJellyseerrSearch()
                    uiState.isAudiobookshelfSearchMode -> viewModel.performAudiobookshelfSearch()
                    else -> {
                        viewModel.performSearch()
                        viewModel.performAudiobookshelfSearch()
                    }
                }
            },
        )

        if (
            uiState.libraries.isNotEmpty() ||
                isJellyseerrAuthenticated ||
                isAudiobookshelfAuthenticated
        ) {
            HorizontalLibraryFilters(
                libraries = uiState.libraries,
                selectedLibrary = uiState.selectedLibrary,
                isJellyseerrAuthenticated = isJellyseerrAuthenticated,
                isJellyseerrSearchMode = uiState.isJellyseerrSearchMode,
                isAudiobookshelfAuthenticated = isAudiobookshelfAuthenticated,
                isAudiobookshelfSearchMode = uiState.isAudiobookshelfSearchMode,
                onLibrarySelected = viewModel::selectLibrary,
                onJellyseerrSearchSelected = viewModel::selectJellyseerrSearchMode,
                onJellyfinSearchSelected = viewModel::selectJellyfinSearchMode,
                onAudiobookshelfSearchSelected = viewModel::selectAudiobookshelfSearchMode,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val isAllMode =
                !uiState.isJellyseerrSearchMode &&
                    !uiState.isAudiobookshelfSearchMode &&
                    uiState.selectedLibrary == null
            val allLoading =
                if (isAllMode) {
                    uiState.isSearching &&
                        uiState.isAudiobookshelfSearching &&
                        uiState.searchResults.isEmpty() &&
                        uiState.audiobookshelfSearchResults.isEmpty()
                } else {
                    uiState.isSearching ||
                        uiState.isJellyseerrSearching ||
                        uiState.isAudiobookshelfSearching
                }

            when {
                uiState.searchQuery.isEmpty() &&
                    !uiState.isSearching &&
                    !uiState.isJellyseerrSearching &&
                    !uiState.isAudiobookshelfSearching -> {
                    when {
                        uiState.isJellyseerrSearchMode -> {}
                        uiState.isAudiobookshelfSearchMode -> {
                            SearchHomeContent(
                                genres = uiState.audiobookshelfGenres,
                                onGenreClick = onAudiobookshelfGenreClick,
                                widthSizeClass = widthSizeClass,
                                isAudiobookshelf = true,
                            )
                        }
                        else -> {
                            SearchHomeContent(
                                genres = uiState.genres,
                                onGenreClick = onGenreClick,
                                widthSizeClass = widthSizeClass,
                            )
                        }
                    }
                }

                allLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                                    existingStatus = item.getDisplayStatus(),
                                )
                            }
                        },
                    )
                }

                uiState.isAudiobookshelfSearchMode &&
                    uiState.audiobookshelfSearchResults.isNotEmpty() -> {
                    AudiobookshelfSearchResultsContent(
                        results = uiState.audiobookshelfSearchResults,
                        serverUrl = uiState.audiobookshelfServerUrl,
                        onItemClick = onAudiobookshelfItemClick,
                    )
                }

                isAllMode &&
                    (uiState.searchResults.isNotEmpty() ||
                        uiState.audiobookshelfSearchResults.isNotEmpty()) -> {
                    CombinedSearchResultsContent(
                        jellyfinResults = uiState.searchResults,
                        audiobookshelfResults = uiState.audiobookshelfSearchResults,
                        isAudiobookshelfSearching = uiState.isAudiobookshelfSearching,
                        serverUrl = uiState.audiobookshelfServerUrl,
                        onItemClick = onItemClick,
                        onAudiobookshelfItemClick = onAudiobookshelfItemClick,
                    )
                }

                uiState.searchResults.isNotEmpty() -> {
                    SearchResultsContent(results = uiState.searchResults, onItemClick = onItemClick)
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.search_no_results),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                ratingsCombined = uiState.pendingRequest!!.ratingsCombined,
                can4k = currentUser?.hasPermission(Permissions.REQUEST_4K) == true,
                is4k = uiState.is4kRequested,
                onIs4kChange = { viewModel.setIs4kRequested(it) },
                canAdvanced = currentUser?.hasPermission(Permissions.REQUEST_ADVANCED) == true,
                availableServers = uiState.availableServers,
                selectedServer = uiState.selectedServer,
                onServerSelected = { viewModel.selectServer(it) },
                availableProfiles = uiState.availableProfiles,
                selectedProfile = uiState.selectedProfile,
                onProfileSelected = { viewModel.selectProfile(it) },
                selectedRootFolder = uiState.selectedRootFolder,
                isLoadingServers = uiState.isLoadingServers,
                isLoadingProfiles = uiState.isLoadingProfiles,
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
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_left),
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_clear),
                            contentDescription = stringResource(R.string.cd_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            shape = RoundedCornerShape(28.dp),
        )
    }
}

@Composable
private fun HorizontalLibraryFilters(
    libraries: List<AfinityCollection>,
    selectedLibrary: AfinityCollection?,
    isJellyseerrAuthenticated: Boolean,
    isJellyseerrSearchMode: Boolean,
    isAudiobookshelfAuthenticated: Boolean,
    isAudiobookshelfSearchMode: Boolean,
    onLibrarySelected: (AfinityCollection?) -> Unit,
    onJellyseerrSearchSelected: () -> Unit,
    onJellyfinSearchSelected: () -> Unit,
    onAudiobookshelfSearchSelected: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        item {
            LibraryFilterChip(
                text = stringResource(R.string.filter_all),
                isSelected =
                    !isJellyseerrSearchMode &&
                        !isAudiobookshelfSearchMode &&
                        selectedLibrary == null,
                onClick = {
                    onJellyfinSearchSelected()
                    onLibrarySelected(null)
                },
                showCheckIcon = true,
            )
        }

        if (isJellyseerrAuthenticated) {
            item {
                LibraryFilterChip(
                    text = stringResource(R.string.filter_request),
                    isSelected = isJellyseerrSearchMode,
                    onClick = onJellyseerrSearchSelected,
                    showCheckIcon = false,
                )
            }
        }

        if (isAudiobookshelfAuthenticated) {
            item {
                LibraryFilterChip(
                    text = stringResource(R.string.filter_audiobooks),
                    isSelected = isAudiobookshelfSearchMode,
                    onClick = onAudiobookshelfSearchSelected,
                    showCheckIcon = false,
                )
            }
        }

        items(libraries) { library ->
            LibraryFilterChip(
                text = library.name,
                isSelected =
                    !isJellyseerrSearchMode &&
                        !isAudiobookshelfSearchMode &&
                        selectedLibrary?.id == library.id,
                onClick = {
                    onJellyfinSearchSelected()
                    onLibrarySelected(library)
                },
                showCheckIcon = false,
            )
        }
    }
}

@Composable
private fun SearchHomeContent(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    isAudiobookshelf: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.explore_genres),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(widthSizeClass.gridMinSize),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(genres) { genre ->
                GenreCard(
                    genre = genre,
                    onClick = { onGenreClick(genre) },
                    isAudiobookshelf = isAudiobookshelf,
                )
            }
        }
    }
}

@Composable
private fun SearchResultsContent(results: List<AfinityItem>, onItemClick: (AfinityItem) -> Unit) {
    LocalSoftwareKeyboardController.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.search_results_count_fmt, results.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(results) { item -> SearchResultItem(item = item, onClick = { onItemClick(item) }) }
    }
}

@Composable
private fun LibraryFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    showCheckIcon: Boolean = false,
) {
    FilterChip(
        onClick = onClick,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        selected = isSelected,
        leadingIcon =
            if (isSelected && showCheckIcon) {
                {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else null,
        shape = RoundedCornerShape(20.dp),
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            ),
    )
}

@Composable
private fun GenreCard(genre: String, onClick: () -> Unit, isAudiobookshelf: Boolean = false) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = getGenreIcon(genre, isAudiobookshelf),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = genre,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(item: AfinityItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.width(80.dp).height(120.dp)) {
                Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp)) {
                    AsyncImage(
                        imageUrl = item.images.primaryImageUrl,
                        contentDescription = item.name,
                        blurHash = item.images.primaryBlurHash,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                if (item.played) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = stringResource(R.string.cd_watched),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name,
                    style =
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text =
                            when (item) {
                                is AfinityMovie -> stringResource(R.string.media_type_movie)
                                is AfinityShow -> stringResource(R.string.media_type_tv_show)
                                else -> stringResource(R.string.media_type_media)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    when (item) {
                        is AfinityMovie -> item.productionYear
                        is AfinityShow -> item.productionYear
                        else -> null
                    }?.let { year ->
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_imdb_logo),
                            contentDescription = stringResource(R.string.cd_imdb),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", rating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (item is AfinityMovie) {
                        item.criticRating?.let { rtRating ->
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                painter =
                                    painterResource(
                                        id =
                                            if (rtRating > 60) {
                                                R.drawable.ic_rotten_tomato_fresh
                                            } else {
                                                R.drawable.ic_rotten_tomato_rotten
                                            }
                                    ),
                                contentDescription = stringResource(R.string.cd_rotten_tomatoes),
                                tint = Color.Unspecified,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "${rtRating.toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun JellyseerrSearchResultsContent(
    results: List<SearchResultItem>,
    onRequestClick: (SearchResultItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.search_results_count_fmt, results.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(results) { item ->
            JellyseerrSearchResultItem(item = item, onRequestClick = { onRequestClick(item) })
        }
    }
}

@Composable
private fun JellyseerrSearchResultItem(item: SearchResultItem, onRequestClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.width(80.dp).height(120.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                AsyncImage(
                    imageUrl = item.getPosterUrl(),
                    contentDescription = item.getDisplayTitle(),
                    blurHash = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.getDisplayTitle(),
                    style =
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text =
                            item.getMediaType()?.name
                                ?: stringResource(R.string.media_type_unknown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    item.releaseDate?.let { releaseDate ->
                        if (releaseDate.length >= 4) {
                            val year = releaseDate.take(4)
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = year,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            val status = item.getDisplayStatus()
            val canRequest = !item.hasExistingRequest() || status == MediaStatus.PARTIALLY_AVAILABLE

            if (
                item.hasExistingRequest() &&
                    status != null &&
                    status != MediaStatus.PARTIALLY_AVAILABLE
            ) {
                Surface(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    shape = RoundedCornerShape(16.dp),
                    color =
                        when (status) {
                            MediaStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                            MediaStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                            MediaStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                            MediaStatus.PARTIALLY_AVAILABLE -> MaterialTheme.colorScheme.secondary
                            MediaStatus.AVAILABLE -> MaterialTheme.colorScheme.secondary
                            MediaStatus.DELETED -> MaterialTheme.colorScheme.error
                        },
                ) {
                    val statusText =
                        when (status) {
                            MediaStatus.PENDING -> stringResource(R.string.status_pending)
                            MediaStatus.PROCESSING -> stringResource(R.string.status_processing)
                            MediaStatus.PARTIALLY_AVAILABLE ->
                                stringResource(R.string.status_partially_available)
                            MediaStatus.AVAILABLE -> stringResource(R.string.status_available)
                            MediaStatus.DELETED -> stringResource(R.string.status_deleted)
                            else -> stringResource(R.string.status_unknown)
                        }

                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            when (status) {
                                MediaStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                                MediaStatus.PENDING -> MaterialTheme.colorScheme.onTertiary
                                MediaStatus.PROCESSING -> MaterialTheme.colorScheme.onPrimary
                                MediaStatus.PARTIALLY_AVAILABLE ->
                                    MaterialTheme.colorScheme.onSecondary
                                MediaStatus.AVAILABLE -> MaterialTheme.colorScheme.onSecondary
                                MediaStatus.DELETED -> MaterialTheme.colorScheme.onError
                            },
                    )
                }
            }

            if (canRequest) {
                IconButton(
                    onClick = onRequestClick,
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = stringResource(R.string.cd_request_add),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun getGenreIcon(genre: String, isAudiobookshelf: Boolean = false): Painter {
    val normalizedGenre = genre.lowercase()
    return when {
        normalizedGenre == "action" || normalizedGenre.startsWith("action") ->
            painterResource(id = R.drawable.ic_boom)
        normalizedGenre == "comedy" || normalizedGenre.contains("humor") ->
            painterResource(id = R.drawable.ic_comedy)
        normalizedGenre == "drama" -> painterResource(id = R.drawable.ic_theater)
        normalizedGenre == "horror" -> painterResource(id = R.drawable.ic_horror)
        normalizedGenre == "thriller" -> painterResource(id = R.drawable.ic_bolt)
        normalizedGenre == "romance" || normalizedGenre.contains("romance") ->
            painterResource(id = R.drawable.ic_favorite)
        normalizedGenre == "sci-fi" ||
            normalizedGenre == "science fiction" ||
            normalizedGenre.contains("sci-fi") -> painterResource(id = R.drawable.ic_alien)
        normalizedGenre == "fantasy" || normalizedGenre.startsWith("fantasy") ->
            painterResource(id = R.drawable.ic_auto_awesome)
        normalizedGenre == "documentary" -> painterResource(id = R.drawable.ic_article)
        normalizedGenre == "animation" -> painterResource(id = R.drawable.ic_animation)
        normalizedGenre == "family" || normalizedGenre.contains("family") ->
            painterResource(id = R.drawable.ic_family)
        normalizedGenre == "adventure" || normalizedGenre.contains("adventure") ->
            painterResource(id = R.drawable.ic_adventure)
        normalizedGenre == "crime" -> painterResource(id = R.drawable.ic_security)
        normalizedGenre == "mystery" || normalizedGenre.contains("mystery") ->
            painterResource(id = R.drawable.ic_mystery)
        normalizedGenre == "western" -> painterResource(id = R.drawable.ic_cactus)
        normalizedGenre == "war" || normalizedGenre.contains("military") ->
            painterResource(id = R.drawable.ic_war)
        normalizedGenre == "music" -> painterResource(id = R.drawable.ic_music_heart)
        normalizedGenre == "sport" || normalizedGenre == "sports" ->
            painterResource(id = R.drawable.ic_sports)
        normalizedGenre == "biography" ||
            normalizedGenre.contains("biograph") ||
            normalizedGenre.contains("memoir") -> painterResource(id = R.drawable.ic_person_heart)
        normalizedGenre == "history" || normalizedGenre.startsWith("history") ->
            painterResource(id = R.drawable.ic_history)
        normalizedGenre.contains("fiction") || normalizedGenre.contains("literary") ->
            painterResource(id = R.drawable.ic_book_audio)
        normalizedGenre.contains("children") || normalizedGenre.contains("young adult") ->
            painterResource(id = R.drawable.ic_family)
        normalizedGenre.contains("erotica") -> painterResource(id = R.drawable.ic_favorite)
        normalizedGenre.contains("art") -> painterResource(id = R.drawable.ic_auto_awesome)
        normalizedGenre.contains("entertainment") -> painterResource(id = R.drawable.ic_theater)
        else ->
            if (isAudiobookshelf) {
                painterResource(id = R.drawable.ic_book_audio)
            } else {
                painterResource(id = R.drawable.ic_movie)
            }
    }
}

private fun formatAudiobookDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun AudiobookshelfSearchResultItem(
    item: LibraryItem,
    serverUrl: String?,
    onClick: () -> Unit,
) {
    val isFinished = item.userMediaProgress?.isFinished == true
    val progress = item.userMediaProgress?.progress ?: 0.0

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.width(80.dp).height(120.dp)) {
                Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp)) {
                    AsyncImage(
                        imageUrl = serverUrl?.let { "$it/api/items/${item.id}/cover" },
                        contentDescription = item.media.metadata.title,
                        blurHash = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                if (isFinished) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .background(Color(0xFF4CAF50), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = stringResource(R.string.cd_finished),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.media.metadata.title ?: "",
                    style =
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                item.media.metadata.authorName?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text =
                            if (item.mediaType == "podcast") {
                                stringResource(R.string.media_type_podcast)
                            } else {
                                stringResource(R.string.media_type_book)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    item.media.duration?.let { duration ->
                        Text(
                            text = "\u2022",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatAudiobookDuration(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!isFinished && progress > 0.0) {
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                        modifier =
                            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFFFC107),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                item.media.metadata.description?.let { rawHtml ->
                    val cleanDescription =
                        remember(rawHtml) {
                            HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
                                .toString()
                                .trim()
                        }
                    Text(
                        text = cleanDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookshelfSearchResultsContent(
    results: List<LibraryItem>,
    serverUrl: String?,
    onItemClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.search_results_count_fmt, results.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(results, key = { it.id }) { item ->
            AudiobookshelfSearchResultItem(
                item = item,
                serverUrl = serverUrl,
                onClick = { onItemClick(item.id) },
            )
        }
    }
}

@Composable
private fun CombinedSearchResultsContent(
    jellyfinResults: List<AfinityItem>,
    audiobookshelfResults: List<LibraryItem>,
    isAudiobookshelfSearching: Boolean,
    serverUrl: String?,
    onItemClick: (AfinityItem) -> Unit,
    onAudiobookshelfItemClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (jellyfinResults.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.search_results_count_fmt, jellyfinResults.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(jellyfinResults) { item ->
                SearchResultItem(item = item, onClick = { onItemClick(item) })
            }
        }

        if (audiobookshelfResults.isNotEmpty() || isAudiobookshelfSearching) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.search_results_count_fmt,
                                audiobookshelfResults.size,
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isAudiobookshelfSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            items(audiobookshelfResults, key = { it.id }) { item ->
                AudiobookshelfSearchResultItem(
                    item = item,
                    serverUrl = serverUrl,
                    onClick = { onAudiobookshelfItemClick(item.id) },
                )
            }
        }
    }
}
