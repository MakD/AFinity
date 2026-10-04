package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServiceSettings(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("activeProfileId") val activeProfileId: Int? = null,
    @SerialName("activeProfileName") val activeProfileName: String? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
)

@Serializable
data class QualityProfile(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
)

@Serializable
data class RootFolder(
    @SerialName("id") val id: Int,
    @SerialName("path") val path: String,
    @SerialName("freeSpace") val freeSpace: Long? = null,
)

@Serializable
data class LanguageProfile(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
)

@Serializable
data class ServiceTag(
    @SerialName("id") val id: Int,
    @SerialName("label") val label: String,
)

@Serializable
data class ServiceServerConfig(
    @SerialName("id") val id: Int? = null,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("activeProfileId") val activeProfileId: Int? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
    @SerialName("activeLanguageProfileId") val activeLanguageProfileId: Int? = null,
    @SerialName("activeTags") val activeTags: List<Int> = emptyList(),
    @SerialName("activeAnimeProfileId") val activeAnimeProfileId: Int? = null,
    @SerialName("activeAnimeDirectory") val activeAnimeDirectory: String? = null,
    @SerialName("activeAnimeLanguageProfileId") val activeAnimeLanguageProfileId: Int? = null,
    @SerialName("activeAnimeTags") val activeAnimeTags: List<Int>? = null,
) {
    fun defaultProfileId(isAnime: Boolean): Int? =
        if (isAnime) activeAnimeProfileId ?: activeProfileId else activeProfileId

    fun defaultDirectory(isAnime: Boolean): String? =
        if (isAnime) activeAnimeDirectory ?: activeDirectory else activeDirectory

    fun defaultLanguageProfileId(isAnime: Boolean): Int? =
        if (isAnime) activeAnimeLanguageProfileId ?: activeLanguageProfileId
        else activeLanguageProfileId

    fun defaultTags(isAnime: Boolean): List<Int> =
        if (isAnime) activeAnimeTags ?: activeTags else activeTags
}

@Serializable
data class ServiceDetailsResponse(
    @SerialName("profiles") val profiles: List<QualityProfile> = emptyList(),
    @SerialName("rootFolders") val rootFolders: List<RootFolder> = emptyList(),
    @SerialName("languageProfiles") val languageProfiles: List<LanguageProfile> = emptyList(),
    @SerialName("tags") val tags: List<ServiceTag> = emptyList(),
    @SerialName("server") val server: ServiceServerConfig? = null,
)

@Serializable
data class SonarrSeries(
    @SerialName("tvdbId") val tvdbId: Int,
    @SerialName("title") val title: String,
    @SerialName("year") val year: Int? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("remotePoster") val remotePoster: String? = null,
)
