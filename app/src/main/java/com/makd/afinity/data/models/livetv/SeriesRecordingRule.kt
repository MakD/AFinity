package com.makd.afinity.data.models.livetv

data class SeriesRecordingRule(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val options: SeriesRecordingOptions,
)
