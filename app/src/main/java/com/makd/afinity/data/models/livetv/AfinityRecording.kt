package com.makd.afinity.data.models.livetv

import com.makd.afinity.data.models.media.AfinityItem
import java.time.LocalDateTime

data class AfinityRecording(
    val item: AfinityItem,
    val recordedAt: LocalDateTime?,
    val canDelete: Boolean,
)
