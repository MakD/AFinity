package com.makd.afinity.data.books

import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.MediaChangeSource
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.di.ApplicationScope
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class BookProgressReporter
@Inject
constructor(
    private val playbackRepository: PlaybackRepository,
    private val mediaRepository: MediaRepository,
    private val mediaChangeManager: MediaChangeManager,
    private val mediaRefreshBus: MediaRefreshBus,
    private val itemStore: ItemStore,
    private val userDataRepository: UserDataRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val sessionId = UUID.randomUUID().toString()
    private var itemId: UUID? = null
    private var runtimeTicks: Long = 0L
    private var started = false
    private var lastTicks: Long = 0L

    suspend fun start(itemId: UUID, runtimeTicks: Long, positionTicks: Long) {
        this.itemId = itemId
        this.runtimeTicks = runtimeTicks
        lastTicks = positionTicks
        started =
            try {
                playbackRepository.reportPlaybackStart(
                    itemId = itemId,
                    sessionId = sessionId,
                    mediaSourceId = mediaSourceId(itemId),
                    canSeek = true,
                )
                playbackRepository.reportPlaybackProgress(
                    itemId = itemId,
                    sessionId = sessionId,
                    positionTicks = positionTicks,
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Book start report failed for $itemId")
                false
            }
    }

    suspend fun progress(positionTicks: Long) {
        val id = itemId ?: return
        if (!started) return
        lastTicks = positionTicks
        itemStore.applyPatch(id, UserDataPatch(playbackPositionTicks = positionTicks))
        try {
            playbackRepository.reportPlaybackProgress(
                itemId = id,
                sessionId = sessionId,
                positionTicks = positionTicks,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Book progress report failed for $id")
        }
    }

    fun stop(positionTicks: Long = lastTicks, finished: Boolean = false) {
        val id = itemId ?: return
        if (!started) return
        started = false
        val runtime = runtimeTicks
        val finishedPatch = UserDataPatch(played = true, playbackPositionTicks = 0L)
        if (finished) itemStore.applyPatch(id, finishedPatch)
        applicationScope.launch {
            try {
                playbackRepository.reportPlaybackStop(
                    itemId = id,
                    sessionId = sessionId,
                    positionTicks = positionTicks,
                    mediaSourceId = mediaSourceId(id),
                    runtimeTicks = runtime,
                    isBook = true,
                )
                val markedRead = finished && userDataRepository.markWatched(id)
                val refreshed = mediaRepository.refreshItemUserData(id, FieldSets.BOOK_GRID)
                if (refreshed != null) {
                    mediaChangeManager.publishKnownChange(
                        updatedItem = refreshed,
                        source = MediaChangeSource.PLAYBACK,
                        patch = if (markedRead) finishedPatch else null,
                    )
                    mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Book stop report failed for $id")
            }
        }
    }

    private fun mediaSourceId(itemId: UUID): String = itemId.toString().replace("-", "")
}
