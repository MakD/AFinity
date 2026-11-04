package com.makd.afinity.player.audiobookshelf

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.data.repository.AudiobookshelfRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class AbsServerUrlUnavailableException :
    IllegalStateException("Audiobookshelf server URL unavailable")

@Singleton
class AbsPlaybackStarter
@Inject
constructor(
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val playbackManager: AudiobookshelfPlaybackManager,
    private val audiobookshelfPlayer: AudiobookshelfPlayer,
) {

    @OptIn(UnstableApi::class)
    suspend fun start(
        itemId: String,
        episodeId: String?,
        startPosition: Double? = null,
        episodeSort: String? = null,
        includePlayed: Boolean = false,
    ): Result<Unit> {
        preloadMetadata(itemId)
        val session =
            audiobookshelfRepository.startPlaybackSession(itemId, episodeId).getOrElse {
                return Result.failure(it)
            }
        val serverUrl =
            audiobookshelfRepository.currentConfig.value?.serverUrl
                ?: return Result.failure(AbsServerUrlUnavailableException())
        audiobookshelfPlayer.loadSession(
            session,
            serverUrl,
            startPosition,
            episodeSort,
            includePlayed,
        )
        return Result.success(Unit)
    }

    @OptIn(UnstableApi::class)
    suspend fun prepareForResumption(
        itemId: String,
        episodeId: String?,
    ): AudiobookshelfPlayer.PreparedSession? {
        val config =
            withTimeoutOrNull(CONFIG_WAIT) {
                audiobookshelfRepository.currentConfig.filterNotNull().first()
            }
        if (config == null) {
            Timber.w("ABS resumption: no Audiobookshelf config, skipping")
            return null
        }
        preloadMetadata(itemId)
        val session =
            audiobookshelfRepository.startPlaybackSession(itemId, episodeId).getOrElse {
                Timber.w(it, "ABS resumption: failed to start a playback session")
                return null
            }
        val prepared = audiobookshelfPlayer.prepareSession(session, config.serverUrl) ?: return null
        audiobookshelfPlayer.connectController()
        return prepared
    }

    private suspend fun preloadMetadata(itemId: String) {
        val current = playbackManager.playbackState.value
        if (current.displayTitle.isNotEmpty() && current.itemId == itemId) return
        val (title, author, coverUrl) =
            audiobookshelfRepository.getCachedItemMetadata(itemId) ?: return
        playbackManager.preloadDisplayMetadata(itemId, title, author, coverUrl)
    }

    private companion object {
        val CONFIG_WAIT = 10.seconds
    }
}
