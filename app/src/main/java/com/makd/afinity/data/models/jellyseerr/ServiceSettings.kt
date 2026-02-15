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
)

@Serializable
data class ServiceServerConfig(
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("activeProfileId") val activeProfileId: Int? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
)

@Serializable
data class ServiceDetailsResponse(
    @SerialName("profiles") val profiles: List<QualityProfile> = emptyList(),
    @SerialName("rootFolders") val rootFolders: List<RootFolder> = emptyList(),
    @SerialName("server") val server: ServiceServerConfig? = null,
)