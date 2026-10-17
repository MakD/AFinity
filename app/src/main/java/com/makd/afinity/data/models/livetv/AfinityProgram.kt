package com.makd.afinity.data.models.livetv

import com.makd.afinity.data.models.media.AfinityImages
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.ProgramAudio
import org.jellyfin.sdk.model.api.RecordingStatus

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
    val isRepeat: Boolean,
    val isSeries: Boolean,
    val isMovie: Boolean,
    val isNews: Boolean,
    val isKids: Boolean,
    val isSports: Boolean,
    val isHd: Boolean,
    val audio: ProgramAudio?,
    val episodeTitle: String?,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val productionYear: Int?,
    val originalAirDate: LocalDateTime?,
    val genres: List<String>,
    val tags: List<String>,
    val officialRating: String?,
    val communityRating: Float?,
    val providerIds: Map<String, String>?,
    val channelName: String?,
    val channelNumber: String?,
    val timerId: String?,
    val seriesTimerId: String?,
    val timerStatus: RecordingStatus?,
) {
    val durationMinutes: Long?
        get() {
            if (startDate == null || endDate == null) return null
            return java.time.Duration.between(startDate, endDate).toMinutes()
        }

    fun getProgressPercent(now: LocalDateTime = LocalDateTime.now()): Float {
        if (startDate == null || endDate == null) return 0f
        if (now.isBefore(startDate)) return 0f
        if (now.isAfter(endDate)) return 100f
        val totalDuration = java.time.Duration.between(startDate, endDate).toMillis()
        val elapsed = java.time.Duration.between(startDate, now).toMillis()
        return (elapsed.toFloat() / totalDuration.toFloat() * 100f).coerceIn(0f, 100f)
    }

    fun isCurrentlyAiring(now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (startDate == null || endDate == null) return false
        return now.isAfter(startDate) && now.isBefore(endDate)
    }
}
