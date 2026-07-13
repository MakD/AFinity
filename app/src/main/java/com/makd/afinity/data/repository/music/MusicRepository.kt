package com.makd.afinity.data.repository.music

import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityLyricLine
import com.makd.afinity.data.models.music.AfinityMusicGenre
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicFilterOptions
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.data.models.music.MusicSearchResults
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

interface MusicRepository {

    fun getBaseUrl(): String

    fun getStreamUrl(trackId: UUID): String

    suspend fun getTracks(
        libraryId: UUID,
        sortBy: ItemSortBy = ItemSortBy.SORT_NAME,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        filters: MusicFilters = MusicFilters(),
        startIndex: Int = 0,
        limit: Int = 50,
        nameStartsWith: String? = null,
    ): List<AfinityTrack>

    suspend fun getAlbums(
        libraryId: UUID,
        sortBy: ItemSortBy = ItemSortBy.SORT_NAME,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        filters: MusicFilters = MusicFilters(),
        startIndex: Int = 0,
        limit: Int = 50,
        nameStartsWith: String? = null,
    ): List<AfinityAlbum>

    suspend fun getArtists(
        libraryId: UUID,
        sortBy: ItemSortBy = ItemSortBy.SORT_NAME,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        filters: MusicFilters = MusicFilters(),
        startIndex: Int = 0,
        limit: Int = 50,
        nameStartsWith: String? = null,
    ): List<AfinityArtist>

    suspend fun getMusicFilterOptions(
        libraryId: UUID,
        itemType: BaseItemKind,
    ): MusicFilterOptions

    suspend fun getAlbumById(albumId: UUID): AfinityAlbum?

    suspend fun getAlbumTracks(albumId: UUID): List<AfinityTrack>

    suspend fun getArtistById(artistId: UUID): AfinityArtist?

    suspend fun getArtistAlbums(artistId: UUID, libraryId: UUID? = null): List<AfinityAlbum>

    suspend fun getArtistTopTracks(artistId: UUID, libraryId: UUID? = null, limit: Int = 10): List<AfinityTrack>

    suspend fun getArtistAppearsOn(artistId: UUID, libraryId: UUID? = null): List<AfinityAlbum>

    suspend fun getPlaylists(libraryId: UUID? = null): List<AfinityPlaylist>

    suspend fun getFavoritePlaylists(): List<AfinityPlaylist>

    fun getPlaylistsFlow(libraryId: UUID? = null): Flow<List<AfinityPlaylist>>

    fun invalidatePlaylistsCache()

    suspend fun getPlaylistById(playlistId: UUID): AfinityPlaylist?

    suspend fun getPlaylistTracks(playlistId: UUID): List<AfinityTrack>

    suspend fun createPlaylist(name: String, trackIds: List<UUID>, isPublic: Boolean): AfinityPlaylist?

    suspend fun addTracksToPlaylist(playlistId: UUID, trackIds: List<UUID>)

    suspend fun removeTracksFromPlaylist(playlistId: UUID, entryIds: List<String>)

    suspend fun deletePlaylist(playlistId: UUID)

    suspend fun getInstantMix(itemId: UUID, limit: Int = 50): List<AfinityTrack>

    suspend fun getArtistRadio(artistId: UUID, limit: Int = 50): List<AfinityTrack>

    suspend fun getSimilarAlbums(itemId: UUID, limit: Int = 5): List<AfinityAlbum>

    suspend fun getLyrics(trackId: UUID): List<AfinityLyricLine>

    suspend fun searchMusic(query: String, libraryId: UUID? = null): MusicSearchResults

    suspend fun setFavorite(itemId: UUID, favorite: Boolean)

    suspend fun getRecentlyPlayedTracks(limit: Int = 20): List<AfinityTrack>

    suspend fun getRecentlyAddedAlbums(limit: Int = 15): List<AfinityAlbum>

    suspend fun getMusicGenres(limit: Int = 10): List<AfinityMusicGenre>

    suspend fun getAllMusicGenres(libraryId: UUID? = null, startIndex: Int = 0, limit: Int = 100): List<AfinityMusicGenre>

    suspend fun getAlbumsByGenre(genreName: String, limit: Int = 15): List<AfinityAlbum>

    suspend fun getArtistsByGenre(genreName: String, limit: Int = 30): List<AfinityArtist>

    suspend fun getRecentlyAddedAlbumsByGenre(genreName: String, limit: Int = 12): List<AfinityAlbum>

    suspend fun getFavoriteArtists(limit: Int = 10): List<AfinityArtist>

    suspend fun getTopArtists(limit: Int = 10): List<AfinityArtist>

    suspend fun getRecentlyPlayedAlbums(limit: Int = 15): List<AfinityAlbum>

    suspend fun getMostPlayedAlbums(limit: Int = 15): List<AfinityAlbum>

    suspend fun getFavoriteTracks(limit: Int = 50): List<AfinityTrack>

    suspend fun getFavoriteAlbums(limit: Int = 15): List<AfinityAlbum>

    suspend fun getRandomAlbums(limit: Int = 15): List<AfinityAlbum>

    suspend fun getRandomArtists(limit: Int = 20): List<AfinityArtist>

    suspend fun getTracksByGenre(genreName: String, limit: Int = 15): List<AfinityTrack>

    suspend fun getRandomTracks(limit: Int = 15): List<AfinityTrack>

    suspend fun getTopRatedAlbums(limit: Int = 15): List<AfinityAlbum>

    suspend fun getAlbumsByDecade(decade: Int, limit: Int = 15): List<AfinityAlbum>

    fun getDownloadedTracksFlow(): Flow<List<AfinityTrack>>

    fun getDownloadedAlbumsFlow(): Flow<List<AfinityAlbum>>

    suspend fun getCachedLyrics(trackId: UUID): List<AfinityLyricLine>?
}