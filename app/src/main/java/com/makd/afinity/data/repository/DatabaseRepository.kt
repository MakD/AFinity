package com.makd.afinity.data.repository

import com.makd.afinity.data.database.entities.AfinitySourceDto
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinityTrickplayInfo
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.models.server.ServerWithAddresses
import com.makd.afinity.data.models.server.ServerWithAddressesAndUsers
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.models.user.User
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface DatabaseRepository {

    suspend fun insertServer(server: Server)

    suspend fun updateServer(server: Server)

    suspend fun deleteServer(serverId: String)

    suspend fun getServer(serverId: String): Server?

    suspend fun getAllServers(): List<Server>

    fun getAllServersFlow(): Flow<List<Server>>

    suspend fun insertServerAddress(serverAddress: ServerAddress)

    suspend fun updateServerAddress(serverAddress: ServerAddress)

    suspend fun deleteServerAddress(addressId: UUID)

    suspend fun getServerAddresses(serverId: String): List<ServerAddress>

    suspend fun getServerWithAddresses(serverId: String): ServerWithAddresses?

    suspend fun getServerWithAddressesAndUsers(serverId: String): ServerWithAddressesAndUsers?

    suspend fun insertUser(user: User)

    suspend fun updateUser(user: User)

    suspend fun deleteUser(userId: UUID)

    suspend fun getUser(userId: UUID): User?

    suspend fun getUsersForServer(serverId: String): List<User>

    suspend fun getAllUsers(): List<User>

    suspend fun getCurrentUser(): User?

    suspend fun insertMovie(movie: AfinityMovie, serverId: String? = null)

    suspend fun updateMovie(movie: AfinityMovie)

    suspend fun deleteMovie(movieId: UUID)

    suspend fun getMovie(movieId: UUID, userId: UUID): AfinityMovie?

    suspend fun getAllMovies(userId: UUID, serverId: String? = null): List<AfinityMovie>

    suspend fun searchMovies(query: String, userId: UUID): List<AfinityMovie>

    fun getMoviesFlow(userId: UUID): Flow<List<AfinityMovie>>

    suspend fun insertShow(show: AfinityShow, serverId: String? = null)

    suspend fun updateShow(show: AfinityShow)

    suspend fun deleteShow(showId: UUID)

    suspend fun getShow(showId: UUID, userId: UUID): AfinityShow?

    suspend fun getAllShows(userId: UUID, serverId: String? = null): List<AfinityShow>

    suspend fun searchShows(query: String, userId: UUID): List<AfinityShow>

    fun getShowsFlow(userId: UUID): Flow<List<AfinityShow>>

    suspend fun insertSeason(season: AfinitySeason, serverId: String? = null)

    suspend fun updateSeason(season: AfinitySeason)

    suspend fun deleteSeason(seasonId: UUID)

    suspend fun getSeason(seasonId: UUID, userId: UUID): AfinitySeason?

    suspend fun getSeasonsForShow(showId: UUID, userId: UUID): List<AfinitySeason>

    suspend fun insertEpisode(episode: AfinityEpisode, serverId: String? = null)

    suspend fun updateEpisode(episode: AfinityEpisode)

    suspend fun deleteEpisode(episodeId: UUID)

    suspend fun getEpisode(episodeId: UUID, userId: UUID): AfinityEpisode?

    suspend fun getEpisodesForSeason(seasonId: UUID, userId: UUID): List<AfinityEpisode>

    suspend fun getEpisodesForShow(showId: UUID, userId: UUID): List<AfinityEpisode>

    suspend fun getNextEpisodesToWatch(userId: UUID, limit: Int = 16): List<AfinityEpisode>

    suspend fun searchEpisodes(query: String, userId: UUID): List<AfinityEpisode>

    fun getEpisodesFlow(seasonId: UUID, userId: UUID): Flow<List<AfinityEpisode>>

    suspend fun insertSource(source: AfinitySource, itemId: UUID)

    suspend fun updateSource(source: AfinitySource)

    suspend fun deleteSource(sourceId: String)

    suspend fun getSource(sourceId: String): AfinitySource?

    suspend fun getSourcesForItem(itemId: UUID): List<AfinitySource>

    suspend fun insertMediaStream(stream: AfinityMediaStream, sourceId: String)

    suspend fun updateMediaStream(stream: AfinityMediaStream)

    suspend fun deleteMediaStream(streamId: UUID)

    suspend fun getMediaStreamsForSource(sourceId: String): List<AfinityMediaStream>

    suspend fun insertTrickplayInfo(trickplayInfo: AfinityTrickplayInfo, sourceId: String)

    suspend fun updateTrickplayInfo(trickplayInfo: AfinityTrickplayInfo)

    suspend fun deleteTrickplayInfo(sourceId: String)

    suspend fun getTrickplayInfo(sourceId: String): AfinityTrickplayInfo?

    suspend fun insertSegment(segment: AfinitySegment, itemId: UUID)

    suspend fun updateSegment(segment: AfinitySegment, itemId: UUID)

    suspend fun deleteSegment(itemId: UUID, segmentType: AfinitySegmentType)

    suspend fun getSegmentsForItem(itemId: UUID): List<AfinitySegment>

    suspend fun insertUserData(userData: AfinityUserDataDto)

    suspend fun updateUserData(userData: AfinityUserDataDto)

    suspend fun deleteUserData(userId: UUID, itemId: UUID)

    suspend fun getUserData(userId: UUID, itemId: UUID): AfinityUserDataDto?

    suspend fun getUserDataOrCreateNew(itemId: UUID, userId: UUID): AfinityUserDataDto

    suspend fun getAllUserDataToSync(userId: UUID): List<AfinityUserDataDto>

    suspend fun getAllUserDataToSync(userId: UUID, serverId: String): List<AfinityUserDataDto>

    suspend fun markUserDataSynced(userId: UUID, itemId: UUID)

    suspend fun markUserDataSynced(userId: UUID, itemId: UUID, serverId: String)

    suspend fun getFavoriteMovies(userId: UUID): List<AfinityMovie>

    suspend fun getFavoriteShows(userId: UUID): List<AfinityShow>

    suspend fun getFavoriteEpisodes(userId: UUID): List<AfinityEpisode>

    fun getFavoriteMoviesFlow(userId: UUID): Flow<List<AfinityMovie>>

    fun getFavoriteShowsFlow(userId: UUID): Flow<List<AfinityShow>>

    suspend fun getRecentlyWatchedItems(userId: UUID, limit: Int = 20): List<AfinityItem>

    suspend fun getContinueWatchingMovies(userId: UUID, limit: Int = 16): List<AfinityMovie>

    suspend fun getContinueWatchingEpisodes(userId: UUID, limit: Int = 16): List<AfinityEpisode>

    fun getContinueWatchingFlow(userId: UUID): Flow<List<AfinityItem>>

    suspend fun searchAllItems(query: String, userId: UUID): List<AfinityItem>

    suspend fun clearAllData()

    suspend fun clearServerData(serverId: String)

    suspend fun clearUserData(userId: UUID)

    suspend fun getDatabaseSize(): Long

    suspend fun insertDownload(download: DownloadDto)

    suspend fun getDownload(downloadId: UUID): DownloadDto?

    suspend fun getDownloadByItemId(itemId: UUID): DownloadDto?

    suspend fun getDownloadByItemIdScoped(
        itemId: UUID,
        serverId: String,
        userId: UUID,
    ): DownloadDto?

    fun getAllDownloadsFlow(): Flow<List<DownloadDto>>

    fun getAllDownloadsFlowScoped(serverId: String, userId: UUID): Flow<List<DownloadDto>>

    fun getDownloadsByStatusFlow(statuses: List<DownloadStatus>): Flow<List<DownloadDto>>

    fun getDownloadsByStatusFlowScoped(
        statuses: List<DownloadStatus>,
        serverId: String,
        userId: UUID,
    ): Flow<List<DownloadDto>>

    suspend fun getTotalBytesForServer(serverId: String): Long

    suspend fun getTotalBytesAllServers(): Long

    suspend fun backfillEmptyServerIds(serverId: String, userId: UUID)

    suspend fun deleteDownload(downloadId: UUID)

    suspend fun getSources(itemId: UUID): List<AfinitySourceDto>
}
