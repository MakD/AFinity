package com.makd.afinity.data.models.subtitles

import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class SubtitleItemIdentity(
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val seriesImdbId: String? = null,
    val seriesTmdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
) {
    fun matches(other: SubtitleItemIdentity): Boolean {
        if (season != other.season || episode != other.episode) return false
        val own = setOfNotNull(imdbId, tmdbId, seriesImdbId, seriesTmdbId)
        val theirs =
            setOfNotNull(other.imdbId, other.tmdbId, other.seriesImdbId, other.seriesTmdbId)
        if (own.isEmpty() || theirs.isEmpty()) return false
        if (season == null) return own.intersect(theirs).isNotEmpty()
        val ownSeries = setOfNotNull(seriesImdbId, seriesTmdbId)
        val theirSeries = setOfNotNull(other.seriesImdbId, other.seriesTmdbId)
        return ownSeries.intersect(theirSeries).isNotEmpty()
    }
}

@Serializable
data class DeviceSubtitleMeta(
    val fileName: String,
    val subtitleId: String,
    val language: String,
    val format: String,
    val releaseName: String? = null,
    val hearingImpaired: Boolean = false,
    val aiTranscribed: Boolean = false,
    val runtimeTicksAtSave: Long = 0L,
    val savedAtMs: Long = 0L,
)

@Serializable
data class ServerAddedSubtitle(
    val fileName: String,
    val language: String,
    val addedAtMs: Long = 0L,
)

@Serializable
data class DeviceSubtitleFolder(
    val itemId: String,
    val identity: SubtitleItemIdentity = SubtitleItemIdentity(),
    val subtitles: List<DeviceSubtitleMeta> = emptyList(),
    val serverAdded: List<ServerAddedSubtitle> = emptyList(),
    val lastSelectedFileName: String? = null,
)

data class DeviceSubtitle(val file: File, val meta: DeviceSubtitleMeta)

data class DeviceSubtitleStats(val count: Int, val bytes: Long)
