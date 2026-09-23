package com.makd.afinity.ui.item.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.models.external.ExternalTitles
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle
import com.makd.afinity.ui.item.components.shared.ExternalTitlesSection
import com.makd.afinity.ui.item.components.shared.baseMediaDetailItems
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

fun LazyListScope.boxSetDetailItems(
    item: AfinityBoxSet,
    boxSetItems: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    missingParts: ExternalTitles?,
    onMissingPartClick: (SearchResultItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    horizontalPadding: Dp,
    detailLayout: DetailLayout,
) {
    val portraitWidth = widthSizeClass.portraitWidth
    val landscapeWidth = widthSizeClass.landscapeWidth

    val movies = boxSetItems.filterIsInstance<AfinityMovie>()
    val shows = boxSetItems.filterIsInstance<AfinityShow>()
    val seasons = boxSetItems.filterIsInstance<AfinitySeason>()
    val episodes = boxSetItems.filterIsInstance<AfinityEpisode>()

    baseMediaDetailItems(
        item = item,
        specialFeatures = emptyList(),
        containingBoxSets = emptyList(),
        tmdbReviews = emptyList(),
        mdbRatings = emptyList(),
        isRatingsFromCache = false,
        onSpecialFeatureClick = {},
        onBoxSetClick = {},
        onPersonClick = {},
        widthSizeClass = widthSizeClass,
        horizontalPadding = horizontalPadding,
        detailLayout = detailLayout,
    ) {
        if (movies.isNotEmpty()) {
            detailItem("boxset_movies", horizontalPadding) {
                BoxSetTypeSection(
                    title = stringResource(R.string.section_movies),
                    items = movies,
                    onItemClick = onItemClick,
                    cardWidth = portraitWidth,
                )
            }
        }

        if (shows.isNotEmpty()) {
            detailItem("boxset_shows", horizontalPadding) {
                BoxSetTypeSection(
                    title = stringResource(R.string.section_tv_shows),
                    items = shows,
                    onItemClick = onItemClick,
                    cardWidth = portraitWidth,
                )
            }
        }

        if (seasons.isNotEmpty()) {
            detailItem("boxset_seasons", horizontalPadding) {
                BoxSetTypeSection(
                    title = stringResource(R.string.section_seasons),
                    items = seasons,
                    onItemClick = onItemClick,
                    cardWidth = portraitWidth,
                )
            }
        }

        if (episodes.isNotEmpty()) {
            detailItem("boxset_episodes", horizontalPadding) {
                BoxSetEpisodesSection(
                    title = stringResource(R.string.section_episodes),
                    episodes = episodes,
                    onEpisodeClick = { onItemClick(it) },
                    cardWidth = landscapeWidth,
                )
            }
        }

        if (missingParts != null) {
            detailItem("boxset_missing", horizontalPadding) {
                ExternalTitlesSection(
                    title = stringResource(R.string.not_in_library_title),
                    titles = missingParts,
                    onSeerrItemClick = onMissingPartClick,
                    cardWidth = portraitWidth,
                )
            }
        }
    }
}

@Composable
private fun BoxSetTypeSection(
    title: String,
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    cardWidth: Dp,
) {
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionTitle(text = title)

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items, key = { it.id.toString() }) { item ->
                MediaItemCard(item = item, onClick = { onItemClick(item) }, cardWidth = cardWidth)
            }
        }
    }
}

@Composable
private fun BoxSetEpisodesSection(
    title: String,
    episodes: List<AfinityEpisode>,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    cardWidth: Dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionTitle(text = title)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(episodes, key = { it.id.toString() }) { episode ->
                ContinueWatchingCard(
                    item = episode,
                    onClick = { onEpisodeClick(episode) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}
