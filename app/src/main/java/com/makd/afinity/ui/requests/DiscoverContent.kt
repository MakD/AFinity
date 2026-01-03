package com.makd.afinity.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.SearchResultItem

@Composable
fun DiscoverContent(
    trendingItems: List<SearchResultItem>,
    popularMovies: List<SearchResultItem>,
    popularTv: List<SearchResultItem>,
    upcomingMovies: List<SearchResultItem>,
    upcomingTv: List<SearchResultItem>,
    isLoading: Boolean,
    onItemClick: (SearchResultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (trendingItems.isEmpty() && popularMovies.isEmpty() && popularTv.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_error),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "No content available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (trendingItems.isNotEmpty()) {
                item {
                    DiscoverSection(
                        title = "Trending Now",
                        items = trendingItems,
                        onItemClick = onItemClick,
                        onSeeAllClick = { /* TODO: Navigate to full list */ }
                    )
                }
            }

            if (popularMovies.isNotEmpty()) {
                item {
                    DiscoverSection(
                        title = "Popular Movies",
                        items = popularMovies,
                        onItemClick = onItemClick,
                        onSeeAllClick = { /* TODO: Navigate to full list */ }
                    )
                }
            }

            if (popularTv.isNotEmpty()) {
                item {
                    DiscoverSection(
                        title = "Popular TV Shows",
                        items = popularTv,
                        onItemClick = onItemClick,
                        onSeeAllClick = { /* TODO: Navigate to full list */ }
                    )
                }
            }

            if (upcomingMovies.isNotEmpty()) {
                item {
                    DiscoverSection(
                        title = "Upcoming Movies",
                        items = upcomingMovies,
                        onItemClick = onItemClick,
                        onSeeAllClick = { /* TODO: Navigate to full list */ }
                    )
                }
            }

            if (upcomingTv.isNotEmpty()) {
                item {
                    DiscoverSection(
                        title = "Upcoming TV Shows",
                        items = upcomingTv,
                        onItemClick = onItemClick,
                        onSeeAllClick = { /* TODO: Navigate to full list */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverSection(
    title: String,
    items: List<SearchResultItem>,
    onItemClick: (SearchResultItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            TextButton(onClick = onSeeAllClick) {
                Text("See All")
                Spacer(modifier = Modifier.size(4.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { item ->
                DiscoverMediaCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}