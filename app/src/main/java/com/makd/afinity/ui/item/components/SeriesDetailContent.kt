package com.makd.afinity.ui.item.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.showPrimaryImageUrl
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.tmdb.TmdbReview
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.item.components.shared.BaseMediaDetailContent
import com.makd.afinity.ui.item.components.shared.NextUpSection
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun SeriesDetailContent(
    item: AfinityShow,
    seasons: List<AfinitySeason>,
    nextEpisode: AfinityEpisode?,
    specialFeatures: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    tmdbReviews: List<TmdbReview> = emptyList(),
    onEpisodeClick: (AfinityEpisode) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    widthSizeClass: WindowWidthSizeClass,
) {
    BaseMediaDetailContent(
        item = item,
        specialFeatures = specialFeatures,
        containingBoxSets = containingBoxSets,
        tmdbReviews = tmdbReviews,
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
    ) {
        if (nextEpisode != null) {
            NextUpSection(
                episode = nextEpisode,
                onEpisodeClick = onEpisodeClick,
                widthSizeClass = widthSizeClass,
            )
        }

        if (seasons.isNotEmpty()) {
            SeasonsSection(
                seasons = seasons,
                navController = navController,
                widthSizeClass = widthSizeClass,
            )
        }
    }
}

@Composable
internal fun SeasonsSection(
    seasons: List<AfinitySeason>,
    navController: NavController,
    widthSizeClass: WindowWidthSizeClass,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.seasons_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        val cardWidth = widthSizeClass.portraitWidth

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(seasons, key = { it.id.toString() }) { season ->
                SeasonCard(
                    season = season,
                    onClick = {
                        val route =
                            Destination.createEpisodeListRoute(
                                seasonId = season.id.toString(),
                                seasonName = season.name,
                                seriesId = season.seriesId.toString(),
                            )
                        navController.navigate(route)
                    },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
internal fun SeasonCard(season: AfinitySeason, onClick: () -> Unit, cardWidth: Dp) {

    Column(modifier = Modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    imageUrl = season.images.primaryImageUrl ?: season.images.showPrimaryImageUrl,
                    contentDescription = season.name,
                    blurHash =
                        season.images.primaryBlurHash ?: season.images.showPrimaryImageBlurHash,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth * 3f / 2f,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                if (season.played) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = stringResource(R.string.cd_watched_status),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    season.unplayedItemCount?.let { unwatchedCount ->
                        if (unwatchedCount > 0) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            ) {
                                Text(
                                    text =
                                        if (unwatchedCount > 99)
                                            stringResource(R.string.home_episode_count_plus)
                                        else
                                            stringResource(
                                                R.string.home_episode_count_fmt,
                                                unwatchedCount,
                                            ),
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = season.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )

        season.productionYear?.let { year ->
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
