package com.makd.afinity.ui.item.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.item.components.shared.BaseMediaDetailContent
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun BoxSetDetailContent(
    item: AfinityBoxSet,
    boxSetItems: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val portraitWidth = widthSizeClass.portraitWidth
    val landscapeWidth = widthSizeClass.landscapeWidth

    val movies = boxSetItems.filterIsInstance<AfinityMovie>()
    val shows = boxSetItems.filterIsInstance<AfinityShow>()
    val seasons = boxSetItems.filterIsInstance<AfinitySeason>()
    val episodes = boxSetItems.filterIsInstance<AfinityEpisode>()

    BaseMediaDetailContent(
        item = item,
        specialFeatures = emptyList(),
        containingBoxSets = emptyList(),
        tmdbReviews = emptyList(),
        onSpecialFeatureClick = {},
        onBoxSetClick = {},
        onPersonClick = {},
        widthSizeClass = widthSizeClass,
    ) {
        if (movies.isNotEmpty()) {
            BoxSetTypeSection(
                title = stringResource(R.string.section_movies),
                items = movies,
                onItemClick = onItemClick,
                cardWidth = portraitWidth,
            )
        }

        if (shows.isNotEmpty()) {
            BoxSetTypeSection(
                title = stringResource(R.string.section_tv_shows),
                items = shows,
                onItemClick = onItemClick,
                cardWidth = portraitWidth,
            )
        }

        if (seasons.isNotEmpty()) {
            BoxSetTypeSection(
                title = stringResource(R.string.section_seasons),
                items = seasons,
                onItemClick = onItemClick,
                cardWidth = portraitWidth,
            )
        }

        if (episodes.isNotEmpty()) {
            BoxSetEpisodesSection(
                title = stringResource(R.string.section_episodes),
                episodes = episodes,
                onEpisodeClick = { onItemClick(it) },
                cardWidth = landscapeWidth,
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.watchlist_section_header_fmt, title, items.size),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items) { item ->
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
        Text(
            text = stringResource(R.string.watchlist_section_header_fmt, title, episodes.size),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(episodes) { episode ->
                ContinueWatchingCard(
                    item = episode,
                    onClick = { onEpisodeClick(episode) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}
