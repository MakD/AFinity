package com.makd.afinity.data.models.livetv

import java.time.LocalDateTime

data class SeriesRecordingOptions(
    val recordNewOnly: Boolean,
    val skipEpisodesInLibrary: Boolean,
    val recordAnyChannel: Boolean,
    val recordAnyTime: Boolean,
    val keepUpTo: Int,
    val padding: RecordingPadding,
    val channelName: String?,
    val startDate: LocalDateTime?,
)
