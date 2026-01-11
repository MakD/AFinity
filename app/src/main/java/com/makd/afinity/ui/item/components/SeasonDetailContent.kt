package com.makd.afinity.ui.item.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.makd.afinity.data.models.common.EpisodeLayout
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.EpisodeListCard
import com.makd.afinity.ui.item.components.shared.CastSection
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.item.components.shared.SpecialFeaturesSection
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import kotlinx.coroutines.flow.Flow

@Composable
fun SeasonDetailContent(
    season: AfinitySeason,
    episodesPagingData: Flow<PagingData<AfinityEpisode>>?,
    specialFeatures: List<AfinityItem>,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: androidx.navigation.NavController,
    preferencesRepository: PreferencesRepository,
    widthSizeClass: WindowWidthSizeClass
) {
    val episodeLayout by preferencesRepository.getEpisodeLayoutFlow()
        .collectAsState(initial = EpisodeLayout.HORIZONTAL)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TaglineSection(item = season)

        OverviewSection(item = season)

        ExternalLinksSection(item = season)

        episodesPagingData?.let { pagingData ->
            EpisodesSection(
                episodesPagingData = pagingData,
                onEpisodeClick = onEpisodeClick,
                layout = episodeLayout,
                widthSizeClass = widthSizeClass
            )
        }

        SpecialFeaturesSection(
            specialFeatures = specialFeatures,
            onItemClick = onSpecialFeatureClick,
            widthSizeClass = widthSizeClass
        )

        CastSection(
            item = season,
            onPersonClick = { personId ->
                val route =
                    com.makd.afinity.navigation.Destination.createPersonRoute(personId.toString())
                navController.navigate(route)
            },
            widthSizeClass = widthSizeClass
        )
    }
}

@Composable
private fun EpisodesSection(
    episodesPagingData: Flow<PagingData<AfinityEpisode>>,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    layout: EpisodeLayout,
    widthSizeClass: WindowWidthSizeClass
) {
    val lazyEpisodeItems = episodesPagingData.collectAsLazyPagingItems()

    val cardWidth = widthSizeClass.landscapeWidth

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

        when (layout) {
            EpisodeLayout.HORIZONTAL -> {
                HorizontalEpisodesList(
                    lazyEpisodeItems = lazyEpisodeItems,
                    onEpisodeClick = onEpisodeClick,
                    cardWidth = cardWidth
                )
            }

            EpisodeLayout.VERTICAL -> {
                VerticalEpisodesList(
                    lazyEpisodeItems = lazyEpisodeItems,
                    onEpisodeClick = onEpisodeClick,
                    cardWidth = cardWidth
                )
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

@Composable
private fun HorizontalEpisodesList(
    lazyEpisodeItems: LazyPagingItems<AfinityEpisode>,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    cardWidth: Dp
) {
    val cardHeight =
        CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

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
                    modifier = Modifier.width(cardWidth),
                    cardWidth = cardWidth
                )
            }
        }
    }
}

@Composable
private fun VerticalEpisodesList(
    lazyEpisodeItems: LazyPagingItems<AfinityEpisode>,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    cardWidth: Dp
) {
    Column {
        repeat(lazyEpisodeItems.itemCount) { index ->
            lazyEpisodeItems[index]?.let { episode ->
                EpisodeListCard(
                    item = episode,
                    onClick = { onEpisodeClick(episode) },
                    thumbnailWidth = cardWidth
                )
            }
        }
    }
}