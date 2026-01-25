package com.makd.afinity.data.models.livetv

import com.makd.afinity.data.models.media.AfinityImages
import java.time.LocalDateTime
import java.util.UUID

data class AfinityProgram(
    val id: UUID,
    val channelId: UUID,
    val name: String,
    val overview: String,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val images: AfinityImages,
    val isLive: Boolean,
    val isNew: Boolean,
    val isPremiere: Boolean,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val productionYear: Int?,
    val genres: List<String>,
    val officialRating: String?,
    val communityRating: Float?
) {
    val durationMinutes: Long?
        get() {
            if (startDate == null || endDate == null) return null
            return java.time.Duration.between(startDate, endDate).toMinutes()
        }

    fun getProgressPercent(): Float {
        if (startDate == null || endDate == null) return 0f
        val now = LocalDateTime.now()
        if (now.isBefore(startDate)) return 0f
        if (now.isAfter(endDate)) return 100f
        val totalDuration = java.time.Duration.between(startDate, endDate).toMillis()
        val elapsed = java.time.Duration.between(startDate, now).toMillis()
        return (elapsed.toFloat() / totalDuration.toFloat() * 100f).coerceIn(0f, 100f)
    }

    fun isCurrentlyAiring(): Boolean {
        if (startDate == null || endDate == null) return false
        val now = LocalDateTime.now()
        return now.isAfter(startDate) && now.isBefore(endDate)
    }
}