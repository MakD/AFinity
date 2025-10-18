package com.makd.afinity.data.updater.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String,
    @SerialName("body") val body: String?,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("assets") val assets: List<GitHubAsset>,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("draft") val draft: Boolean = false
)

@Serializable
data class GitHubAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("size") val size: Long,
    @SerialName("content_type") val contentType: String
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val release: GitHubRelease) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class Downloaded(val file: java.io.File, val release: GitHubRelease) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

enum class UpdateCheckFrequency(val hours: Int, val displayName: String) {
    ON_APP_OPEN(0, "On App Open"),
    SIX_HOURS(6, "Every 6 Hours"),
    TWELVE_HOURS(12, "Every 12 Hours"),
    TWENTY_FOUR_HOURS(24, "Every 24 Hours");

    companion object {
        fun fromHours(hours: Int): UpdateCheckFrequency {
            return entries.find { it.hours == hours } ?: ON_APP_OPEN
        }
    }
}