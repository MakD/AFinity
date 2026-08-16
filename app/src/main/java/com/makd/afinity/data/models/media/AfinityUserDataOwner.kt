package com.makd.afinity.data.models.media

import java.util.UUID

interface AfinityUserDataOwner {
    val id: UUID
    val played: Boolean
    val favorite: Boolean
    val liked: Boolean
    val playbackPositionTicks: Long
}