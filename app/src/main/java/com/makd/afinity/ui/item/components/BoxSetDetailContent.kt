package com.makd.afinity.ui.item.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.models.external.ExternalTitles
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityMusicVideo
import com.makd.afinity.data.models.media.AfinityPhoto
import com.makd.afinity.data.models.media.AfinityPhotoAlbum
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.AfinityVideoPlaylist
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.favorites.FavoriteTrackCard
import com.makd.afinity.ui.item.BoxSetLoad
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle
import com.makd.afinity.ui.item.components.shared.ExternalTitlesSection
import com.makd.afinity.ui.item.components.shared.baseMediaDetailItems
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.music.components.MusicAlbumCard
import com.makd.afinity.ui.music.components.MusicArtistCard
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

fun LazyListScope.boxSetDetailItems(
    item: AfinityBoxSet,
    boxSetItems: List<AfinityItem>,
    albums: List<AfinityAlbum>,
    artists: List<AfinityArtist>,
    tracks: List<AfinityTrack>,
    load: BoxSetLoad,
    onItemClick: (AfinityItem) -> Unit,
    onAlbumClick: ((String) -> Unit)?,
    onArtistClick: ((String) -> Unit)?,
    onTrackFavorite: (AfinityTrack) -> Unit,
    onRetry: () -> Unit,
    missingParts: ExternalTitles?,
    onMissingPartClick: (SearchResultItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    horizontalPadding: Dp,
    detailLayout: DetailLayout,
) {
    val movies = boxSetItems.filterIsInstance<AfinityMovie>()
    val shows = boxSetItems.filterIsInstance<AfinityShow>()
    val seasons = boxSetItems.filterIsInstance<AfinitySeason>()
    val episodes = boxSetItems.filterIsInstance<AfinityEpisode>()
    val videos = boxSetItems.filter { it is AfinityVideo || it is AfinityMusicVideo }
    val books = boxSetItems.filterIsInstance<AfinityBook>()
    val collections = boxSetItems.filterIsInstance<AfinityBoxSet>()
    val photos = boxSetItems.filter { it is AfinityPhotoAlbum || it is AfinityPhoto }
    val audiobooks = boxSetItems.filterIsInstance<AfinityAudiobook>()
    val playlists = boxSetItems.filterIsInstance<AfinityVideoPlaylist>()
    val grouped =
        movies +
            shows +
            seasons +
            episodes +
            videos +
            books +
            collections +
            photos +
            audiobooks +
            playlists
    val groupedIds = grouped.mapTo(HashSet()) { it.id }
    val others = boxSetItems.filterNot { it.id in groupedIds }
    val isEmpty = boxSetItems.isEmpty() && albums.isEmpty() && artists.isEmpty() && tracks.isEmpty()

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
        when {
            load == BoxSetLoad.FAILED && isEmpty ->
                detailItem("boxset_error", horizontalPadding) {
                    BoxSetMessage(
                        text = stringResource(R.string.collection_items_load_failed),
                        onRetry = onRetry,
                    )
                }
            load == BoxSetLoad.LOADED && isEmpty ->
                detailItem("boxset_empty", horizontalPadding) {
                    BoxSetMessage(text = stringResource(R.string.collection_manage_empty))
                }
        }

        val section = { key: String, titleRes: Int, items: List<AfinityItem>, aspectRatio: Float ->
            typeSection(
                key,
                titleRes,
                items,
                aspectRatio,
                onItemClick,
                widthSizeClass,
                horizontalPadding,
            )
        }
        val portrait = CardDimensions.ASPECT_RATIO_PORTRAIT
        val square = CardDimensions.ASPECT_RATIO_SQUARE

        section("boxset_movies", R.string.section_movies, movies, portrait)
        section("boxset_shows", R.string.section_tv_shows, shows, portrait)
        section("boxset_seasons", R.string.section_seasons, seasons, portrait)

        if (episodes.isNotEmpty()) {
            detailItem("boxset_episodes", horizontalPadding) {
                BoxSetEpisodesSection(
                    title = stringResource(R.string.section_episodes),
                    episodes = episodes,
                    onEpisodeClick = { onItemClick(it) },
                    cardWidth = widthSizeClass.landscapeWidth,
                )
            }
        }

        if (videos.isNotEmpty()) {
            detailItem("boxset_videos", horizontalPadding) {
                BoxSetTypeSection(
                    title = stringResource(R.string.section_videos),
                    items = videos,
                    onItemClick = onItemClick,
                    cardWidth = widthSizeClass.landscapeWidth,
                    aspectRatio = CardDimensions.ASPECT_RATIO_LANDSCAPE,
                )
            }
        }

        if (albums.isNotEmpty()) {
            detailItem("boxset_albums", horizontalPadding) {
                BoxSetAlbumsSection(
                    albums = albums,
                    onAlbumClick = { album -> onAlbumClick?.invoke(album.id.toString()) },
                )
            }
        }

        if (artists.isNotEmpty()) {
            detailItem("boxset_artists", horizontalPadding) {
                BoxSetArtistsSection(
                    artists = artists,
                    onArtistClick = { artist -> onArtistClick?.invoke(artist.id.toString()) },
                )
            }
        }

        section("boxset_books", R.string.section_books, books, portrait)
        section("boxset_collections", R.string.section_collections, collections, portrait)
        section("boxset_photos", R.string.section_photos, photos, square)
        section("boxset_audiobooks", R.string.section_audiobooks, audiobooks, square)
        section("boxset_playlists", R.string.section_playlists, playlists, square)
        section("boxset_other", R.string.section_other_items, others, portrait)

        if (tracks.isNotEmpty()) {
            detailItem("boxset_tracks", horizontalPadding) {
                BoxSetTracksSection(tracks = tracks, onTrackFavorite = onTrackFavorite)
            }
        }

        if (missingParts != null) {
            detailItem("boxset_missing", horizontalPadding) {
                ExternalTitlesSection(
                    title = stringResource(R.string.not_in_library_title),
                    titles = missingParts,
                    onSeerrItemClick = onMissingPartClick,
                    cardWidth = widthSizeClass.portraitWidth,
                )
            }
        }
    }
}

private fun LazyListScope.typeSection(
    key: String,
    titleRes: Int,
    items: List<AfinityItem>,
    aspectRatio: Float,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    horizontalPadding: Dp,
) {
    if (items.isEmpty()) return
    detailItem(key, horizontalPadding) {
        BoxSetTypeSection(
            title = stringResource(titleRes),
            items = items,
            onItemClick = onItemClick,
            cardWidth = widthSizeClass.portraitWidth,
            aspectRatio = aspectRatio,
        )
    }
}

@Composable
private fun BoxSetTypeSection(
    title: String,
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    cardWidth: Dp,
    aspectRatio: Float = CardDimensions.ASPECT_RATIO_PORTRAIT,
) {
    val fixedRowHeight = CardDimensions.rowHeight(cardWidth, aspectRatio)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionTitle(text = title)

        CardRow(modifier = Modifier.height(fixedRowHeight)) {
            items(items, key = { it.id.toString() }) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth,
                    aspectRatio = aspectRatio,
                )
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
    val fixedRowHeight = CardDimensions.rowHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionTitle(text = title)

        CardRow(modifier = Modifier.height(fixedRowHeight)) {
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

@Composable
private fun BoxSetAlbumsSection(albums: List<AfinityAlbum>, onAlbumClick: (AfinityAlbum) -> Unit) {
    val cardWidth = CardDimensions.posterCardWidth

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionTitle(text = stringResource(R.string.section_albums))

        CardRow {
            items(albums, key = { it.id.toString() }) { album ->
                MusicAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@Composable
private fun BoxSetArtistsSection(
    artists: List<AfinityArtist>,
    onArtistClick: (AfinityArtist) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionTitle(text = stringResource(R.string.section_artists))

        CardRow {
            items(artists, key = { it.id.toString() }) { artist ->
                MusicArtistCard(
                    name = artist.name,
                    imageUrl = artist.images.primary?.toString(),
                    blurHash = artist.images.primaryImageBlurHash,
                    onClick = { onArtistClick(artist) },
                    size = CardDimensions.posterCardWidth,
                )
            }
        }
    }
}

@Composable
private fun BoxSetTracksSection(
    tracks: List<AfinityTrack>,
    onTrackFavorite: (AfinityTrack) -> Unit,
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val fixedRowHeight =
        CardDimensions.rowHeight(CardDimensions.posterCardWidth, CardDimensions.ASPECT_RATIO_SQUARE)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionTitle(text = stringResource(R.string.section_songs))

        CardRow(modifier = Modifier.height(fixedRowHeight)) {
            itemsIndexed(tracks, key = { _, track -> track.id.toString() }) { index, track ->
                FavoriteTrackCard(
                    track = track,
                    isPlaying = track.id == playbackState.currentTrack?.id,
                    onClick = {
                        startMusicService(context)
                        playerViewModel.playQueue(tracks, index)
                    },
                    onInstantMix = {
                        startMusicService(context)
                        playerViewModel.playInstantMix(track.id)
                    },
                    onAddNext = { playerViewModel.addNext(listOf(track)) },
                    onAddLast = { playerViewModel.addLast(listOf(track)) },
                    onFavorite = { onTrackFavorite(track) },
                )
            }
        }
    }
}

@Composable
private fun BoxSetMessage(text: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}
