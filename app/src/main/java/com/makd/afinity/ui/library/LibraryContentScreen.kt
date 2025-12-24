package com.makd.afinity.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.makd.afinity.R
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.theme.rememberGridMinColumnSize
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContentScreen(
    onBackClick: () -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: LibraryContentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagingDataFlow by viewModel.pagingData.collectAsStateWithLifecycle()
    val lazyPagingItems = pagingDataFlow.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val scrollToIndex by viewModel.scrollToIndex.collectAsStateWithLifecycle()
    var showSortDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scrollToIndex) {
        Timber.d("Alphabet scroll: LaunchedEffect triggered with scrollToIndex: $scrollToIndex")
        if (scrollToIndex >= 0) {
            Timber.d("Alphabet scroll: Animating to item $scrollToIndex")
            gridState.animateScrollToItem(scrollToIndex)
            Timber.d("Alphabet scroll: Animation completed, resetting index")
            viewModel.resetScrollIndex()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            LibraryContentTopBar(
                title = uiState.libraryName,
                onBackClick = onBackClick,
                backgroundOpacity = 1f,
                userProfileImageUrl = uiState.userProfileImageUrl,
                itemCount = lazyPagingItems.itemCount,
                onSortClick = { showSortDialog = true },
                onProfileClick = onProfileClick,
                navController = navController
            )

            FilterRow(
                currentFilter = uiState.currentFilter,
                onFilterSelected = { viewModel.updateFilter(it) }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorMessage(
                            message = uiState.error!!
                        )
                    }
                }

                lazyPagingItems.itemCount == 0 && lazyPagingItems.loadState.refresh !is LoadState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyLibraryMessage()
                    }
                }

                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            //columns = GridCells.Adaptive(160.dp),
                            columns = GridCells.Adaptive(rememberGridMinColumnSize()),
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 80.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(
                                count = lazyPagingItems.itemCount,
                                key = { index -> lazyPagingItems[index]?.id ?: index }
                            ) { index ->
                                lazyPagingItems[index]?.let { item ->
                                    MediaItemGridCard(
                                        item = item,
                                        onClick = {
                                            viewModel.onItemClick(item)
                                            onItemClick(item)
                                        }
                                    )
                                }
                            }

                            lazyPagingItems.apply {
                                when {
                                    loadState.append is LoadState.Loading -> {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    }

                                    loadState.append is LoadState.Error -> {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Button(onClick = { retry() }) {
                                                    Text("Retry")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            AlphabetScroller(
                                onLetterSelected = { letter ->
                                    Timber.d("Alphabet scroll: Letter '$letter' selected")
                                    viewModel.scrollToLetter(letter)
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showSortDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(end = 24.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrows_sort),
                contentDescription = "Sort"
            )
        }
    }

    if (showSortDialog) {
        SortDialog(
            onDismiss = { showSortDialog = false },
            onSortSelected = { sortBy, descending ->
                viewModel.updateSort(sortBy, descending)
                showSortDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContentTopBar(
    title: String,
    onBackClick: () -> Unit,
    backgroundOpacity: Float = 0f,
    navController: NavController,
    userProfileImageUrl: String? = null,
    itemCount: Int,
    onSortClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = title,
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
                            painter = painterResource(id = R.drawable.ic_search),
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
                            painter = painterResource(id = R.drawable.ic_user_circle),
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
private fun MediaItemGridCard(
    item: AfinityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box {
                OptimizedAsyncImage(
                    imageUrl = item.images.primaryImageUrl,
                    contentDescription = item.name,
                    blurHash = item.images.primaryBlurHash,
                    targetWidth = 160.dp,
                    targetHeight = 240.dp,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                when {
                    item.played -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_circle_check),
                                contentDescription = "Watched",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    item is AfinityShow -> {
                        val episodeText = when {
                            item.unplayedItemCount != null && item.unplayedItemCount > 0 ->
                                "${item.unplayedItemCount}"

                            item.episodeCount != null && item.episodeCount > 0 ->
                                "${item.episodeCount}"

                            else -> null
                        }

                        episodeText?.let { text ->
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = if (text.toIntOrNull() != null && text.toInt() > 99) "99+ EP" else "$text EP",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start
        )

        when (item) {
            is AfinityMovie -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item.communityRating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_imdb_logo),
                                contentDescription = "IMDB",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item.criticRating?.let { rtRating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
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
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${rtRating.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is AfinityShow -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item.communityRating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_imdb_logo),
                                contentDescription = "IMDB",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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
    }
}

@Composable
private fun EmptyLibraryMessage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "No items found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "This library appears to be empty or the content hasn't been scanned yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SortDialog(
    onDismiss: () -> Unit,
    onSortSelected: (SortBy, Boolean) -> Unit
) {
    var isAscending by remember { mutableStateOf(true) }
    var selectedSort by remember { mutableStateOf(SortBy.NAME) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sort by")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = isAscending,
                        onClick = { isAscending = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Spacer(Modifier.width(4.dp))
                        Text("Ascending")
                    }

                    SegmentedButton(
                        selected = !isAscending,
                        onClick = { isAscending = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Spacer(Modifier.width(4.dp))
                        Text("Descending")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SortOptionRow("Title", SortBy.NAME, selectedSort) { selectedSort = it }
                    SortOptionRow("IMDB Rating", SortBy.IMDB_RATING, selectedSort) {
                        selectedSort = it
                    }
                    SortOptionRow(
                        "Parental Rating",
                        SortBy.PARENTAL_RATING,
                        selectedSort
                    ) { selectedSort = it }
                    SortOptionRow("Date Added", SortBy.DATE_ADDED, selectedSort) {
                        selectedSort = it
                    }
                    SortOptionRow("Date Played", SortBy.DATE_PLAYED, selectedSort) {
                        selectedSort = it
                    }
                    SortOptionRow(
                        "Release Date",
                        SortBy.RELEASE_DATE,
                        selectedSort
                    ) { selectedSort = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSortSelected(selectedSort, !isAscending)
                    onDismiss()
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SortOptionRow(
    label: String,
    sortBy: SortBy,
    selectedSort: SortBy,
    onSelect: (SortBy) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(sortBy) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selectedSort == sortBy,
            onClick = { onSelect(sortBy) }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun FilterRow(
    currentFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        FilterType.ALL to "All",
        FilterType.WATCHED to "Watched",
        FilterType.UNWATCHED to "Unwatched",
        FilterType.WATCHLIST to "Watchlist",
        FilterType.FAVORITES to "Favorites"
    )

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(filters) { (filterType, label) ->
            FilterChip(
                selected = currentFilter == filterType,
                onClick = {
                    if (filterType != FilterType.WATCHLIST) {
                        onFilterSelected(filterType)
                    }
                },
                label = { Text(label) },
                leadingIcon = if (currentFilter == filterType) {
                    when (filterType) {
                        FilterType.FAVORITES -> {
                            {
                                Icon(
                                    painterResource(id = R.drawable.ic_favorite_filled),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        else -> {
                            {
                                Icon(
                                    painterResource(id = R.drawable.ic_circle_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else null,
                shape = RoundedCornerShape(50)
            )
        }
    }
}

@Composable
private fun AlphabetScroller(
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = listOf("#") + ('A'..'Z').map { it.toString() }

    LazyColumn(
        modifier = modifier
            .width(24.dp)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(
            items = letters,
            key = { letter -> letter }
        ) { letter ->
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onLetterSelected(letter) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}