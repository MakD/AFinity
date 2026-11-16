package com.makd.afinity.player.audiobook

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.data.audiobooks.AudiobookDownloadStore
import com.makd.afinity.data.audiobooks.JellyfinAudiobookLoader
import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.timeline
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.player.audiobookshelf.JellyfinAudiobookSession
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import timber.log.Timber

private const val TICKS_PER_SECOND = 10_000_000.0
private val MP4_CONTAINERS = setOf("mp4", "m4a", "m4b", "mov")

class AudiobookNotFoundException : IllegalStateException("Audiobook not found")

@Singleton
class JellyfinAudiobookStarter
@Inject
constructor(
    private val loader: JellyfinAudiobookLoader,
    private val downloadStore: AudiobookDownloadStore,
    private val mediaRepository: MediaRepository,
    private val audiobookshelfPlayer: AudiobookshelfPlayer,
) {

    @OptIn(UnstableApi::class)
    suspend fun start(itemId: UUID, startPosition: Double?): Result<Unit> {
        val book =
            loader.loadBook(itemId).getOrElse {
                return Result.failure(it)
            } ?: return Result.failure(AudiobookNotFoundException())
        val session = sessionFor(book, itemId)
        audiobookshelfPlayer.loadJellyfinSession(session, startPosition ?: resumePosition(book))
        return Result.success(Unit)
    }

    @OptIn(UnstableApi::class)
    suspend fun prepareForResumption(itemId: String): AudiobookshelfPlayer.PreparedSession? {
        val id =
            try {
                UUID.fromString(itemId)
            } catch (_: IllegalArgumentException) {
                return null
            }
        val book =
            try {
                loader.loadBook(id).getOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Audiobook resumption: failed to load $itemId")
                null
            } ?: return null
        val prepared =
            audiobookshelfPlayer.prepareJellyfinSession(sessionFor(book, id), resumePosition(book))
        audiobookshelfPlayer.connectController()
        return prepared
    }

    private fun mimeTypeFor(container: String?): String? {
        val parts = container?.lowercase()?.split(',')?.map { it.trim() } ?: return null
        return when {
            parts.any { it in MP4_CONTAINERS } -> "audio/mp4"
            "mp3" in parts -> "audio/mpeg"
            "flac" in parts -> "audio/flac"
            parts.any { it == "ogg" || it == "opus" } -> "audio/ogg"
            "wav" in parts -> "audio/wav"
            else -> null
        }
    }

    private fun resumePosition(book: AfinityAudiobook): Double =
        if (book.played) 0.0 else book.playbackPositionTicks / TICKS_PER_SECOND

    private suspend fun sessionFor(
        book: AfinityAudiobook,
        routeId: UUID,
    ): JellyfinAudiobookSession {
        val baseUrl = mediaRepository.getBaseUrl().trimEnd('/')
        val parts = book.playableParts
        var offset = 0.0
        val tracks = parts.mapIndexed { index, part ->
            val duration = part.runtimeTicks / TICKS_PER_SECOND
            AudioTrack(
                    index = index,
                    startOffset = offset,
                    duration = duration,
                    title = part.name,
                    contentUrl = "$baseUrl/Audio/${part.id}/stream?static=true",
                    mimeType = mimeTypeFor(part.container),
                )
                .also { offset += duration }
        }
        return JellyfinAudiobookSession(
            sessionId = UUID.randomUUID().toString(),
            bookId = routeId.toString(),
            title = book.name,
            author = book.authorLine.ifEmpty { null },
            coverUrl = book.images.primary?.toString(),
            tracks = tracks,
            chapters = book.timeline(),
            partIds = parts.map { it.id },
            partRuntimeTicks = parts.map { it.runtimeTicks },
            localUris = parts.map { downloadStore.localFile(it.id)?.toUri()?.toString() },
        )
    }
}
