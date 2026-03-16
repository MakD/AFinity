package com.makd.afinity.data.repository.impl

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import com.makd.afinity.data.repository.server.ServerRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinRepositoryImpl
@Inject
constructor(
    private val serverRepository: ServerRepository,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : JellyfinRepository {

    override fun getBaseUrl(): String {
        return serverRepository.getBaseUrl()
    }

    override suspend fun setBaseUrl(baseUrl: String) {
        serverRepository.setBaseUrl(baseUrl)
    }

    override suspend fun discoverServersFlow(): Flow<List<Server>> {
        return try {
            serverRepository.discoverServersFlow()
        } catch (e: Exception) {
            Timber.e(e, "Failed to discover servers flow")
            flowOf(emptyList())
        }
    }

    override suspend fun validateServer(
        serverUrl: String
    ): JellyfinServerRepository.ServerConnectionResult {
        return try {
            serverRepository.testServerConnection(serverUrl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate server: $serverUrl")
            JellyfinServerRepository.ServerConnectionResult.Error(
                "Failed to validate server: ${e.message ?: "Unknown error"}"
            )
        }
    }

    override suspend fun refreshServerInfo() {
        serverRepository.refreshServerInfo()
    }

    override suspend fun logout() {
        try {
            authRepository.logout()
        } catch (e: Exception) {
            Timber.e(e, "Failed to logout")
        }
    }

    override suspend fun getCurrentUser(): User? {
        return try {
            authRepository.getCurrentUser()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user")
            null
        }
    }

    override suspend fun getPublicUsers(serverUrl: String): List<User> {
        return try {
            authRepository.getPublicUsers(serverUrl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get public users")
            emptyList()
        }
    }

    override suspend fun getUserProfileImageUrl(): String? {
        return try {
            val currentUser = authRepository.currentUser.value
            val serverUrl = getBaseUrl()

            currentUser?.primaryImageTag?.let { imageTag ->
                "$serverUrl/Users/${currentUser.id}/Images/Primary?tag=$imageTag"
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user profile image URL")
            null
        }
    }

    override suspend fun getSeriesNextEpisode(seriesId: UUID): AfinityEpisode? {
        return try {
            mediaRepository.getNextUp(seriesId, limit = 1).firstOrNull()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get next episode for series: $seriesId")
            null
        }
    }

    override suspend fun reportPlaybackStart(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String?,
    ) {
        try {
            val actualSessionId = sessionId ?: playbackRepository.getActiveSession() ?: return
            playbackRepository.reportPlaybackStart(
                itemId = itemId,
                sessionId = actualSessionId,
                mediaSourceId = itemId.toString(),
                playMethod = "DirectPlay",
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback start: $itemId")
        }
    }

    override suspend fun reportPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
        sessionId: String?,
    ) {
        try {
            val actualSessionId = sessionId ?: playbackRepository.getActiveSession() ?: return
            playbackRepository.reportPlaybackProgress(
                itemId = itemId,
                sessionId = actualSessionId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                playMethod = "DirectPlay",
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback progress: $itemId")
        }
    }

    override suspend fun reportPlaybackStopped(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String?,
    ) {
        try {
            val actualSessionId = sessionId ?: playbackRepository.getActiveSession() ?: return
            playbackRepository.reportPlaybackStop(
                itemId = itemId,
                sessionId = actualSessionId,
                positionTicks = positionTicks,
                mediaSourceId = itemId.toString(),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback stopped: $itemId")
        }
    }

    override suspend fun getStreamUrl(
        itemId: UUID,
        mediaSourceId: String,
        maxBitrate: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        videoStreamIndex: Int?,
    ): String {
        return try {
            serverRepository.buildStreamUrl(
                itemId.toString(),
                mediaSourceId,
                maxBitrate,
                audioStreamIndex,
                subtitleStreamIndex,
                videoStreamIndex,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get stream URL for item: $itemId")
            ""
        }
    }

    override suspend fun getImageUrl(
        itemId: UUID,
        imageType: String,
        imageIndex: Int,
        tag: String?,
        maxWidth: Int?,
        maxHeight: Int?,
        quality: Int?,
    ): String {
        return try {
            serverRepository.buildImageUrl(
                itemId = itemId.toString(),
                imageType = imageType,
                imageIndex = imageIndex,
                tag = tag,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                quality = quality,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get image URL for item: $itemId")
            ""
        }
    }

    override suspend fun getEpisodeToPlay(seriesId: UUID): AfinityEpisode? {
        return try {
            Timber.d("Getting episode to play for series: $seriesId")
            try {
                val nextUpEpisodes =
                    mediaRepository.getNextUp(
                        seriesId = seriesId,
                        limit = 1,
                        fields = FieldSets.PLAYABLE_EPISODE,
                    )
                if (nextUpEpisodes.isNotEmpty()) {
                    Timber.d("Found NextUp episode: ${nextUpEpisodes.first().name}")
                    return nextUpEpisodes.first()
                }
            } catch (e: Exception) {
                Timber.w(e, "NextUp API failed")
            }
            Timber.d("Fallback to manual logic")
            val seasons = mediaRepository.getSeasons(seriesId)
            if (seasons.isEmpty()) return null

            val sortedSeasons = seasons.sortedBy { it.indexNumber }
            val episodesBySeason = coroutineScope {
                sortedSeasons
                    .map { season ->
                        season to
                            async {
                                mediaRepository.getEpisodes(
                                        season.id,
                                        seriesId,
                                        fields = FieldSets.PLAYABLE_EPISODE,
                                    )
                                    .sortedBy { it.indexNumber }
                            }
                    }
                    .map { (season, deferred) -> season to deferred.await() }
            }

            var firstEpisodeOfSeries: AfinityEpisode? = null
            for ((_, episodes) in episodesBySeason) {
                if (episodes.isEmpty()) continue
                if (firstEpisodeOfSeries == null) firstEpisodeOfSeries = episodes.firstOrNull()
                val nextEpisode = episodes.firstOrNull { !it.played }
                if (nextEpisode != null) return nextEpisode
            }
            return firstEpisodeOfSeries
        } catch (e: Exception) {
            Timber.e(e, "Failed to determine episode to play for series: $seriesId")
            null
        }
    }

    override suspend fun getEpisodeToPlayForSeason(
        seasonId: UUID,
        seriesId: UUID,
    ): AfinityEpisode? {
        return try {
            Timber.d("Getting episode to play for season: $seasonId")
            val episodes = mediaRepository.getEpisodes(seasonId, seriesId, fields = FieldSets.PLAYABLE_EPISODE)
            if (episodes.isEmpty()) return null

            val sortedEpisodes = episodes.sortedBy { it.indexNumber }
            sortedEpisodes.firstOrNull { !it.played } ?: sortedEpisodes.firstOrNull()
        } catch (e: Exception) {
            Timber.e(e, "Failed to determine episode to play for season: $seasonId")
            null
        }
    }
}