package com.makd.afinity.data.repository.impl

import com.makd.afinity.data.database.dao.EpisodeDao
import com.makd.afinity.data.database.dao.MediaStreamDao
import com.makd.afinity.data.database.dao.MovieDao
import com.makd.afinity.data.database.dao.SeasonDao
import com.makd.afinity.data.database.dao.ServerAddressDao
import com.makd.afinity.data.database.dao.ServerDao
import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.database.dao.ShowDao
import com.makd.afinity.data.database.dao.SourceDao
import com.makd.afinity.data.database.dao.UserDao
import com.makd.afinity.data.database.dao.UserDataDao
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.database.entities.AfinityEpisodeDto
import com.makd.afinity.data.database.entities.AfinityMovieDto
import com.makd.afinity.data.database.entities.AfinitySeasonDto
import com.makd.afinity.data.database.entities.AfinityShowDto
import com.makd.afinity.data.database.entities.AfinitySourceDto
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.database.entities.toAfinityEpisodeDto
import com.makd.afinity.data.database.entities.toAfinityMediaStreamDto
import com.makd.afinity.data.database.entities.toAfinityMovieDto
import com.makd.afinity.data.database.entities.toAfinitySeasonDto
import com.makd.afinity.data.database.entities.toAfinitySegmentsDto
import com.makd.afinity.data.database.entities.toAfinityShowDto
import com.makd.afinity.data.database.entities.toAfinitySourceDto
import com.makd.afinity.data.database.entities.toAfinityTrickplayInfoDto
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinityTrickplayInfo
import com.makd.afinity.data.models.media.toAfinityMediaStream
import com.makd.afinity.data.models.media.toAfinitySegment
import com.makd.afinity.data.models.media.toAfinitySource
import com.makd.afinity.data.models.media.toAfinityTrickplayInfo
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.models.server.ServerWithAddresses
import com.makd.afinity.data.models.server.ServerWithAddressesAndUsers
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.DatabaseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DatabaseRepositoryImpl @Inject constructor(
    //private val sessionManager: SessionManager,
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
    private val serverDatabaseDao: ServerDatabaseDao
) : DatabaseRepository {

    private val sessionManager: SessionManager
        get() = sessionManagerProvider.get()

    override suspend fun insertServer(server: Server) {
        serverDao.insertServer(server)
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

    override suspend fun getServerWithAddresses(serverId: String): ServerWithAddresses? {
        return serverDao.getServerWithAddresses(serverId)
    }

    override suspend fun getServerWithAddressesAndUsers(serverId: String): ServerWithAddressesAndUsers? {
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

    override suspend fun deleteMovie(movieId: UUID) {
        movieDao.deleteMovieById(movieId)
    }

    override suspend fun getMovie(movieId: UUID, userId: UUID): AfinityMovie? {
        val serverId = sessionManager.currentSession.value?.serverId ?: return null
        val movieDto = movieDao.getMovie(movieId, serverId) ?: return null
        return movieDto.toAfinityMovie(serverDatabaseDao, userId)
    }

    override suspend fun getAllMovies(userId: UUID, serverId: String?): List<AfinityMovie> {
        val actualServerId = serverId ?: sessionManager.currentSession.value?.serverId ?: return emptyList()
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
        return sessionManager.currentSession
            .filterNotNull()
            .flatMapLatest { session ->
                movieDao.getMoviesFlow(session.serverId).map { movieDtos ->
                    movieDtos.map { it.toAfinityMovie(serverDatabaseDao, userId) }
                }
            }
    }

    override suspend fun insertShow(show: AfinityShow, serverId: String?) {
        val actualServerId = serverId ?: sessionManager.currentSession.value?.serverId ?: return
        showDao.insertShow(show.toAfinityShowDto(actualServerId))
    }

    override suspend fun updateShow(show: AfinityShow) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        showDao.updateShow(show.toAfinityShowDto(serverId))
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
        val actualServerId = serverId ?: sessionManager.currentSession.value?.serverId ?: return emptyList()
        return showDao.getShows(actualServerId).map {
            it.toAfinityShow(serverDatabaseDao, userId)
        }
    }

    override suspend fun searchShows(query: String, userId: UUID): List<AfinityShow> {
        val serverId = sessionManager.currentSession.value?.serverId ?: return emptyList()
        return showDao.searchShows(query, serverId).map {
            it.toAfinityShow(serverDatabaseDao, userId)
        }
    }

    override fun getShowsFlow(userId: UUID): Flow<List<AfinityShow>> {
        return sessionManager.currentSession
            .filterNotNull()
            .flatMapLatest { session ->
                showDao.getShowsFlow(session.serverId).map { showDtos ->
                    showDtos.map { it.toAfinityShow(serverDatabaseDao, userId) }
                }
            }
    }

    override suspend fun insertSeason(season: AfinitySeason) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        seasonDao.insertSeason(season.toAfinitySeasonDto(serverId))
    }

    override suspend fun updateSeason(season: AfinitySeason) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        seasonDao.updateSeason(season.toAfinitySeasonDto(serverId))
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
        return sessionManager.currentSession
            .filterNotNull()
            .flatMapLatest { session ->
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
        return sourceDao.getSourcesForItem(itemId).map {
            it.toAfinitySource(serverDatabaseDao)
        }
    }

    override suspend fun insertMediaStream(stream: AfinityMediaStream, sourceId: String) {
        mediaStreamDao.insertMediaStream(
            stream.toAfinityMediaStreamDto(UUID.randomUUID(), sourceId, stream.path ?: "")
        )
    }

    override suspend fun updateMediaStream(stream: AfinityMediaStream) {
    }

    override suspend fun deleteMediaStream(streamId: UUID) {
        mediaStreamDao.deleteMediaStreamById(streamId)
    }

    override suspend fun getMediaStreamsForSource(sourceId: String): List<AfinityMediaStream> {
        return mediaStreamDao.getMediaStreamsBySourceId(sourceId).map {
            it.toAfinityMediaStream()
        }
    }

    override suspend fun insertTrickplayInfo(trickplayInfo: AfinityTrickplayInfo, sourceId: String) {
        serverDatabaseDao.insertTrickplayInfo(
            trickplayInfo.toAfinityTrickplayInfoDto(sourceId)
        )
    }

    override suspend fun updateTrickplayInfo(trickplayInfo: AfinityTrickplayInfo) {
    }

    override suspend fun deleteTrickplayInfo(sourceId: String) {
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

    override suspend fun deleteSegment(itemId: UUID, segmentType: AfinitySegmentType) {
    }

    override suspend fun getSegmentsForItem(itemId: UUID): List<AfinitySegment> {
        return serverDatabaseDao.getSegmentsForItem(itemId).map {
            it.toAfinitySegment()
        }
    }

    override suspend fun insertUserData(userData: AfinityUserDataDto) {
        userDataDao.insertUserData(userData)
    }

    override suspend fun updateUserData(userData: AfinityUserDataDto) {
        userDataDao.updateUserData(userData)
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

    override suspend fun markUserDataSynced(userId: UUID, itemId: UUID) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
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
        return sessionManager.currentSession
            .filterNotNull()
            .flatMapLatest { session ->
                userDataDao.getFavoriteItemsFlow(userId, session.serverId).map { userDataList ->
                    val movieIds = userDataList.map { it.itemId }
                    movieIds.mapNotNull { movieId ->
                        getMovie(movieId, userId)
                    }
                }
            }
    }

    override fun getFavoriteShowsFlow(userId: UUID): Flow<List<AfinityShow>> {
        return sessionManager.currentSession
            .filterNotNull()
            .flatMapLatest { session ->
                userDataDao.getFavoriteItemsFlow(userId, session.serverId).map { userDataList ->
                    val showIds = userDataList.map { it.itemId }
                    showIds.mapNotNull { showId ->
                        getShow(showId, userId)
                    }
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

    override suspend fun getContinueWatchingEpisodes(userId: UUID, limit: Int): List<AfinityEpisode> {
        return serverDatabaseDao.getContinueWatchingEpisodes(userId, limit).map {
            it.toAfinityEpisode(serverDatabaseDao, userId)
        }
    }

    override fun getContinueWatchingFlow(userId: UUID): Flow<List<AfinityItem>> {
        return sessionManager.currentSession
            .filterNotNull()
            .flatMapLatest { session ->
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
        movieDao.deleteMoviesByServerId(serverId)
        showDao.deleteShowsByServerId(serverId)
        episodeDao.deleteEpisodesByServerId(serverId)
    }

    override suspend fun clearUserData(userId: UUID) {
        val serverId = sessionManager.currentSession.value?.serverId ?: return
        userDataDao.deleteUserDataByUserId(userId, serverId)
    }

    override suspend fun getDatabaseSize(): Long {
        return 0L
    }

    private suspend fun AfinityMovieDto.toAfinityMovie(
        database: ServerDatabaseDao,
        userId: UUID
    ): AfinityMovie {
        val userData = database.getUserDataOrCreateNew(id, userId, serverId)
        val sources = database.getSources(id).map { it.toAfinitySource(database) }
        val trickplayInfos = mutableMapOf<String, AfinityTrickplayInfo>()
        for (source in sources) {
            database.getTrickplayInfo(source.id)?.toAfinityTrickplayInfo()?.let {
                trickplayInfos[source.id] = it
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
            providerIds = null,
            externalUrls = null,
            liked = false,
            )
    }

    private suspend fun AfinityShowDto.toAfinityShow(
        database: ServerDatabaseDao,
        userId: UUID
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
            tagline = null,
            providerIds = null,
            externalUrls = null,
            liked = false,
            )
    }

    private suspend fun AfinitySeasonDto.toAfinitySeason(
        database: ServerDatabaseDao,
        userId: UUID
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
            productionYear = null,
            premiereDate = null,
            people = emptyList(),
            providerIds = null,
            externalUrls = null,
            liked = false,
        )
    }

    private suspend fun AfinityEpisodeDto.toAfinityEpisode(
        database: ServerDatabaseDao,
        userId: UUID
    ): AfinityEpisode {
        val userData = database.getUserDataOrCreateNew(id, userId, serverId)
        val sources = database.getSources(id).map { it.toAfinitySource(database) }
        val trickplayInfos = mutableMapOf<String, AfinityTrickplayInfo>()
        for (source in sources) {
            database.getTrickplayInfo(source.id)?.toAfinityTrickplayInfo()?.let {
                trickplayInfos[source.id] = it
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
            people = emptyList(),
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

    override suspend fun getDownloadByItemId(itemId: UUID): DownloadDto? {
        return serverDatabaseDao.getDownloadByItemId(itemId)
    }

    override fun getAllDownloadsFlow(): Flow<List<DownloadDto>> {
        return serverDatabaseDao.getAllDownloadsFlow()
    }

    override fun getDownloadsByStatusFlow(statuses: List<DownloadStatus>): Flow<List<DownloadDto>> {
        return serverDatabaseDao.getDownloadsByStatusFlow(statuses)
    }

    override suspend fun deleteDownload(downloadId: UUID) {
        serverDatabaseDao.deleteDownload(downloadId)
    }

    override suspend fun getSources(itemId: UUID): List<AfinitySourceDto> {
        return serverDatabaseDao.getSources(itemId)
    }
}