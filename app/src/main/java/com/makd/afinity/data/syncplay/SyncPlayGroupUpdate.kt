package com.makd.afinity.data.syncplay

import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.GroupUpdateType

data class SyncPlayGroupUpdate(
    val type: GroupUpdateType,
    val groupId: UUID,
)
