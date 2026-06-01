package com.makd.afinity.data.models.syncplay

import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.GroupRepeatMode
import org.jellyfin.sdk.model.api.GroupShuffleMode
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.SyncPlayQueueItem

data class SyncPlayState(
    val isInGroup: Boolean = false,
    val groupId: UUID? = null,
    val groupName: String = "",
    val members: List<String> = emptyList(),
    val groupState: GroupStateType = GroupStateType.IDLE,
    val queue: List<SyncPlayQueueItem> = emptyList(),
    val playingItemIndex: Int = 0,
    val shuffleMode: GroupShuffleMode = GroupShuffleMode.SORTED,
    val repeatMode: GroupRepeatMode = GroupRepeatMode.REPEAT_NONE,
)
