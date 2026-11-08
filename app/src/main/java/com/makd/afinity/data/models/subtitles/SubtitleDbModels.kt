package com.makd.afinity.data.models.subtitles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubtitleDbBundle(
    val title: SubtitleDbTitle? = null,
    val subtitles: SubtitleDbPage = SubtitleDbPage(),
)

@Serializable
data class SubtitleDbTitle(
    val imdb: String? = null,
    @SerialName("tmdb_id") val tmdbId: Long? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val name: String? = null,
    val year: Int? = null,
    @SerialName("subtitle_count") val subtitleCount: Int? = null,
    @SerialName("subtitle_languages") val subtitleLanguages: Map<String, Int> = emptyMap(),
)

@Serializable
data class SubtitleDbPage(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val items: List<SubtitleDbRow> = emptyList(),
)

@Serializable
data class SubtitleDbRow(
    val id: Long,
    val language: String = "",
    val format: String? = null,
    val cues: Int? = null,
    @SerialName("duration_s") val durationSeconds: Double? = null,
    val bytes: Long? = null,
    val encoding: String? = null,
    @SerialName("release_name") val releaseName: String? = null,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    val fps: Double? = null,
    @SerialName("ai_transcribed") val aiTranscribed: Boolean = false,
)
