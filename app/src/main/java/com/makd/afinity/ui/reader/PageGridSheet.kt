package com.makd.afinity.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.AfinityToggleChip
import java.io.File

private val GridMinCell = 96.dp
private const val PAGE_ASPECT = 2f / 3f
private const val ROWS_ABOVE_CURRENT = 6

@Composable
internal fun PageGridSheet(
    pageCount: Int,
    currentPage: Int,
    bookmarkedPages: Set<Int>,
    loadThumbnail: suspend (Int) -> File?,
    onPageClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var onlyBookmarked by rememberSaveable { mutableStateOf(false) }
    val filtering = onlyBookmarked && bookmarkedPages.isNotEmpty()
    val pages =
        remember(pageCount, filtering, bookmarkedPages) {
            if (filtering) bookmarkedPages.sorted() else (0 until pageCount).toList()
        }
    AfinityModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.reader_pages),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AfinityToggleChip(
                label = stringResource(R.string.reader_bookmarked_filter_fmt, bookmarkedPages.size),
                selected = filtering,
                onSelectedChange = { onlyBookmarked = it },
                enabled = bookmarkedPages.isNotEmpty(),
            )
        }
        key(filtering) {
            val gridState =
                rememberLazyGridState(
                    initialFirstVisibleItemIndex =
                        if (filtering) 0 else (currentPage - ROWS_ABOVE_CURRENT).coerceAtLeast(0),
                )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(GridMinCell),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            ) {
                items(pages, key = { it }) { page ->
                    GridPage(
                        page = page,
                        current = page == currentPage,
                        bookmarked = page in bookmarkedPages,
                        loadThumbnail = loadThumbnail,
                        onClick = { onPageClick(page) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GridPage(
    page: Int,
    current: Boolean,
    bookmarked: Boolean,
    loadThumbnail: suspend (Int) -> File?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val file by produceState<File?>(initialValue = null, page) { value = loadThumbnail(page) }
    val label = stringResource(R.string.reader_page_number_fmt, page + 1)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = label
                    this.selected = current
                },
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(PAGE_ASPECT)
                    .then(
                        if (current) {
                            Modifier.border(2.dp, colors.primary, RoundedCornerShape(10.dp))
                                .padding(3.dp)
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceContainerHigh),
        ) {
            file?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (bookmarked) PageRibbon(modifier = Modifier.align(Alignment.TopEnd))
        }
        Text(
            text = (page + 1).toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (current) colors.primary else colors.onSurfaceVariant,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
    }
}
