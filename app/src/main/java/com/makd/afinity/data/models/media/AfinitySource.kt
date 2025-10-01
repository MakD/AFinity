package com.makd.afinity.data.models.media

import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.database.entities.AfinitySourceDto
import com.makd.afinity.data.repository.JellyfinRepository
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import java.io.File
import java.util.UUID

data class AfinitySource(
    val id: String,
    val name: String,
    val type: AfinitySourceType,
    val path: String,
    val size: Long,
    val mediaStreams: List<AfinityMediaStream>,
    val downloadId: Long? = null,
)

suspend fun MediaSourceInfo.toAfinitySource(
    jellyfinRepository: JellyfinRepository,
    itemId: UUID,
    includePath: Boolean = false,
): AfinitySource {
    val path = when (protocol) {
        MediaProtocol.FILE -> {
            try {
                if (includePath) jellyfinRepository.getStreamUrl(itemId, id.orEmpty()) else ""
            } catch (e: Exception) {
                ""
            }
        }
        MediaProtocol.HTTP -> this.path.orEmpty()
        else -> ""
    }
    return AfinitySource(
        id = id.orEmpty(),
        name = name.orEmpty(),
        type = AfinitySourceType.REMOTE,
        path = path,
        size = size ?: 0,
        mediaStreams = mediaStreams?.map { it.toAfinityMediaStream(jellyfinRepository) } ?: emptyList(),
    )
}

suspend fun AfinitySourceDto.toAfinitySource(
    serverDatabaseDao: ServerDatabaseDao,
): AfinitySource {
    return AfinitySource(
        id = id,
        name = name,
        type = type,
        path = path,
        size = File(path).length(),
        mediaStreams = serverDatabaseDao.getMediaStreamsBySourceId(id).map { it.toAfinityMediaStream() },
        downloadId = downloadId,
    )
}

enum class AfinitySourceType {
    REMOTE,
    LOCAL,
}