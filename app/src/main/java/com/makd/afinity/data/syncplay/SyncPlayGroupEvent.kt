package com.makd.afinity.data.syncplay

import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.GroupUpdateType
import org.jellyfin.sdk.model.api.PlayQueueUpdate

sealed class SyncPlayGroupEvent {
    data class GroupStateRefreshed(val groupInfo: GroupInfoDto) : SyncPlayGroupEvent()

    data class GroupLeft(val groupId: UUID) : SyncPlayGroupEvent()

    data class StateChanged(val groupId: UUID, val newState: GroupStateType) : SyncPlayGroupEvent()

    data class UserJoined(val groupId: UUID, val userName: String) : SyncPlayGroupEvent()

    data class UserLeft(val groupId: UUID, val userName: String) : SyncPlayGroupEvent()

    data class QueueChanged(val groupId: UUID, val update: PlayQueueUpdate? = null) :
        SyncPlayGroupEvent()

    data class Error(val type: GroupUpdateType, val message: String) : SyncPlayGroupEvent()
}
