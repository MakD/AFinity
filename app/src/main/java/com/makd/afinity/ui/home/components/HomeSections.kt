package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMusicVideo
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.GalleryTile
import com.makd.afinity.ui.components.LibraryCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.MusicVideoCardRow
import com.makd.afinity.ui.components.episodeBlur
import com.makd.afinity.ui.components.formatTimeLeftTicks
import com.makd.afinity.ui.components.rememberRatingMetadataScale
import com.makd.afinity.ui.components.shouldBlurEpisode
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import kotlin.math.roundToInt

@Composable
fun OptimizedContinueWatchingSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    scrollState: LazyListState = rememberLazyListState(),
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val fixedRowHeight = CardDimensions.rowHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)

    Column(modifier = Modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = stringResource(R.string.home_continue_watching))

        CardRow(state = scrollState, modifier = Modifier.height(fixedRowHeight)) {
            items(items = items, key = { item -> "continue_${item.id}" }) { item ->
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
fun ContinueReadingSection(
    books: List<AfinityBook>,
    onBookClick: (AfinityBook) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val cardWidth = widthSizeClass.portraitWidth
    val fixedRowHeight =
        CardDimensions.rowHeight(
            cardWidth,
            CardDimensions.ASPECT_RATIO_PORTRAIT,
            metadataHeight = CardDimensions.subtitleLine,
        )

    Column(modifier = Modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = stringResource(R.string.home_continue_reading))

        val firstBookId = books.firstOrNull()?.id
        val scrollState = rememberLazyListState()
        val lastKnownFirstBookId = remember { mutableStateOf<Any?>(null) }

        LaunchedEffect(firstBookId) {
            val previous = lastKnownFirstBookId.value
            if (firstBookId != null && previous != null && firstBookId != previous) {
                scrollState.scrollToItem(0)
            }
            if (firstBookId != null) {
                lastKnownFirstBookId.value = firstBookId
            }
        }

        CardRow(state = scrollState, modifier = Modifier.height(fixedRowHeight)) {
            items(items = books, key = { book -> "continue_reading_${book.id}" }) { book ->
                ContinueReadingCard(
                    book = book,
                    onClick = { onBookClick(book) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(book: AfinityBook, onClick: () -> Unit, cardWidth: Dp) {
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
                    imageUrl = book.images.primaryImageUrl,
                    blurHash = book.images.primaryBlurHash,
                    contentDescription = book.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth / CardDimensions.ASPECT_RATIO_PORTRAIT,
                )
                val progress = book.readingProgress ?: 0f
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier =
                            Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.3f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CardDimensions.CardTextSpacing))

        Text(
            text = book.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = continueReadingSubtitle(book),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LatestGalleryRowSection(
    title: String,
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val fixedRowHeight =
        CardDimensions.rowHeight(
            cardWidth,
            CardDimensions.ASPECT_RATIO_LANDSCAPE,
            metadataHeight = 0.dp,
        )

    Column(modifier = Modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = title)

        val scrollState = rememberLazyListState()
        val firstId = items.firstOrNull()?.id
        val lastKnownFirstId = remember { mutableStateOf<Any?>(null) }
        LaunchedEffect(firstId) {
            val previous = lastKnownFirstId.value
            if (firstId != null && previous != null && firstId != previous) {
                scrollState.scrollToItem(0)
            }
            if (firstId != null) lastKnownFirstId.value = firstId
        }

        CardRow(state = scrollState, modifier = Modifier.height(fixedRowHeight)) {
            items(items = items, key = { item -> "gallery_${item.id}" }) { item ->
                Box(modifier = Modifier.width(cardWidth)) {
                    GalleryTile(
                        item = item,
                        onClick = { onItemClick(item) },
                        fixedRatio = CardDimensions.ASPECT_RATIO_LANDSCAPE,
                        galleryStyle = false,
                        showTitle = true,
                    )
                }
            }
        }
    }
}

@Composable
fun LatestMusicVideoRowSection(
    title: String,
    videos: List<AfinityMusicVideo>,
    onVideoClick: (AfinityMusicVideo) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = title)
        MusicVideoCardRow(videos = videos, onVideoClick = onVideoClick)
    }
}

@Composable
fun AudiobookRowSection(
    title: String,
    books: List<AfinityAudiobook>,
    onBookClick: (AfinityAudiobook) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    showProgress: Boolean,
    onViewAllClick: (() -> Unit)? = null,
) {
    val cardWidth = widthSizeClass.portraitWidth
    val fixedRowHeight =
        CardDimensions.rowHeight(
            cardWidth,
            CardDimensions.ASPECT_RATIO_SQUARE,
            metadataHeight = CardDimensions.subtitleLine,
        )

    Column(modifier = Modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = title, onViewAllClick = onViewAllClick)

        val firstBookId = books.firstOrNull()?.id
        val scrollState = rememberLazyListState()
        val lastKnownFirstBookId = remember { mutableStateOf<Any?>(null) }

        LaunchedEffect(firstBookId) {
            val previous = lastKnownFirstBookId.value
            if (firstBookId != null && previous != null && firstBookId != previous) {
                scrollState.scrollToItem(0)
            }
            if (firstBookId != null) {
                lastKnownFirstBookId.value = firstBookId
            }
        }

        CardRow(state = scrollState, modifier = Modifier.height(fixedRowHeight)) {
            items(items = books, key = { book -> "audiobook_${book.id}" }) { book ->
                AudiobookCard(
                    book = book,
                    onClick = { onBookClick(book) },
                    cardWidth = cardWidth,
                    showProgress = showProgress,
                )
            }
        }
    }
}

@Composable
private fun AudiobookCard(
    book: AfinityAudiobook,
    onClick: () -> Unit,
    cardWidth: Dp,
    showProgress: Boolean,
) {
    Column(modifier = Modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_SQUARE),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    imageUrl = book.images.primaryImageUrl,
                    blurHash = book.images.primaryBlurHash,
                    contentDescription = book.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth,
                )
                val progress = book.listeningProgress ?: 0f
                if (showProgress && progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier =
                            Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.3f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CardDimensions.CardTextSpacing))

        Text(
            text = book.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val ticksLeft = book.runtimeTicks - book.playbackPositionTicks
        val subtitle =
            if (showProgress && book.playbackPositionTicks > 0 && ticksLeft > 0) {
                formatTimeLeftTicks(ticksLeft)
            } else {
                book.authorLine
            }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun continueReadingSubtitle(book: AfinityBook): String {
    val pages = book.pageCount
    return if (pages != null) {
        val page =
            (book.playbackPositionTicks / AfinityBook.TICKS_PER_PAGE)
                .toInt()
                .plus(1)
                .coerceIn(1, pages)
        stringResource(R.string.book_progress_page_fmt, page, pages)
    } else {
        val percent = ((book.readingProgress ?: 0f) * 100).roundToInt().coerceIn(1, 100)
        stringResource(R.string.book_progress_percent_fmt, percent)
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
    val fixedRowHeight = CardDimensions.rowHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)

    Column(modifier = Modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = title)

        val firstItemId = items.firstOrNull()?.id
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

        CardRow(state = scrollState, modifier = Modifier.height(fixedRowHeight)) {
            items(items = items, key = { item -> "latest_movie_${item.id}" }) { item ->
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
    val fixedRowHeight =
        CardDimensions.rowHeight(
            cardWidth,
            CardDimensions.ASPECT_RATIO_LANDSCAPE,
            titleHeight = CardDimensions.textHeight(MaterialTheme.typography.titleMedium),
            metadataHeight = 0.dp,
        )

    Column(modifier = modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = stringResource(R.string.libraries_title))

        CardRow(modifier = Modifier.height(fixedRowHeight)) {
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
fun <T : AfinityItem> OptimizedLatestTvSeriesSection(
    items: List<T>,
    onItemClick: (T) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    title: String = stringResource(R.string.home_latest_tv_series),
    unavailableItemIds: Set<java.util.UUID> = emptySet(),
    onViewAllClick: (() -> Unit)? = null,
    subtitleFor: ((T) -> String)? = null,
) {
    val cardWidth = widthSizeClass.portraitWidth
    val fixedRowHeight =
        if (subtitleFor != null) {
            CardDimensions.rowHeight(
                cardWidth,
                CardDimensions.ASPECT_RATIO_PORTRAIT,
                metadataHeight = CardDimensions.subtitleLine,
            )
        } else {
            CardDimensions.rowHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
        }

    Column(modifier = Modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = title, onViewAllClick = onViewAllClick)

        val firstItemId = items.firstOrNull()?.id
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

        CardRow(state = scrollState, modifier = Modifier.height(fixedRowHeight)) {
            items(items = items, key = { item -> "latest_tv_${item.id}" }) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth,
                    isUnavailable = item.id in unavailableItemIds,
                    subtitle = subtitleFor?.invoke(item),
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
    val fixedRowHeight = CardDimensions.rowHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)

    Column(modifier = modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = title)

        CardRow(modifier = Modifier.height(fixedRowHeight)) {
            items(items = items, key = { item -> "upcoming_${item.id}" }) { episode ->
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
                    modifier =
                        Modifier.fillMaxSize().episodeBlur(shouldBlurEpisode(episode, imageUrl)),
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
                        stringResource(
                            R.string.episode_season_episode_fmt,
                            episode.parentIndexNumber,
                            episode.indexNumber.toString(),
                            episode.name,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
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
                        painter = painterResource(id = R.drawable.ic_community_rating),
                        contentDescription = stringResource(R.string.cd_imdb),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(ratingScale.rtIconSize),
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
    onViewAllClick: (() -> Unit)? = null,
) {
    val fixedRowHeight =
        CardDimensions.rowHeight(CardDimensions.posterCardWidth, CardDimensions.ASPECT_RATIO_SQUARE)
    Column(modifier = modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = title, onViewAllClick = onViewAllClick)
        CardRow(modifier = Modifier.height(fixedRowHeight)) {
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
    onViewAllClick: (() -> Unit)? = null,
) {
    val fixedRowHeight =
        CardDimensions.rowHeight(CardDimensions.posterCardWidth, CardDimensions.ASPECT_RATIO_SQUARE)
    Column(modifier = modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = title, onViewAllClick = onViewAllClick)
        CardRow(modifier = Modifier.height(fixedRowHeight)) {
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
    onViewAllClick: (() -> Unit)? = null,
) {
    val fixedRowHeight =
        CardDimensions.rowHeight(CardDimensions.posterCardWidth, CardDimensions.ASPECT_RATIO_SQUARE)
    Column(modifier = modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = title, onViewAllClick = onViewAllClick)
        CardRow(modifier = Modifier.height(fixedRowHeight)) {
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
