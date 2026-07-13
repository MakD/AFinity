package com.makd.afinity.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.theme.CardDimensions.gridMinSize
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth

@Composable
fun MediaCategoryGrid(
    items: List<AfinityItem>,
    landscape: Boolean,
    widthSizeClass: WindowWidthSizeClass,
    playerOffset: Dp,
    onItemClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSortDialog by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(SortBy.NAME) }
    var descending by remember { mutableStateOf(false) }

    val sortedItems =
        remember(items, sortBy, descending) { items.sortedForCategory(sortBy, descending) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(widthSizeClass.gridMinSize),
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 80.dp + playerOffset,
                ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items = sortedItems, key = { it.id }) { item ->
                if (landscape) {
                    ContinueWatchingCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        cardWidth = widthSizeClass.landscapeWidth,
                        fillWidth = true,
                    )
                } else {
                    MediaItemGridCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }

        FloatingActionButton(
            onClick = { showSortDialog = true },
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 24.dp)
                    .padding(bottom = 16.dp + playerOffset),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrows_sort),
                contentDescription = stringResource(R.string.cd_sort_fab),
            )
        }
    }

    if (showSortDialog) {
        MediaCategorySortDialog(
            currentSortBy = sortBy,
            currentDescending = descending,
            onDismiss = { showSortDialog = false },
            onSortSelected = { selected, isDescending ->
                sortBy = selected
                descending = isDescending
                showSortDialog = false
            },
        )
    }
}

private fun List<AfinityItem>.sortedForCategory(
    sortBy: SortBy,
    descending: Boolean,
): List<AfinityItem> {
    val comparator: Comparator<AfinityItem> =
        when (sortBy) {
            SortBy.IMDB_RATING -> compareBy { ratingKey(it) }
            SortBy.DATE_ADDED -> compareBy { dateAddedKey(it) }
            SortBy.RELEASE_DATE -> compareBy { releaseYearKey(it) }
            else -> compareBy { it.name.lowercase() }
        }
    return if (descending) sortedWith(comparator.reversed()) else sortedWith(comparator)
}

private fun ratingKey(item: AfinityItem): Float? =
    when (item) {
        is AfinityMovie -> item.communityRating
        is AfinityShow -> item.communityRating
        is AfinityBoxSet -> item.communityRating
        is AfinityEpisode -> item.communityRating
        else -> null
    }

private fun dateAddedKey(item: AfinityItem): java.time.LocalDateTime? =
    when (item) {
        is AfinityMovie -> item.dateCreated
        is AfinityShow -> item.dateCreated
        else -> null
    }

private fun releaseYearKey(item: AfinityItem): Int? =
    when (item) {
        is AfinityMovie -> item.productionYear
        is AfinityShow -> item.productionYear
        is AfinitySeason -> item.productionYear
        is AfinityBoxSet -> item.productionYear
        else -> null
    }

@Composable
private fun MediaCategorySortDialog(
    currentSortBy: SortBy,
    currentDescending: Boolean,
    onDismiss: () -> Unit,
    onSortSelected: (SortBy, Boolean) -> Unit,
) {
    var isAscending by remember { mutableStateOf(!currentDescending) }
    var selectedSort by remember { mutableStateOf(currentSortBy) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isAscending,
                        onClick = { isAscending = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.sort_ascending))
                    }

                    SegmentedButton(
                        selected = !isAscending,
                        onClick = { isAscending = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.sort_descending))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SortOptionRow(
                        stringResource(R.string.sort_option_title),
                        SortBy.NAME,
                        selectedSort,
                    ) {
                        selectedSort = it
                    }
                    SortOptionRow(
                        stringResource(R.string.sort_option_imdb),
                        SortBy.IMDB_RATING,
                        selectedSort,
                    ) {
                        selectedSort = it
                    }
                    SortOptionRow(
                        stringResource(R.string.sort_option_date_added),
                        SortBy.DATE_ADDED,
                        selectedSort,
                    ) {
                        selectedSort = it
                    }
                    SortOptionRow(
                        stringResource(R.string.sort_option_release_date),
                        SortBy.RELEASE_DATE,
                        selectedSort,
                    ) {
                        selectedSort = it
                    }
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
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SortOptionRow(
    label: String,
    sortBy: SortBy,
    selectedSort: SortBy,
    onSelect: (SortBy) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(sortBy) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selectedSort == sortBy, onClick = { onSelect(sortBy) })
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}