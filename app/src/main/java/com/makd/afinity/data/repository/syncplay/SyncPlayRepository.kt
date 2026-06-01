package com.makd.afinity.data.repository.syncplay

import com.makd.afinity.data.models.syncplay.SyncPlayState
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.GroupInfoDto

interface SyncPlayRepository {

    val syncPlayState: StateFlow<SyncPlayState>

    suspend fun getGroups(): List<GroupInfoDto>

    suspend fun createGroup(name: String)

    suspend fun joinGroup(groupId: UUID)

    suspend fun leaveGroup()

    suspend fun pause()

    suspend fun unpause()

    suspend fun seek(positionTicks: Long)

    suspend fun stop()

    suspend fun reportBuffering(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: UUID,
    )

    suspend fun reportReady(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: UUID,
    )

    suspend fun ping(clientTimeMs: Long)

    fun updateFromGroupEvent(event: com.makd.afinity.data.syncplay.SyncPlayGroupEvent)

    fun setGroupJoined(groupId: UUID)

    suspend fun setNewQueue(itemIds: List<UUID>, position: Int, startPositionTicks: Long)
}