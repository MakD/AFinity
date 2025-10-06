package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityRecommendationCategory
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard

@Composable
fun OptimizedContinueWatchingSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val cardWidth = (configuration.screenWidthDp.dp - 28.dp - 12.dp) / 2f
    val fixedRowHeight = (cardWidth / (16f / 9f)) + 8.dp + 20.dp + 22.dp
    Column(
        modifier = Modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = items,
                key = { item -> "continue_${item.id}" }
            ) { item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun OptimizedLatestMoviesSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = "Latest Movies",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = items,
                key = { item -> "latest_movie_${item.id}" }
            ) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun OptimizedLatestTvSeriesSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = "Latest TV Series",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = items,
                key = { item -> "latest_tv_${item.id}" }
            ) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun OptimizedRecommendationCategorySection(
    category: AfinityRecommendationCategory,
    onItemClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = category.items,
                key = { item -> "rec_${category.title}_${item.id}" }
            ) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}