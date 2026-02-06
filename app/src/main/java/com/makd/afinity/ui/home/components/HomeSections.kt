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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun OptimizedContinueWatchingSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val cardHeight =
        CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        Text(
            text = stringResource(R.string.home_continue_watching),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        val uniqueItems = items.distinctBy { it.id }

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = uniqueItems, key = { item -> "continue_${item.id}" }) { item ->
                ContinueWatchingCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
fun OptimizedLatestMoviesSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.home_latest_movies),
) {
    val cardWidth = widthSizeClass.portraitWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        val uniqueItems = items.distinctBy { it.id }

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = uniqueItems, key = { item -> "latest_movie_${item.id}" }) { item ->
                MediaItemCard(item = item, onClick = { onItemClick(item) }, cardWidth = cardWidth)
            }
        }
    }
}

@Composable
fun OptimizedLatestTvSeriesSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.home_latest_tv_series),
) {
    val cardWidth = widthSizeClass.portraitWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        val uniqueItems = items.distinctBy { it.id }

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = uniqueItems, key = { item -> "latest_tv_${item.id}" }) { item ->
                MediaItemCard(item = item, onClick = { onItemClick(item) }, cardWidth = cardWidth)
            }
        }
    }
}
