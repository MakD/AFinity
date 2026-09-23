package com.makd.afinity.ui.item.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.makd.afinity.R
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.models.common.EpisodeLayout
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.mdblist.MdbListRatingBadges
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.tmdb.TmdbReview
import com.makd.afinity.data.models.wikidata.WikidataAwards
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.EpisodeListCard
import com.makd.afinity.ui.item.components.shared.DetailItemBox
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle
import com.makd.afinity.ui.item.components.shared.baseMediaDetailItems
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth

fun LazyListScope.seasonDetailItems(
    season: AfinitySeason,
    lazyEpisodeItems: LazyPagingItems<AfinityEpisode>?,
    episodeLayout: EpisodeLayout,
    specialFeatures: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    tmdbReviews: List<TmdbReview> = emptyList(),
    mdbRatings: List<MdbListRating> = emptyList(),
    mdbRatingBadges: MdbListRatingBadges = MdbListRatingBadges(),
    wikidataAwards: WikidataAwards? = null,
    isRatingsFromCache: Boolean = false,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    widthSizeClass: WindowWidthSizeClass,
    horizontalPadding: Dp,
    detailLayout: DetailLayout,
) {
    baseMediaDetailItems(
        item = season,
        specialFeatures = specialFeatures,
        containingBoxSets = containingBoxSets,
        tmdbReviews = tmdbReviews,
        mdbRatings = mdbRatings,
        mdbRatingBadges = mdbRatingBadges,
        wikidataAwards = wikidataAwards,
        isRatingsFromCache = isRatingsFromCache,
        onSpecialFeatureClick = onSpecialFeatureClick,
        onBoxSetClick = { boxSet ->
            val route = Destination.createItemDetailRoute(boxSet.id.toString())
            navController.navigate(route)
        },
        onPersonClick = { personId ->
            val route = Destination.createPersonRoute(personId.toString())
            navController.navigate(route)
        },
        widthSizeClass = widthSizeClass,
        horizontalPadding = horizontalPadding,
        detailLayout = detailLayout,
    ) {
        if (lazyEpisodeItems != null) {
            episodesItems(
                lazyEpisodeItems = lazyEpisodeItems,
                onEpisodeClick = onEpisodeClick,
                layout = episodeLayout,
                widthSizeClass = widthSizeClass,
                horizontalPadding = horizontalPadding,
            )
        }
    }
}

private val EpisodesInnerGap = 12.dp

private fun LazyListScope.episodesItems(
    lazyEpisodeItems: LazyPagingItems<AfinityEpisode>,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    layout: EpisodeLayout,
    widthSizeClass: WindowWidthSizeClass,
    horizontalPadding: Dp,
) {
    detailItem("episodes_header", horizontalPadding) {
        DetailSectionTitle(text = stringResource(R.string.season_episodes_title))
    }

    when (layout) {
        EpisodeLayout.HORIZONTAL -> {
            detailItem("episodes_row", horizontalPadding, gap = EpisodesInnerGap) {
                HorizontalEpisodesList(
                    lazyEpisodeItems = lazyEpisodeItems,
                    onEpisodeClick = onEpisodeClick,
                    cardWidth = widthSizeClass.landscapeWidth,
                )
            }
        }

        EpisodeLayout.VERTICAL -> {
            items(
                count = lazyEpisodeItems.itemCount,
                key = lazyEpisodeItems.itemKey { "episode_${it.id}" },
                contentType = lazyEpisodeItems.itemContentType { "episode" },
            ) { index ->
                DetailItemBox(
                    horizontalPadding = horizontalPadding,
                    gap = if (index == 0) EpisodesInnerGap else 0.dp,
                ) {
                    lazyEpisodeItems[index]?.let { episode ->
                        EpisodeListCard(
                            item = episode,
                            onClick = { onEpisodeClick(episode) },
                            thumbnailWidth = widthSizeClass.landscapeWidth,
                        )
                    }
                }
            }
        }
    }

    if (lazyEpisodeItems.loadState.append is LoadState.Loading) {
        detailItem("episodes_loading", horizontalPadding, gap = EpisodesInnerGap) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
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
    cardWidth: Dp,
) {
    val cardHeight =
        CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    LazyRow(
        modifier = Modifier.height(fixedRowHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(
            count = lazyEpisodeItems.itemCount,
            key = lazyEpisodeItems.itemKey { it.id },
        ) { index ->
            lazyEpisodeItems[index]?.let { episode ->
                ContinueWatchingCard(
                    item = episode,
                    onClick = { onEpisodeClick(episode) },
                    modifier = Modifier.width(cardWidth),
                    cardWidth = cardWidth,
                )
            }
        }
    }
}
