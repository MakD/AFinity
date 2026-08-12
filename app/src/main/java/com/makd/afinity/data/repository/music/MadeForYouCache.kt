package com.makd.afinity.data.repository.music

import androidx.core.net.toUri
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MadeForYouMixKind
import com.makd.afinity.data.models.music.MadeForYouSlot
import com.makd.afinity.data.repository.home.HomeCacheRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val LAYOUT_VERSION = 1

@Serializable
private data class CachedTrack(
    val id: String,
    val name: String,
    val albumId: String? = null,
    val album: String? = null,
    val artistId: String? = null,
    val artist: String? = null,
    val artists: List<String> = emptyList(),
    val indexNumber: Int? = null,
    val discNumber: Int? = null,
    val productionYear: Int? = null,
    val runtimeTicks: Long = 0L,
    val playbackPositionTicks: Long = 0L,
    val played: Boolean = false,
    val favorite: Boolean = false,
    val playCount: Int? = null,
    val normalizationGain: Float? = null,
    val primary: String? = null,
    val blurHash: String? = null,
)

@Serializable
private data class CachedAlbum(
    val id: String,
    val name: String,
    val artistId: String? = null,
    val artist: String? = null,
    val artists: List<String> = emptyList(),
    val productionYear: Int? = null,
    val songCount: Int? = null,
    val runtimeTicks: Long = 0L,
    val genres: List<String> = emptyList(),
    val overview: String? = null,
    val favorite: Boolean = false,
    val played: Boolean = false,
    val playCount: Int? = null,
    val primary: String? = null,
    val blurHash: String? = null,
)

@Serializable
private data class CachedSlot(
    val slotId: String,
    val mixKind: String? = null,
    val seedName: String? = null,
    val tracks: List<CachedTrack> = emptyList(),
    val album: CachedAlbum? = null,
)

@Serializable
private data class CachedLayout(val version: Int = LAYOUT_VERSION, val slots: List<CachedSlot>)

@Singleton
class MadeForYouCache
@Inject
constructor(
    private val homeCacheRepository: HomeCacheRepository,
    private val sessionManager: SessionManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun cacheKey(libraryId: UUID): String? {
        val session = sessionManager.currentSession.value ?: return null
        if (session.serverId.isBlank()) return null
        return "music_mfy_${session.serverId}_${session.userId}_$libraryId"
    }

    suspend fun get(libraryId: UUID, maxAgeMs: Long? = null): List<MadeForYouSlot>? {
        val key = cacheKey(libraryId) ?: return null
        val raw = homeCacheRepository.getRaw(key, maxAgeMs) ?: return null
        return try {
            val layout = json.decodeFromString<CachedLayout>(raw)
            if (layout.version != LAYOUT_VERSION) return null
            val slots = layout.slots.mapNotNull { it.toSlot() }
            slots.ifEmpty { null }
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode Made For You layout for library=$libraryId")
            null
        }
    }

    suspend fun put(libraryId: UUID, slots: List<MadeForYouSlot>) {
        val key = cacheKey(libraryId) ?: return
        val persistable = slots.mapNotNull { it.toCached() }
        if (persistable.isEmpty()) return
        try {
            homeCacheRepository.putRaw(key, json.encodeToString(CachedLayout(slots = persistable)))
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist Made For You layout for library=$libraryId")
        }
    }

    private fun MadeForYouSlot.toCached(): CachedSlot? =
        when (this) {
            is MadeForYouSlot.Mix ->
                if (tracks.isEmpty()) null
                else
                    CachedSlot(
                        slotId = slotId,
                        mixKind = kind.name,
                        seedName = seedName,
                        tracks = tracks.map { it.toCached() },
                    )
            is MadeForYouSlot.Album -> CachedSlot(slotId = slotId, album = album.toCached())
            is MadeForYouSlot.Pending -> null
        }

    private fun CachedSlot.toSlot(): MadeForYouSlot? {
        album?.let { cached ->
            return cached.toAlbum()?.let { MadeForYouSlot.Album(slotId, it) }
        }
        val kind =
            mixKind?.let { name ->
                MadeForYouMixKind.entries.firstOrNull { it.name == name }
            } ?: return null
        val tracks = tracks.mapNotNull { it.toTrack() }
        if (tracks.isEmpty()) return null
        return MadeForYouSlot.Mix(slotId, kind, seedName, tracks)
    }

    private fun AfinityTrack.toCached() =
        CachedTrack(
            id = id.toString(),
            name = name,
            albumId = albumId?.toString(),
            album = album,
            artistId = artistId?.toString(),
            artist = artist,
            artists = artists,
            indexNumber = indexNumber,
            discNumber = discNumber,
            productionYear = productionYear,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = playbackPositionTicks,
            played = played,
            favorite = favorite,
            playCount = playCount,
            normalizationGain = normalizationGain,
            primary = images.primary?.toString(),
            blurHash = images.primaryImageBlurHash,
        )

    private fun CachedTrack.toTrack(): AfinityTrack? {
        val uuid = id.toUuidOrNull() ?: return null
        return AfinityTrack(
            id = uuid,
            name = name,
            albumId = albumId?.toUuidOrNull(),
            album = album,
            artistId = artistId?.toUuidOrNull(),
            artist = artist,
            artists = artists,
            indexNumber = indexNumber,
            discNumber = discNumber,
            productionYear = productionYear,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = playbackPositionTicks,
            played = played,
            favorite = favorite,
            playCount = playCount,
            normalizationGain = normalizationGain,
            images =
                AfinityImages(
                    primary = primary?.toUri(),
                    primaryImageBlurHash = blurHash,
                ),
        )
    }

    private fun AfinityAlbum.toCached() =
        CachedAlbum(
            id = id.toString(),
            name = name,
            artistId = artistId?.toString(),
            artist = artist,
            artists = artists,
            productionYear = productionYear,
            songCount = songCount,
            runtimeTicks = runtimeTicks,
            genres = genres,
            overview = overview,
            favorite = favorite,
            played = played,
            playCount = playCount,
            primary = images.primary?.toString(),
            blurHash = images.primaryImageBlurHash,
        )

    private fun CachedAlbum.toAlbum(): AfinityAlbum? {
        val uuid = id.toUuidOrNull() ?: return null
        return AfinityAlbum(
            id = uuid,
            name = name,
            artistId = artistId?.toUuidOrNull(),
            artist = artist,
            artists = artists,
            productionYear = productionYear,
            songCount = songCount,
            runtimeTicks = runtimeTicks,
            genres = genres,
            overview = overview,
            favorite = favorite,
            played = played,
            playCount = playCount,
            images =
                AfinityImages(
                    primary = primary?.toUri(),
                    primaryImageBlurHash = blurHash,
                ),
        )
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}