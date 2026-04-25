package com.makd.afinity.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.showBackdropBlurHash
import com.makd.afinity.data.models.extensions.showBackdropImageUrl
import com.makd.afinity.data.models.extensions.showPrimaryBlurHash
import com.makd.afinity.data.models.extensions.showPrimaryImageUrl
import com.makd.afinity.data.models.extensions.showThumbBlurHash
import com.makd.afinity.data.models.extensions.showThumbImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.AsyncImage
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
fun LibrariesSection(
    libraries: List<AfinityCollection>,
    onLibraryClick: (AfinityCollection) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val cardHeight =
        CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 8.dp + 24.dp

    Column(modifier = modifier.padding(horizontal = 14.dp)) {
        Text(
            text = stringResource(R.string.libraries_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = libraries, key = { library -> "lib_${library.id}" }) { library ->
                LibraryHomeCard(
                    library = library,
                    cardWidth = cardWidth,
                    onClick = { onLibraryClick(library) },
                )
            }
        }
    }
}

@Composable
private fun LibraryHomeCard(
    library: AfinityCollection,
    cardWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(cardWidth)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE)) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                AsyncImage(
                    imageUrl = library.images.backdropImageUrl ?: library.images.primaryImageUrl,
                    contentDescription = library.name,
                    blurHash = library.images.backdropBlurHash ?: library.images.primaryBlurHash,
                    targetWidth = cardWidth,
                    targetHeight =
                        CardDimensions.calculateHeight(
                            cardWidth,
                            CardDimensions.ASPECT_RATIO_LANDSCAPE,
                        ),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            if (library.type != CollectionType.Unknown) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                    Icon(
                        painter = painterResource(id = libraryIconRes(library.type)),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                }
            }
        }

        Text(
            text = library.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun libraryIconRes(type: CollectionType): Int =
    when (type) {
        CollectionType.Movies -> R.drawable.ic_movie
        CollectionType.TvShows -> R.drawable.ic_tv
        CollectionType.Music -> R.drawable.ic_music
        CollectionType.Books -> R.drawable.ic_books
        CollectionType.HomeVideos -> R.drawable.ic_music_video
        CollectionType.Playlists -> R.drawable.ic_playlist
        CollectionType.LiveTv -> R.drawable.ic_live_tv_nav
        CollectionType.BoxSets -> R.drawable.ic_collection
        CollectionType.Mixed -> R.drawable.ic_mixed
        CollectionType.Unknown -> R.drawable.ic_folder
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

@Composable
fun UpcomingEpisodesSection(
    items: List<AfinityEpisode>,
    onItemClick: (AfinityEpisode) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.home_upcoming_episodes),
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val cardHeight =
        CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(modifier = modifier.padding(horizontal = 14.dp)) {
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
            items(items = uniqueItems, key = { item -> "upcoming_${item.id}" }) { episode ->
                UpcomingEpisodeCard(
                    episode = episode,
                    onClick = { onItemClick(episode) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
fun UpcomingEpisodeCard(
    episode: AfinityEpisode,
    onClick: () -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val blurHash =
                    episode.images.thumbBlurHash
                        ?: episode.images.showThumbBlurHash
                        ?: episode.images.showBackdropBlurHash
                        ?: episode.images.showPrimaryBlurHash
                        ?: episode.images.primaryBlurHash

                val imageUrl =
                    episode.images.thumbImageUrl
                        ?: episode.images.showThumbImageUrl
                        ?: episode.images.showBackdropImageUrl
                        ?: episode.images.showPrimaryImageUrl
                        ?: episode.images.primaryImageUrl

                AsyncImage(
                    imageUrl = imageUrl,
                    blurHash = blurHash,
                    contentDescription = episode.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = episode.seriesName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (episode.name.isNotBlank()) {
                Text(
                    text = "S${episode.parentIndexNumber}:E${episode.indexNumber} • ${episode.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            episode.communityRating?.let { imdbRating ->
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_imdb_logo),
                        contentDescription = "IMDB",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", imdbRating),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadedAudiobooksSection(
    title: String,
    items: List<AbsDownloadInfo>,
    onItemClick: (AbsDownloadInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = items, key = { "abs_${it.id}" }) { download ->
                Column(modifier = Modifier.width(100.dp).clickable { onItemClick(download) }) {
                    Card(
                        modifier = Modifier.width(100.dp).aspectRatio(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                    ) {
                        AsyncImage(
                            imageUrl = download.coverUrl,
                            contentDescription = download.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = download.title,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(100.dp),
                    )
                }
            }
        }
    }
}
