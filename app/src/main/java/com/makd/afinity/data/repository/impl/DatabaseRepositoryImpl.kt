package com.makd.afinity.data.repository.impl

import com.makd.afinity.data.audiobooks.AUDIOBOOK_DOWNLOAD_TYPE
import com.makd.afinity.data.database.dao.BookDao
import com.makd.afinity.data.database.dao.EpisodeDao
import com.makd.afinity.data.database.dao.ItemMetadataCacheDao
import com.makd.afinity.data.database.dao.LastFmArtistDao
import com.makd.afinity.data.database.dao.MediaStreamDao
import com.makd.afinity.data.database.dao.MovieDao
import com.makd.afinity.data.database.dao.MusicAlbumDao
import com.makd.afinity.data.database.dao.MusicLyricsDao
import com.makd.afinity.data.database.dao.MusicTrackDao
import com.makd.afinity.data.database.dao.OfflinePlayDao
import com.makd.afinity.data.database.dao.SeasonDao
import com.makd.afinity.data.database.dao.ServerAddressDao
import com.makd.afinity.data.database.dao.ServerDao
import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.database.dao.ShowDao
import com.makd.afinity.data.database.dao.SourceDao
import com.makd.afinity.data.database.dao.UserDao
import com.makd.afinity.data.database.dao.UserDataDao
import com.makd.afinity.data.database.dao.VideoDao
import com.makd.afinity.data.database.dao.WikidataAwardsDao
import com.makd.afinity.data.database.entities.AfinityAudiobookPartDto
import com.makd.afinity.data.database.entities.AfinityBookDto
import com.makd.afinity.data.database.entities.AfinityEpisodeDto
import com.makd.afinity.data.database.entities.AfinityMovieDto
import com.makd.afinity.data.database.entities.AfinityMusicVideoDto
import com.makd.afinity.data.database.entities.AfinitySeasonDto
import com.makd.afinity.data.database.entities.AfinityShowDto
import com.makd.afinity.data.database.entities.AfinitySourceDto
import com.makd.afinity.data.database.entities.AfinityVideoDto
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.database.entities.ItemMetadataCacheEntity
import com.makd.afinity.data.database.entities.LastFmArtistCacheEntity
import com.makd.afinity.data.database.entities.LastFmPopularCacheEntity
import com.makd.afinity.data.database.entities.MusicLyricsEntity
import com.makd.afinity.data.database.entities.OfflinePlayEntity
import com.makd.afinity.data.database.entities.WikidataAwardsCacheEntity
import com.makd.afinity.data.database.entities.toAfinityAlbum
import com.makd.afinity.data.database.entities.toAfinityAudiobookPartDto
import com.makd.afinity.data.database.entities.toAfinityBookDto
import com.makd.afinity.data.database.entities.toAfinityEpisodeDto
import com.makd.afinity.data.database.entities.toAfinityMediaStreamDto
import com.makd.afinity.data.database.entities.toAfinityMovieDto
import com.makd.afinity.data.database.entities.toAfinityMusicVideoDto
import com.makd.afinity.data.database.entities.toAfinitySeasonDto
import com.makd.afinity.data.database.entities.toAfinitySegmentsDto
import com.makd.afinity.data.database.entities.toAfinityShowDto
import com.makd.afinity.data.database.entities.toAfinitySourceDto
import com.makd.afinity.data.database.entities.toAfinityTrack
import com.makd.afinity.data.database.entities.toAfinityTrickplayInfoDto
import com.makd.afinity.data.database.entities.toAfinityVideoDto
import com.makd.afinity.data.database.entities.toMusicAlbumEntity
import com.makd.afinity.data.database.entities.toMusicTrackEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.download.StoredItemRemoval
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityMusicVideo
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinityTrickplayInfo
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.BookFormat
import com.makd.afinity.data.models.media.toAfinityMediaStream
import com.makd.afinity.data.models.media.toAfinitySegment
import com.makd.afinity.data.models.media.toAfinitySource
import com.makd.afinity.data.models.media.toAfinityTrickplayInfo
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.models.server.ServerWithAddresses
import com.makd.afinity.data.models.server.ServerWithAddressesAndUsers
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.model.api.ExtraType
import org.jellyfin.sdk.model.api.UserItemDataDto

private const val MOVIE_DOWNLOAD_TYPE = "Movie"
private const val EPISODE_DOWNLOAD_TYPE = "Episode"
private const val AUDIO_DOWNLOAD_TYPE = "Audio"

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DatabaseRepositoryImpl
@Inject
constructor(
    private val sessionManagerProvider: Provider<SessionManager>,
    private val serverDao: ServerDao,
    private val serverAddressDao: ServerAddressDao,
    private val userDao: UserDao,
    private val movieDao: MovieDao,
    private val showDao: ShowDao,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao,
    private val sourceDao: SourceDao,
    private val mediaStreamDao: MediaStreamDao,
    private val userDataDao: UserDataDao,
    private val serverDatabaseDao: ServerDatabaseDao,
    private val itemMetadataCacheDao: ItemMetadataCacheDao,
    private val wikidataAwardsDao: WikidataAwardsDao,
    private val lastFmArtistDao: LastFmArtistDao,
    private val musicTrackDao: MusicTrackDao,
    private val musicAlbumDao: MusicAlbumDao,
    private val musicLyricsDao: MusicLyricsDao,
    private val offlinePlayDao: OfflinePlayDao,
    private val videoDao: VideoDao,
    private val bookDao: BookDao,
) : DatabaseRepository {

    private val sessionManager: SessionManager
        get() = sessionManagerProvider.get()

    override suspend fun insertServer(server: Server) {
        serverDao.upsertServer(server)
    }

    override suspend fun updateServer(server: Server) {
        serverDao.updateServer(server)
    }

    override suspend fun deleteServer(serverId: String) {
        serverDao.deleteServerById(serverId)
    }

    override suspend fun getServer(serverId: String): Server? {
        return serverDao.getServer(serverId)
    }

    override suspend fun getAllServers(): List<Server> {
        return serverDao.getAllServers()
    }

    override fun getAllServersFlow(): Flow<List<Server>> {
        return serverDao.getAllServersFlow()
    }

    override suspend fun insertServerAddress(serverAddress: ServerAddress) {
        serverAddressDao.insertServerAddress(serverAddress)
    }

    override suspend fun updateServerAddress(serverAddress: ServerAddress) {
        serverAddressDao.updateServerAddress(serverAddress)
    }

    override suspend fun deleteServerAddress(addressId: UUID) {
        serverAddressDao.deleteServerAddressById(addressId)
    }

    override suspend fun getServerAddresses(serverId: String): List<ServerAddress> {
        return serverAddressDao.getServerAddresses(serverId)
    }

    override suspend fun getServerAddressByUrl(serverId: String, address: String): ServerAddress? {
        return serverAddressDao.getServerAddressByUrl(serverId, address)
    }

    override suspend fun getServerWithAddresses(serverId: String): ServerWithAddresses? {
        return serverDao.getServerWithAddresses(serverId)
    }

    override suspend fun getServerWithAddressesAndUsers(
        serverId: String
    ): ServerWithAddressesAndUsers? {
        return serverDao.getServerWithAddressesAndUsers(serverId)
    }

    override suspend fun insertUser(user: User) {
        userDao.insertUser(user)
    }

    override suspend fun updateUser(user: User) {
        userDao.updateUser(user)
    }

    override suspend fun deleteUser(userId: UUID) {
        userDao.deleteUserById(userId)
    }

    override suspend fun getUser(userId: UUID): User? {
        return userDao.getUser(userId)
    }

    override suspend fun getUsersForServer(serverId: String): List<User> {
        return userDao.getUsersForServer(serverId)
    }

    override suspend fun getCurrentUser(): User? {
        return userDao.getCurrentUser()
    }

    override suspend fun getAllUsers(): List<User> {
        return userDao.getAllUsers()
    }

    override suspend fun insertMovie(movie: AfinityMovie, serverId: String?) {
        val actualServerId = serverId ?: sessionManager.currentSession.value?.serverId ?: return
        movieDao.insertMovie(movie.toAfinityMovieDto(actualServerId))
    }

    override suspend fun updateMovie(movie: AfinityMovie) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        movieDao.updateMovie(movie.toAfinityMovieDto(serverId))
    }

    override suspend fun updateMovieExternalIds(
        movieId: UUID,
        serverId: String,
        providerIds: Map<String, String>?,
        externalUrls: List<AfinityExternalUrl>?,
    ) {
        movieDao.updateExternalIds(movieId, serverId, providerIds, externalUrls)
    }

    override suspend fun deleteMovie(movieId: UUID) {
        movieDao.deleteMovieById(movieId)
    }

    override suspend fun getMovie(movieId: UUID, userId: UUID): AfinityMovie? {
        val serverId = sessionManager.currentSession.value?.serverId ?: return null
        val movieDto = movieDao.getMovie(movieId, serverId) ?: return null
        return movieDto.toAfinityMovie(serverDatabaseDao, userId)
    }

    override suspend fun getAllMovies(userId: UUID, serverId: String?): List<AfinityMovie> {
        val actualServerId =
            serverId ?: sessionManager.currentSession.value?.serverId ?: return emptyList()
        return movieDao.getMovies(actualServerId).map {
            it.toAfinityMovie(serverDatabaseDao, userId)
        }
    }

    override suspend fun searchMovies(query: String, userId: UUID): List<AfinityMovie> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return movieDao.searchMovies(query, serverId).map {
            it.toAfinityMovie(serverDatabaseDao, userId)
        }
    }

    override fun getMoviesFlow(userId: UUID): Flow<List<AfinityMovie>> {
        return sessionManager.currentSession.filterNotNull().flatMapLatest { session ->
            movieDao.getMoviesFlow(session.serverId).map { movieDtos ->
                movieDtos.map { it.toAfinityMovie(serverDatabaseDao, userId) }
            }
        }
    }

    override suspend fun insertVideo(video: AfinityVideo, serverId: String?) {
        val actualServerId = serverId ?: sessionManager.currentSession.value?.serverId ?: return
        videoDao.insertVideo(video.toAfinityVideoDto(actualServerId))
    }

    override suspend fun getVideo(videoId: UUID, userId: UUID): AfinityVideo? {
        val serverId = sessionManager.currentSession.value?.serverId ?: return null
        return videoDao.getVideo(videoId, serverId)?.toAfinityVideo(serverDatabaseDao, userId)
    }

    override suspend fun getAllVideos(userId: UUID): List<AfinityVideo> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return videoDao.getVideos(serverId).map { it.toAfinityVideo(serverDatabaseDao, userId) }
    }

    override suspend fun insertMusicVideo(musicVideo: AfinityMusicVideo, serverId: String?) {
        val actualServerId = serverId ?: sessionManager.currentSession.value?.serverId ?: return
        videoDao.insertMusicVideo(musicVideo.toAfinityMusicVideoDto(actualServerId))
    }

    override suspend fun getMusicVideo(musicVideoId: UUID, userId: UUID): AfinityMusicVideo? {
        val serverId = sessionManager.currentSession.value?.serverId ?: return null
        return videoDao
            .getMusicVideo(musicVideoId, serverId)
            ?.toAfinityMusicVideo(serverDatabaseDao, userId)
    }

    override suspend fun getAllMusicVideos(userId: UUID): List<AfinityMusicVideo> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return videoDao.getMusicVideos(serverId).map {
            it.toAfinityMusicVideo(serverDatabaseDao, userId)
        }
    }

    override suspend fun insertBook(book: AfinityBook, serverId: String) {
        bookDao.insertBook(book.toAfinityBookDto(serverId))
    }

    override suspend fun getBook(bookId: UUID, userId: UUID): AfinityBook? {
        val serverId = sessionManager.currentSession.value?.serverId ?: return null
        return bookDao.getBook(bookId, serverId)?.toAfinityBook(serverDatabaseDao, userId)
    }

    override suspend fun getBooksInSeries(seriesName: String, userId: UUID): List<AfinityBook> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return bookDao.getBooksInSeries(serverId, seriesName).map {
            it.toAfinityBook(serverDatabaseDao, userId)
        }
    }

    override suspend fun insertAudiobookPart(part: AfinityAudiobook, serverId: String) {
        bookDao.insertAudiobookPart(part.toAfinityAudiobookPartDto(serverId))
    }

    override suspend fun recordAudiobookParts(parts: List<AfinityAudiobook>, serverId: String) {
        if (parts.isEmpty()) return
        bookDao.insertAudiobookPartsIfAbsent(parts.map { it.toAfinityAudiobookPartDto(serverId) })
        bookDao.updateBookPartCount(parts.map { it.id }, serverId, parts.size)
    }

    override suspend fun getAudiobookPartCount(partId: UUID, serverId: String): Int? =
        bookDao.getBookPartCount(partId, serverId)

    override suspend fun removeStoredItem(download: DownloadDto): StoredItemRemoval {
        val itemId = download.itemId
        if (serverDatabaseDao.getDownloadByItemId(itemId) != null) return StoredItemRemoval()
        val serverId = download.serverId
        val userId = download.userId
        return when (download.itemType) {
            MOVIE_DOWNLOAD_TYPE -> {
                movieDao.deleteMovieById(itemId)
                StoredItemRemoval()
            }
            EPISODE_DOWNLOAD_TYPE -> removeStoredEpisode(itemId, serverId, userId)
            JellyfinDownloadRepository.VIDEO_ITEM_TYPE -> {
                videoDao.deleteVideo(itemId)
                StoredItemRemoval()
            }
            JellyfinDownloadRepository.MUSIC_VIDEO_ITEM_TYPE -> {
                videoDao.deleteMusicVideo(itemId)
                StoredItemRemoval()
            }
            JellyfinDownloadRepository.BOOK_ITEM_TYPE -> {
                bookDao.deleteBook(itemId)
                StoredItemRemoval()
            }
            AUDIOBOOK_DOWNLOAD_TYPE -> {
                bookDao.deleteAudiobookPart(itemId)
                StoredItemRemoval()
            }
            AUDIO_DOWNLOAD_TYPE -> removeStoredTrack(download)
            else -> StoredItemRemoval()
        }
    }

    private suspend fun removeStoredEpisode(
        episodeId: UUID,
        serverId: String,
        userId: UUID,
    ): StoredItemRemoval {
        val episode = serverDatabaseDao.getEpisode(episodeId) ?: return StoredItemRemoval()
        serverDatabaseDao.deleteEpisode(episodeId)
        val seriesId = episode.seriesId
        if (
            serverDatabaseDao.countDownloadsWithSeriesId(seriesId.toString(), serverId, userId) == 0
        ) {
            serverDatabaseDao.deleteShow(seriesId)
            return StoredItemRemoval(showId = seriesId)
        }
        if (serverDatabaseDao.getEpisodesForSeason(episode.seasonId).isEmpty()) {
            serverDatabaseDao.deleteSeason(episode.seasonId)
        }
        return StoredItemRemoval()
    }

    private suspend fun removeStoredTrack(download: DownloadDto): StoredItemRemoval {
        val serverId = download.serverId
        val userIdText = download.userId.toString()
        val trackId = download.itemId.toString()
        musicTrackDao.delete(trackId, serverId, userIdText)
        val albumId = download.seriesId ?: return StoredItemRemoval()
        if (serverDatabaseDao.countDownloadsWithSeriesId(albumId, serverId, download.userId) == 0) {
            musicAlbumDao.delete(albumId, serverId, userIdText)
            return StoredItemRemoval(albumId = runCatching { UUID.fromString(albumId) }.getOrNull())
        }
        val album = musicAlbumDao.getById(albumId, serverId, userIdText)
        if (album?.localImagePath?.contains("/$trackId/") == true) {
            musicTrackDao
                .getByAlbum(albumId, serverId, userIdText)
                .firstNotNullOfOrNull { track ->
                    track.localImagePath?.takeIf { track.localFilePath != null }
                }
                ?.let { cover ->
                    musicAlbumDao.updateLocalImagePath(albumId, serverId, userIdText, cover)
                }
        }
        return StoredItemRemoval()
    }

    override suspend fun getAudiobookPart(partId: UUID, userId: UUID): AfinityAudiobook? {
        val serverId = sessionManager.currentSession.value?.serverId ?: return null
        return bookDao
            .getAudiobookPart(partId, serverId)
            ?.toAfinityAudiobook(serverDatabaseDao, userId)
    }

    override suspend fun insertShow(show: AfinityShow, serverId: String?) {
        val actualServerId = serverId ?: sessionManager.currentSession.value?.serverId ?: return
        showDao.insertShow(show.toAfinityShowDto(actualServerId))
    }

    override suspend fun updateShow(show: AfinityShow) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        showDao.updateShow(show.toAfinityShowDto(serverId))
    }

    override suspend fun updateShowExternalIds(
        showId: UUID,
        serverId: String,
        providerIds: Map<String, String>?,
        externalUrls: List<AfinityExternalUrl>?,
        tagline: String?,
    ) {
        showDao.updateExternalIds(showId, serverId, providerIds, externalUrls, tagline)
    }

    override suspend fun deleteShow(showId: UUID) {
        showDao.deleteShowById(showId)
    }

    override suspend fun getShow(showId: UUID, userId: UUID): AfinityShow? {
        val serverId = sessionManager.currentSession.value?.serverId ?: return null
        val showDto = showDao.getShow(showId, serverId) ?: return null
        return showDto.toAfinityShow(serverDatabaseDao, userId)
    }

    override suspend fun getAllShows(userId: UUID, serverId: String?): List<AfinityShow> {
        val actualServerId =
            serverId ?: sessionManager.currentSession.value?.serverId ?: return emptyList()
        return showDao.getShows(actualServerId).map { it.toAfinityShow(serverDatabaseDao, userId) }
    }

    override suspend fun searchShows(query: String, userId: UUID): List<AfinityShow> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return showDao.searchShows(query, serverId).map {
            it.toAfinityShow(serverDatabaseDao, userId)
        }
    }

    override fun getShowsFlow(userId: UUID): Flow<List<AfinityShow>> {
        return sessionManager.currentSession.filterNotNull().flatMapLatest { session ->
            showDao.getShowsFlow(session.serverId).map { showDtos ->
                showDtos.map { it.toAfinityShow(serverDatabaseDao, userId) }
            }
        }
    }

    override suspend fun insertSeason(season: AfinitySeason, serverId: String?) {
        val actualServerId = serverId ?: sessionManager.currentSession.value?.serverId ?: return
        seasonDao.insertSeason(season.toAfinitySeasonDto(actualServerId))
    }

    override suspend fun updateSeason(season: AfinitySeason) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        seasonDao.updateSeason(season.toAfinitySeasonDto(serverId))
    }

    override suspend fun updateSeasonDates(
        seasonId: UUID,
        serverId: String,
        productionYear: Int?,
        premiereDate: LocalDateTime?,
    ) {
        seasonDao.updateDates(seasonId, serverId, productionYear, premiereDate)
    }

    override suspend fun deleteSeason(seasonId: UUID) {
        seasonDao.deleteSeasonById(seasonId)
    }

    override suspend fun getSeason(seasonId: UUID, userId: UUID): AfinitySeason? {
        val serverId = sessionManager.currentSession.value?.serverId ?: return null
        val seasonDto = seasonDao.getSeason(seasonId, serverId) ?: return null
        return seasonDto.toAfinitySeason(serverDatabaseDao, userId)
    }

    override suspend fun getSeasonsForShow(showId: UUID, userId: UUID): List<AfinitySeason> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return seasonDao.getSeasonsForSeries(showId, serverId).map {
            it.toAfinitySeason(serverDatabaseDao, userId)
        }
    }

    override suspend fun insertEpisode(episode: AfinityEpisode, serverId: String?) {
        val actualServerId = serverId ?: sessionManager.currentSession.value?.serverId ?: return
        episodeDao.insertEpisode(episode.toAfinityEpisodeDto(actualServerId))
    }

    override suspend fun updateEpisode(episode: AfinityEpisode) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        episodeDao.updateEpisode(episode.toAfinityEpisodeDto(serverId))
    }

    override suspend fun deleteEpisode(episodeId: UUID) {
        episodeDao.deleteEpisodeById(episodeId)
    }

    override suspend fun getEpisode(episodeId: UUID, userId: UUID): AfinityEpisode? {
        val serverId = sessionManager.currentSession.value?.serverId ?: return null
        val episodeDto = episodeDao.getEpisode(episodeId, serverId) ?: return null
        return episodeDto.toAfinityEpisode(serverDatabaseDao, userId)
    }

    override suspend fun getEpisodesForSeason(seasonId: UUID, userId: UUID): List<AfinityEpisode> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return episodeDao.getEpisodesForSeason(seasonId, serverId).map {
            it.toAfinityEpisode(serverDatabaseDao, userId)
        }
    }

    override suspend fun getEpisodesForShow(showId: UUID, userId: UUID): List<AfinityEpisode> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return episodeDao.getEpisodesForSeries(showId, serverId).map {
            it.toAfinityEpisode(serverDatabaseDao, userId)
        }
    }

    override suspend fun getNextEpisodesToWatch(userId: UUID, limit: Int): List<AfinityEpisode> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return episodeDao.getContinueWatchingEpisodes(userId, serverId, limit).map {
            it.toAfinityEpisode(serverDatabaseDao, userId)
        }
    }

    override suspend fun searchEpisodes(query: String, userId: UUID): List<AfinityEpisode> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return episodeDao.searchEpisodes(query, serverId).map {
            it.toAfinityEpisode(serverDatabaseDao, userId)
        }
    }

    override fun getEpisodesFlow(seasonId: UUID, userId: UUID): Flow<List<AfinityEpisode>> {
        return sessionManager.currentSession.filterNotNull().flatMapLatest { session ->
            episodeDao.getEpisodesForSeasonFlow(seasonId, session.serverId).map { episodeDtos ->
                episodeDtos.map { it.toAfinityEpisode(serverDatabaseDao, userId) }
            }
        }
    }

    override suspend fun insertSource(source: AfinitySource, itemId: UUID) {
        sourceDao.insertSource(source.toAfinitySourceDto(itemId, source.path))
    }

    override suspend fun updateSource(source: AfinitySource) {
        val sourceDto = sourceDao.getSource(source.id)
        if (sourceDto != null) {
            sourceDao.updateSource(source.toAfinitySourceDto(sourceDto.itemId, source.path))
        }
    }

    override suspend fun deleteSource(sourceId: String) {
        sourceDao.deleteSourceById(sourceId)
    }

    override suspend fun getSource(sourceId: String): AfinitySource? {
        val sourceDto = sourceDao.getSource(sourceId) ?: return null
        return sourceDto.toAfinitySource(serverDatabaseDao)
    }

    override suspend fun getSourcesForItem(itemId: UUID): List<AfinitySource> {
        return sourceDao.getSourcesForItem(itemId).map { it.toAfinitySource(serverDatabaseDao) }
    }

    override suspend fun insertMediaStream(stream: AfinityMediaStream, sourceId: String) {
        mediaStreamDao.insertMediaStream(
            stream.toAfinityMediaStreamDto(UUID.randomUUID(), sourceId, stream.path ?: "")
        )
    }

    override suspend fun deleteMediaStream(streamId: UUID) {
        mediaStreamDao.deleteMediaStreamById(streamId)
    }

    override suspend fun getMediaStreamsForSource(sourceId: String): List<AfinityMediaStream> {
        return mediaStreamDao.getMediaStreamsBySourceId(sourceId).map { it.toAfinityMediaStream() }
    }

    override suspend fun insertTrickplayInfo(
        trickplayInfo: AfinityTrickplayInfo,
        sourceId: String,
    ) {
        serverDatabaseDao.insertTrickplayInfo(trickplayInfo.toAfinityTrickplayInfoDto(sourceId))
    }

    override suspend fun getTrickplayInfo(sourceId: String): AfinityTrickplayInfo? {
        return serverDatabaseDao.getTrickplayInfo(sourceId)?.toAfinityTrickplayInfo()
    }

    override suspend fun insertSegment(segment: AfinitySegment, itemId: UUID) {
        serverDatabaseDao.insertSegment(segment.toAfinitySegmentsDto(itemId))
    }

    override suspend fun updateSegment(segment: AfinitySegment, itemId: UUID) {
        serverDatabaseDao.insertSegment(segment.toAfinitySegmentsDto(itemId))
    }

    override suspend fun getSegmentsForItem(itemId: UUID): List<AfinitySegment> {
        return serverDatabaseDao.getSegmentsForItem(itemId).map { it.toAfinitySegment() }
    }

    override suspend fun replaceSegmentsForItem(itemId: UUID, segments: List<AfinitySegment>) {
        serverDatabaseDao.replaceSegmentsForItem(
            itemId,
            segments.map { it.toAfinitySegmentsDto(itemId) },
        )
    }

    override suspend fun insertUserData(userData: AfinityUserDataDto) {
        userDataDao.insertUserData(userData)
    }

    override suspend fun updateUserData(userData: AfinityUserDataDto) {
        userDataDao.updateUserData(userData)
    }

    override suspend fun patchUserDataLocally(
        itemId: UUID,
        userId: UUID,
        serverId: String,
        userData: UserItemDataDto,
    ) {
        userDataDao.patchUserDataLocally(
            itemId = itemId,
            userId = userId,
            serverId = serverId,
            isPlayed = userData.played,
            positionTicks = userData.playbackPositionTicks,
            isFavorite = userData.isFavorite,
            isLiked = userData.likes ?: false,
            unplayedItemCount = userData.unplayedItemCount,
            playCount = userData.playCount,
        )
    }

    override suspend fun deleteUserData(userId: UUID, itemId: UUID) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        userDataDao.deleteUserDataByIds(userId, itemId, serverId)
    }

    override suspend fun getUserData(userId: UUID, itemId: UUID): AfinityUserDataDto? {
        val serverId = sessionManager.currentSession.value?.serverId ?: return null
        return userDataDao.getUserData(userId, itemId, serverId)
    }

    override suspend fun getUserDataOrCreateNew(itemId: UUID, userId: UUID): AfinityUserDataDto {
        val serverId = sessionManager.currentSession.value?.serverId ?: ""
        return serverDatabaseDao.getUserDataOrCreateNew(itemId, userId, serverId)
    }

    override suspend fun getAllUserDataToSync(userId: UUID): List<AfinityUserDataDto> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return userDataDao.getUnsyncedUserData(userId, serverId)
    }

    override suspend fun getAllUserDataToSync(
        userId: UUID,
        serverId: String,
    ): List<AfinityUserDataDto> {
        return userDataDao.getUnsyncedUserData(userId, serverId)
    }

    override suspend fun countUserDataToSync(): Int {
        return userDataDao.countUserDataToSync() + offlinePlayDao.countAll()
    }

    override suspend fun insertOfflinePlay(
        itemId: UUID,
        userId: UUID,
        serverId: String,
        playedAt: Long,
    ) {
        offlinePlayDao.insert(
            OfflinePlayEntity(
                itemId = itemId,
                userId = userId,
                serverId = serverId,
                playedAt = playedAt,
            )
        )
    }

    override suspend fun getOfflinePlays(userId: UUID, serverId: String): List<OfflinePlayEntity> =
        offlinePlayDao.getForAccount(userId, serverId)

    override suspend fun deleteOfflinePlay(id: Long) {
        offlinePlayDao.deleteById(id)
    }

    override fun getAllUserDataFlow(userId: UUID): Flow<List<AfinityUserDataDto>> {
        return sessionManager.currentSession.filterNotNull().flatMapLatest { session ->
            userDataDao.getAllUserDataFlow(userId, session.serverId)
        }
    }

    override suspend fun markUserDataSynced(userId: UUID, itemId: UUID) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        userDataDao.markUserDataSynced(userId, itemId, serverId)
    }

    override suspend fun markUserDataSynced(userId: UUID, itemId: UUID, serverId: String) {
        userDataDao.markUserDataSynced(userId, itemId, serverId)
    }

    override suspend fun getFavoriteMovies(userId: UUID): List<AfinityMovie> {
        return serverDatabaseDao.getFavoriteMovies(userId).map {
            it.toAfinityMovie(serverDatabaseDao, userId)
        }
    }

    override suspend fun getFavoriteShows(userId: UUID): List<AfinityShow> {
        return serverDatabaseDao.getFavoriteShows(userId).map {
            it.toAfinityShow(serverDatabaseDao, userId)
        }
    }

    override suspend fun getFavoriteEpisodes(userId: UUID): List<AfinityEpisode> {
        return serverDatabaseDao.getFavoriteEpisodes(userId).map {
            it.toAfinityEpisode(serverDatabaseDao, userId)
        }
    }

    override fun getFavoriteMoviesFlow(userId: UUID): Flow<List<AfinityMovie>> {
        return sessionManager.currentSession.filterNotNull().flatMapLatest { session ->
            userDataDao.getFavoriteItemsFlow(userId, session.serverId).map { userDataList ->
                val movieIds = userDataList.map { it.itemId }
                movieIds.mapNotNull { movieId -> getMovie(movieId, userId) }
            }
        }
    }

    override fun getFavoriteShowsFlow(userId: UUID): Flow<List<AfinityShow>> {
        return sessionManager.currentSession.filterNotNull().flatMapLatest { session ->
            userDataDao.getFavoriteItemsFlow(userId, session.serverId).map { userDataList ->
                val showIds = userDataList.map { it.itemId }
                showIds.mapNotNull { showId -> getShow(showId, userId) }
            }
        }
    }

    override suspend fun getRecentlyWatchedItems(userId: UUID, limit: Int): List<AfinityItem> {
        val movies = getContinueWatchingMovies(userId, limit / 2)
        val episodes = getContinueWatchingEpisodes(userId, limit / 2)
        return (movies + episodes).take(limit)
    }

    override suspend fun getContinueWatchingMovies(userId: UUID, limit: Int): List<AfinityMovie> {
        return serverDatabaseDao.getContinueWatchingMovies(userId, limit).map {
            it.toAfinityMovie(serverDatabaseDao, userId)
        }
    }

    override suspend fun getContinueWatchingEpisodes(
        userId: UUID,
        limit: Int,
    ): List<AfinityEpisode> {
        return serverDatabaseDao.getContinueWatchingEpisodes(userId, limit).map {
            it.toAfinityEpisode(serverDatabaseDao, userId)
        }
    }

    override fun getContinueWatchingFlow(userId: UUID): Flow<List<AfinityItem>> {
        return sessionManager.currentSession.filterNotNull().flatMapLatest { session ->
            userDataDao.getContinueWatchingItemsFlow(userId, session.serverId).map { userDataList ->
                val itemIds = userDataList.map { it.itemId }
                val items = mutableListOf<AfinityItem>()

                itemIds.forEach { itemId ->
                    getMovie(itemId, userId)?.let { items.add(it) }
                        ?: getEpisode(itemId, userId)?.let { items.add(it) }
                }

                items
            }
        }
    }

    override suspend fun searchAllItems(query: String, userId: UUID): List<AfinityItem> {
        val movies = searchMovies(query, userId)
        val shows = searchShows(query, userId)
        val episodes = searchEpisodes(query, userId)
        return movies + shows + episodes
    }

    override suspend fun clearAllData() {
        serverDatabaseDao.clearAllData()
    }

    override suspend fun clearServerData(serverId: String) {
        serverDatabaseDao.clearAllDataForServer(serverId)
    }

    override suspend fun clearUserData(userId: UUID) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        userDataDao.deleteUserDataByUserId(userId, serverId)
        offlinePlayDao.deleteForAccount(userId, serverId)
    }

    private suspend fun AfinityMovieDto.toAfinityMovie(
        database: ServerDatabaseDao,
        userId: UUID,
    ): AfinityMovie {
        val userData = database.getUserDataOrCreateNew(id, userId, serverId)
        val sources = database.getSources(id).map { it.toAfinitySource(database) }
        val trickplayInfos = mutableMapOf<String, List<AfinityTrickplayInfo>>()
        for (source in sources) {
            database.getTrickplayInfo(source.id)?.toAfinityTrickplayInfo()?.let {
                trickplayInfos[source.id] = listOf(it)
            }
        }
        return AfinityMovie(
            id = id,
            name = name,
            originalTitle = originalTitle,
            overview = overview,
            played = userData.played,
            favorite = userData.favorite,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = userData.playbackPositionTicks,
            premiereDate = premiereDate,
            dateCreated = dateCreated,
            genres = genres ?: emptyList(),
            people = people ?: emptyList(),
            communityRating = communityRating,
            officialRating = officialRating,
            criticRating = criticRating,
            status = status,
            productionYear = productionYear,
            endDate = endDate,
            canDownload = false,
            canPlay = true,
            sources = sources,
            trailer = null,
            images = images ?: AfinityImages(),
            chapters = chapters ?: emptyList(),
            trickplayInfo = trickplayInfos,
            tagline = tagline,
            providerIds = providerIds,
            externalUrls = externalUrls,
            liked = false,
            tmdbReviews = this.tmdbReviews ?: emptyList(),
            mdbRatings = this.mdbRatings ?: emptyList(),
        )
    }

    private suspend fun storedTrickplay(
        database: ServerDatabaseDao,
        sources: List<AfinitySource>,
    ): Map<String, List<AfinityTrickplayInfo>> {
        val trickplayInfos = mutableMapOf<String, List<AfinityTrickplayInfo>>()
        for (source in sources) {
            database.getTrickplayInfo(source.id)?.toAfinityTrickplayInfo()?.let {
                trickplayInfos[source.id] = listOf(it)
            }
        }
        return trickplayInfos
    }

    private suspend fun AfinityVideoDto.toAfinityVideo(
        database: ServerDatabaseDao,
        userId: UUID,
    ): AfinityVideo {
        val userData = database.getUserDataOrCreateNew(id, userId, serverId)
        val sources = database.getSources(id).map { it.toAfinitySource(database) }
        return AfinityVideo(
            id = id,
            name = name,
            originalTitle = originalTitle,
            overview = overview,
            played = userData.played,
            favorite = userData.favorite,
            liked = false,
            canPlay = true,
            canDownload = false,
            sources = sources,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = userData.playbackPositionTicks,
            unplayedItemCount = null,
            images = images ?: AfinityImages(),
            chapters = chapters ?: emptyList(),
            providerIds = providerIds,
            externalUrls = externalUrls,
            premiereDate = premiereDate,
            people = people ?: emptyList(),
            genres = genres ?: emptyList(),
            communityRating = communityRating,
            officialRating = officialRating,
            criticRating = criticRating,
            status = status,
            productionYear = productionYear,
            endDate = endDate,
            trailer = null,
            tagline = tagline,
            trickplayInfo = storedTrickplay(database, sources).values.firstOrNull()?.firstOrNull(),
            extraType =
                extraType?.let { name -> ExtraType.entries.firstOrNull { it.name == name } },
        )
    }

    private suspend fun AfinityMusicVideoDto.toAfinityMusicVideo(
        database: ServerDatabaseDao,
        userId: UUID,
    ): AfinityMusicVideo {
        val userData = database.getUserDataOrCreateNew(id, userId, serverId)
        val sources = database.getSources(id).map { it.toAfinitySource(database) }
        return AfinityMusicVideo(
            id = id,
            name = name,
            originalTitle = originalTitle,
            overview = overview,
            sources = sources,
            played = userData.played,
            favorite = userData.favorite,
            liked = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = userData.playbackPositionTicks,
            images = images ?: AfinityImages(),
            chapters = chapters ?: emptyList(),
            trickplayInfo = storedTrickplay(database, sources),
            providerIds = providerIds,
            externalUrls = externalUrls,
            artists = artists ?: emptyList(),
            artistItems = artistItems ?: emptyList(),
            album = album,
            albumId = albumId,
            parentId = parentId,
            premiereDate = premiereDate,
            dateCreated = dateCreated,
            productionYear = productionYear,
            people = people ?: emptyList(),
            genres = genres ?: emptyList(),
            studios = studios ?: emptyList(),
            tags = tags ?: emptyList(),
            communityRating = communityRating,
            officialRating = officialRating,
            criticRating = criticRating,
            tagline = tagline,
            mediaSourceCount = sources.size,
        )
    }

    private fun Long.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

    private suspend fun AfinityBookDto.toAfinityBook(
        database: ServerDatabaseDao,
        userId: UUID,
    ): AfinityBook {
        val userData = database.getUserDataOrCreateNew(id, userId, serverId)
        return AfinityBook(
            id = id,
            name = name,
            originalTitle = originalTitle,
            overview = overview,
            played = userData.played,
            favorite = userData.favorite,
            liked = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = userData.playbackPositionTicks,
            unplayedItemCount = null,
            images = images ?: AfinityImages(),
            chapters = chapters ?: emptyList(),
            providerIds = providerIds,
            externalUrls = externalUrls,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            people = people ?: emptyList(),
            publisher = publisher,
            premiereDate = premiereDate,
            productionYear = productionYear,
            genres = genres ?: emptyList(),
            tags = tags ?: emptyList(),
            communityRating = communityRating,
            dateCreated = dateCreated,
            format = BookFormat.entries.firstOrNull { it.name == format } ?: BookFormat.UNKNOWN,
            parentId = parentId,
            lastPlayedDate = userData.lastPlayedAt?.toLocalDateTime(),
        )
    }

    private suspend fun AfinityAudiobookPartDto.toAfinityAudiobook(
        database: ServerDatabaseDao,
        userId: UUID,
    ): AfinityAudiobook {
        val userData = database.getUserDataOrCreateNew(id, userId, serverId)
        return AfinityAudiobook(
            id = id,
            name = name,
            originalTitle = originalTitle,
            overview = overview,
            played = userData.played,
            favorite = userData.favorite,
            liked = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = userData.playbackPositionTicks,
            unplayedItemCount = null,
            images = images ?: AfinityImages(),
            chapters = chapters ?: emptyList(),
            providerIds = providerIds,
            externalUrls = externalUrls,
            album = album,
            people = people ?: emptyList(),
            publisher = publisher,
            premiereDate = premiereDate,
            productionYear = productionYear,
            genres = genres ?: emptyList(),
            communityRating = communityRating,
            dateCreated = dateCreated,
            parentId = parentId,
            trackNumber = trackNumber,
            discNumber = discNumber,
            container = container,
            lastPlayedDate = userData.lastPlayedAt?.toLocalDateTime(),
            bookPartCount = bookPartCount,
        )
    }

    private suspend fun AfinityShowDto.toAfinityShow(
        database: ServerDatabaseDao,
        userId: UUID,
    ): AfinityShow {
        val userData = database.getUserDataOrCreateNew(id, userId, serverId)
        val seasonDtos = database.getSeasonsForSeries(id)
        val seasons = seasonDtos.map { it.toAfinitySeason(database, userId) }
        val seasonCount = seasons.size
        return AfinityShow(
            id = id,
            name = name,
            originalTitle = originalTitle,
            overview = overview,
            played = userData.played,
            favorite = userData.favorite,
            canPlay = true,
            canDownload = false,
            unplayedItemCount = null,
            sources = emptyList(),
            seasons = seasons,
            genres = genres ?: emptyList(),
            people = people ?: emptyList(),
            runtimeTicks = runtimeTicks,
            communityRating = communityRating,
            officialRating = officialRating,
            status = status,
            productionYear = productionYear,
            premiereDate = premiereDate,
            dateCreated = dateCreated,
            dateLastContentAdded = dateLastContentAdded,
            endDate = endDate,
            trailer = null,
            images = images ?: AfinityImages(),
            seasonCount = seasonCount,
            episodeCount = null,
            tagline = tagline,
            providerIds = providerIds,
            externalUrls = externalUrls,
            liked = false,
            tmdbReviews = this.tmdbReviews ?: emptyList(),
            mdbRatings = this.mdbRatings ?: emptyList(),
        )
    }

    private suspend fun AfinitySeasonDto.toAfinitySeason(
        database: ServerDatabaseDao,
        userId: UUID,
    ): AfinitySeason {
        val userData = database.getUserDataOrCreateNew(id, userId, serverId)
        val episodeDtos = database.getEpisodesForSeason(id)
        val episodes = episodeDtos.map { it.toAfinityEpisode(database, userId) }
        val episodeCount = episodes.size
        return AfinitySeason(
            id = id,
            name = name,
            originalTitle = null,
            overview = overview,
            played = userData.played,
            favorite = userData.favorite,
            canPlay = true,
            canDownload = false,
            unplayedItemCount = null,
            indexNumber = indexNumber,
            sources = emptyList(),
            episodes = episodes,
            seriesId = seriesId,
            seriesName = seriesName,
            images = images ?: AfinityImages(),
            episodeCount = episodeCount,
            productionYear = productionYear,
            premiereDate = premiereDate,
            people = emptyList(),
            providerIds = null,
            externalUrls = null,
            liked = false,
        )
    }

    private suspend fun AfinityEpisodeDto.toAfinityEpisode(
        database: ServerDatabaseDao,
        userId: UUID,
    ): AfinityEpisode {
        val userData = database.getUserDataOrCreateNew(id, userId, serverId)
        val sources = database.getSources(id).map { it.toAfinitySource(database) }
        val trickplayInfos = mutableMapOf<String, List<AfinityTrickplayInfo>>()
        for (source in sources) {
            database.getTrickplayInfo(source.id)?.toAfinityTrickplayInfo()?.let {
                trickplayInfos[source.id] = listOf(it)
            }
        }
        return AfinityEpisode(
            id = id,
            name = name,
            originalTitle = "",
            overview = overview,
            indexNumber = indexNumber,
            indexNumberEnd = indexNumberEnd,
            parentIndexNumber = parentIndexNumber,
            sources = sources,
            played = userData.played,
            favorite = userData.favorite,
            canPlay = true,
            canDownload = false,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = userData.playbackPositionTicks,
            premiereDate = premiereDate,
            seriesName = seriesName,
            seriesId = seriesId,
            seriesLogo = null,
            seriesLogoBlurHash = null,
            seasonId = seasonId,
            communityRating = communityRating,
            people = people ?: emptyList(),
            images = images ?: AfinityImages(),
            chapters = chapters ?: emptyList(),
            trickplayInfo = trickplayInfos,
            providerIds = null,
            externalUrls = null,
            liked = false,
        )
    }

    override suspend fun insertDownload(download: DownloadDto) {
        serverDatabaseDao.insertDownload(download)
    }

    override suspend fun getDownload(downloadId: UUID): DownloadDto? {
        return serverDatabaseDao.getDownload(downloadId)
    }

    override suspend fun updateDownloadProgress(
        downloadId: UUID,
        progress: Float,
        bytesDownloaded: Long,
        totalBytes: Long,
        updatedAt: Long,
    ) {
        serverDatabaseDao.updateDownloadProgress(
            downloadId,
            progress,
            bytesDownloaded,
            totalBytes,
            updatedAt,
        )
    }

    override suspend fun getDownloadByItemId(itemId: UUID): DownloadDto? {
        return serverDatabaseDao.getDownloadByItemId(itemId)
    }

    override suspend fun getDownloadByItemIdScoped(
        itemId: UUID,
        serverId: String,
        userId: UUID,
    ): DownloadDto? {
        return serverDatabaseDao.getDownloadByItemIdScoped(itemId, serverId, userId)
    }

    override fun getAllDownloadsFlow(): Flow<List<DownloadDto>> {
        return serverDatabaseDao.getAllDownloadsFlow()
    }

    override fun getAllDownloadsFlowScoped(
        serverId: String,
        userId: UUID,
    ): Flow<List<DownloadDto>> {
        return serverDatabaseDao.getAllDownloadsFlowScoped(serverId, userId)
    }

    override fun getDownloadsByStatusFlow(statuses: List<DownloadStatus>): Flow<List<DownloadDto>> {
        return serverDatabaseDao.getDownloadsByStatusFlow(statuses)
    }

    override fun getDownloadsByStatusFlowScoped(
        statuses: List<DownloadStatus>,
        serverId: String,
        userId: UUID,
    ): Flow<List<DownloadDto>> {
        return serverDatabaseDao.getDownloadsByStatusFlowScoped(statuses, serverId, userId)
    }

    override suspend fun getTotalBytesForServer(serverId: String): Long {
        return serverDatabaseDao.getTotalBytesForServer(serverId)
    }

    override suspend fun getTotalBytesAllServers(): Long {
        return serverDatabaseDao.getTotalBytesAllServers()
    }

    override suspend fun getTotalBytesPerVolumeForServer(serverId: String): Map<String, Long> {
        return serverDatabaseDao.getTotalBytesPerVolumeForServer(serverId).associate {
            it.storageVolumeId to it.totalBytes
        }
    }

    override suspend fun getTotalBytesPerVolumeAllServers(): Map<String, Long> {
        return serverDatabaseDao.getTotalBytesPerVolume().associate {
            it.storageVolumeId to it.totalBytes
        }
    }

    override suspend fun backfillEmptyServerIds(serverId: String, userId: UUID) {
        serverDatabaseDao.backfillEmptyServerIds(serverId, userId)
    }

    override suspend fun deleteDownload(downloadId: UUID) {
        serverDatabaseDao.deleteDownload(downloadId)
    }

    override suspend fun getSources(itemId: UUID): List<AfinitySourceDto> {
        return serverDatabaseDao.getSources(itemId)
    }

    override suspend fun getItemMetadata(
        itemId: UUID,
        serverId: String,
        userId: String,
    ): ItemMetadataCacheEntity? {
        return itemMetadataCacheDao.getMetadata(itemId, serverId, userId)
    }

    override suspend fun insertItemMetadata(metadata: ItemMetadataCacheEntity) {
        itemMetadataCacheDao.insertOrUpdateMetadata(metadata)
    }

    override suspend fun getWikidataAwards(
        subjectType: String,
        tmdbId: String,
    ): WikidataAwardsCacheEntity? {
        return wikidataAwardsDao.getAwards(subjectType, tmdbId)
    }

    override suspend fun insertWikidataAwards(awards: WikidataAwardsCacheEntity) {
        wikidataAwardsDao.insertOrUpdateAwards(awards)
    }

    override suspend fun getLastFmArtist(artistKey: String): LastFmArtistCacheEntity? {
        return lastFmArtistDao.getArtist(artistKey)
    }

    override suspend fun insertLastFmArtist(artist: LastFmArtistCacheEntity) {
        lastFmArtistDao.insertOrUpdateArtist(artist)
    }

    override suspend fun deleteLastFmArtistsOlderThan(cutoff: Long) {
        lastFmArtistDao.deleteOlderThan(cutoff)
        lastFmArtistDao.deletePopularOlderThan(cutoff)
    }

    override suspend fun getLastFmPopular(
        serverId: String,
        userId: String,
        artistId: String,
    ): LastFmPopularCacheEntity? {
        return lastFmArtistDao.getPopular(serverId, userId, artistId)
    }

    override suspend fun insertLastFmPopular(popular: LastFmPopularCacheEntity) {
        lastFmArtistDao.insertOrUpdatePopular(popular)
    }

    override suspend fun insertMusicTrack(track: AfinityTrack, serverId: String, userId: String) {
        musicTrackDao.insert(track.toMusicTrackEntity(serverId, userId))
    }

    override suspend fun insertMusicAlbum(album: AfinityAlbum, serverId: String, userId: String) {
        musicAlbumDao.insert(album.toMusicAlbumEntity(serverId, userId))
    }

    override suspend fun getMusicTrack(id: UUID, serverId: String, userId: String): AfinityTrack? {
        return musicTrackDao.getById(id.toString(), serverId, userId)?.toAfinityTrack()
    }

    override suspend fun getMusicAlbumTracks(
        albumId: UUID,
        serverId: String,
        userId: String,
    ): List<AfinityTrack> {
        return musicTrackDao.getByAlbum(albumId.toString(), serverId, userId).map {
            it.toAfinityTrack()
        }
    }

    override fun getAllMusicTracksFlow(serverId: String, userId: String): Flow<List<AfinityTrack>> {
        return musicTrackDao.getAllFlow(serverId, userId).map { list ->
            list.map { it.toAfinityTrack() }
        }
    }

    override fun getAllMusicAlbumsFlow(serverId: String, userId: String): Flow<List<AfinityAlbum>> {
        return musicAlbumDao.getAllFlow(serverId, userId).map { list ->
            list.map { it.toAfinityAlbum() }
        }
    }

    override suspend fun deleteMusicTrack(id: UUID, serverId: String, userId: String) {
        musicTrackDao.delete(id.toString(), serverId, userId)
    }

    override suspend fun deleteMusicAlbum(id: UUID, serverId: String, userId: String) {
        musicAlbumDao.delete(id.toString(), serverId, userId)
    }

    override suspend fun updateMusicTrackLocalFilePath(
        id: UUID,
        serverId: String,
        userId: String,
        path: String,
    ) {
        musicTrackDao.updateLocalFilePath(id.toString(), serverId, userId, path)
    }

    override suspend fun updateMusicTrackLocalImagePath(
        id: UUID,
        serverId: String,
        userId: String,
        path: String,
    ) {
        musicTrackDao.updateLocalImagePath(id.toString(), serverId, userId, path)
    }

    override suspend fun updateMusicAlbumLocalImagePath(
        id: UUID,
        serverId: String,
        userId: String,
        path: String,
    ) {
        musicAlbumDao.updateLocalImagePath(id.toString(), serverId, userId, path)
    }

    override suspend fun updateMusicAlbumDetails(
        id: UUID,
        serverId: String,
        userId: String,
        overview: String?,
        externalUrls: List<AfinityExternalUrl>?,
    ) {
        musicAlbumDao.updateDetails(id.toString(), serverId, userId, overview, externalUrls)
    }

    override suspend fun updateMusicAlbumArtistImagePath(
        id: UUID,
        serverId: String,
        userId: String,
        path: String,
    ) {
        musicAlbumDao.updateArtistImagePath(id.toString(), serverId, userId, path)
    }

    override suspend fun getMusicArtistImagePath(
        artistId: UUID,
        serverId: String,
        userId: String,
    ): String? = musicAlbumDao.getArtistImagePath(artistId.toString(), serverId, userId)

    override suspend fun getMusicAlbum(id: UUID, serverId: String, userId: String): AfinityAlbum? =
        musicAlbumDao.getById(id.toString(), serverId, userId)?.toAfinityAlbum()

    override suspend fun insertMusicLyrics(
        trackId: UUID,
        serverId: String,
        userId: String,
        lyricsJson: String,
    ) {
        musicLyricsDao.insert(MusicLyricsEntity(trackId.toString(), serverId, userId, lyricsJson))
    }

    override suspend fun getMusicLyricsJson(
        trackId: UUID,
        serverId: String,
        userId: String,
    ): String? {
        return musicLyricsDao.get(trackId.toString(), serverId, userId)?.lyricsJson
    }

    override suspend fun deleteMusicLyrics(trackId: UUID, serverId: String, userId: String) {
        musicLyricsDao.delete(trackId.toString(), serverId, userId)
    }

    override fun getAllMusicTracksFlowByUser(userId: UUID): Flow<List<AfinityTrack>> =
        musicTrackDao.getAllFlowByUser(userId.toString()).map { list ->
            list.map { it.toAfinityTrack() }
        }

    override fun getAllMusicAlbumsFlowByUser(userId: UUID): Flow<List<AfinityAlbum>> =
        musicAlbumDao.getAllFlowByUser(userId.toString()).map { list ->
            list.map { it.toAfinityAlbum() }
        }

    override suspend fun getAllMusicTracksByUser(userId: UUID): List<AfinityTrack> =
        musicTrackDao.getAllByUser(userId.toString()).map { it.toAfinityTrack() }

    override suspend fun getAllMusicAlbumsByUser(userId: UUID): List<AfinityAlbum> =
        musicAlbumDao.getAllByUser(userId.toString()).map { it.toAfinityAlbum() }

    override suspend fun getCompletedAudioDownloadsByAlbum(
        albumSeriesId: String,
        serverId: String,
        userId: UUID,
    ): List<DownloadDto> =
        serverDatabaseDao.getCompletedAudioDownloadsByAlbum(albumSeriesId, serverId, userId)
}
