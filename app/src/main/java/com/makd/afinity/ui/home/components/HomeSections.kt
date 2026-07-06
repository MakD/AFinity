package com.makd.afinity.ui.home.components

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
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
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
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
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.LibraryCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.rememberRatingMetadataScale
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun OptimizedContinueWatchingSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    scrollState: LazyListState = rememberLazyListState(),
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val cardHeight =
        CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        HomeSectionHeader(title = stringResource(R.string.home_continue_watching))

        val uniqueItems = items.distinctBy { it.id }

        LazyRow(
            state = scrollState,
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
    title: String = stringResource(R.string.home_latest_movies),
    unavailableItemIds: Set<java.util.UUID> = emptySet(),
) {
    val cardWidth = widthSizeClass.portraitWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        HomeSectionHeader(title = title)

        val uniqueItems = items.distinctBy { it.id }
        val firstItemId = uniqueItems.firstOrNull()?.id
        val scrollState = rememberLazyListState()
        val lastKnownFirstItemId = remember { mutableStateOf<Any?>(null) }

        LaunchedEffect(firstItemId) {
            val previous = lastKnownFirstItemId.value
            if (firstItemId != null && previous != null && firstItemId != previous) {
                scrollState.scrollToItem(0)
            }
            if (firstItemId != null) {
                lastKnownFirstItemId.value = firstItemId
            }
        }

        LazyRow(
            state = scrollState,
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = uniqueItems, key = { item -> "latest_movie_${item.id}" }) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth,
                    isUnavailable = item.id in unavailableItemIds,
                )
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
        HomeSectionHeader(title = stringResource(R.string.libraries_title))

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = libraries, key = { library -> "lib_${library.id}" }) { library ->
                LibraryCard(
                    library = library,
                    modifier = Modifier.width(cardWidth),
                    targetWidth = cardWidth,
                    targetHeight =
                        CardDimensions.calculateHeight(
                            cardWidth,
                            CardDimensions.ASPECT_RATIO_LANDSCAPE,
                        ),
                    titleMaxLines = 1,
                    onClick = { onLibraryClick(library) },
                )
            }
        }
    }
}

@Composable
fun OptimizedLatestTvSeriesSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    title: String = stringResource(R.string.home_latest_tv_series),
    unavailableItemIds: Set<java.util.UUID> = emptySet(),
) {
    val cardWidth = widthSizeClass.portraitWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
        HomeSectionHeader(title = title)

        val uniqueItems = items.distinctBy { it.id }
        val firstItemId = uniqueItems.firstOrNull()?.id
        val scrollState = rememberLazyListState()
        val lastKnownFirstItemId = remember { mutableStateOf<Any?>(null) }

        LaunchedEffect(firstItemId) {
            val previous = lastKnownFirstItemId.value
            if (firstItemId != null && previous != null && firstItemId != previous) {
                scrollState.scrollToItem(0)
            }
            if (firstItemId != null) {
                lastKnownFirstItemId.value = firstItemId
            }
        }

        LazyRow(
            state = scrollState,
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = uniqueItems, key = { item -> "latest_tv_${item.id}" }) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth,
                    isUnavailable = item.id in unavailableItemIds,
                )
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
        HomeSectionHeader(title = title)

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
    val ratingScale = rememberRatingMetadataScale()

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
                    targetWidth = cardWidth,
                    targetHeight = cardWidth / CardDimensions.ASPECT_RATIO_LANDSCAPE,
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
                    text =
                        "S${episode.parentIndexNumber}:E${episode.indexNumber} • ${episode.name}",
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
                        contentDescription = stringResource(R.string.cd_imdb),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(ratingScale.imdbIconSize),
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", imdbRating),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize =
                                    MaterialTheme.typography.bodySmall.fontSize *
                                        ratingScale.textScale
                            ),
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
        HomeSectionHeader(title = title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = items, key = { "abs_${it.id}" }) { download ->
                SquareMediaTile(
                    imageUrl = download.coverUrl,
                    contentDescription = download.title,
                    title = download.title,
                    onClick = { onItemClick(download) },
                )
            }
        }
    }
}

@Composable
fun DownloadedMusicAlbumsSection(
    title: String,
    albums: List<AfinityAlbum>,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 14.dp)) {
        HomeSectionHeader(title = title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = albums, key = { "album_${it.id}" }) { album ->
                SquareMediaTile(
                    imageUrl = album.images.primary?.toString(),
                    contentDescription = album.name,
                    title = album.name,
                    subtitle = album.artist,
                    onClick = { onAlbumClick(album) },
                )
            }
        }
    }
}

@Composable
fun DownloadedMusicTracksSection(
    title: String,
    tracks: List<AfinityTrack>,
    onTrackClick: (AfinityTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 14.dp)) {
        HomeSectionHeader(title = title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = tracks, key = { "track_${it.id}" }) { track ->
                SquareMediaTile(
                    imageUrl = track.images.primary?.toString(),
                    contentDescription = track.name,
                    title = track.name,
                    subtitle = track.artist,
                    onClick = { onTrackClick(track) },
                )
            }
        }
    }
}
