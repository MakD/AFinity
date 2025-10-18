package com.makd.afinity.ui.item.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.item.components.shared.CastSection
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.item.components.shared.SpecialFeaturesSection
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.calculateCardHeight
import com.makd.afinity.ui.theme.rememberLandscapeCardWidth
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData

@Composable
fun SeasonDetailContent(
    season: AfinitySeason,
    episodesPagingData: Flow<PagingData<AfinityEpisode>>?,
    specialFeatures: List<AfinityItem>,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: androidx.navigation.NavController
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TaglineSection(item = season)

        OverviewSection(item = season)

        ExternalLinksSection(item = season)

        episodesPagingData?.let { pagingData ->
            EpisodesSection(
                episodesPagingData = pagingData,
                onEpisodeClick = onEpisodeClick
            )
        }

        SpecialFeaturesSection(
            specialFeatures = specialFeatures,
            onItemClick = onSpecialFeatureClick
        )

        CastSection(
            item = season,
            onPersonClick = { personId ->
                val route = com.makd.afinity.navigation.Destination.createPersonRoute(personId.toString())
                navController.navigate(route)
            }
        )
    }
}

@Composable
private fun EpisodesSection(
    episodesPagingData: Flow<PagingData<AfinityEpisode>>,
    onEpisodeClick: (AfinityEpisode) -> Unit
) {
    val lazyEpisodeItems = episodesPagingData.collectAsLazyPagingItems()
    val cardWidth = rememberLandscapeCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Episodes",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        if (lazyEpisodeItems.loadState.refresh is LoadState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                count = lazyEpisodeItems.itemCount,
                key = { index -> lazyEpisodeItems[index]?.id ?: index }
            ) { index ->
                lazyEpisodeItems[index]?.let { episode ->
                    ContinueWatchingCard(
                        item = episode,
                        onClick = { onEpisodeClick(episode) },
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
        }

        if (lazyEpisodeItems.loadState.append is LoadState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
    }
}