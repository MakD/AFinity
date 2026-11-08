package com.makd.afinity.data.repository.subtitles

import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.subtitles.DeviceSubtitle
import com.makd.afinity.data.models.subtitles.SubtitleItemIdentity
import kotlinx.coroutines.flow.Flow

enum class SubtitleSearchMode {
    SERVER,
    UPLOAD,
    DEVICE;

    val usesSubtitleDb: Boolean
        get() = this != SERVER
}

data class SubtitleCandidate(
    val id: String,
    val language: String,
    val releaseName: String,
    val format: String,
    val hearingImpaired: Boolean,
    val aiTranscribed: Boolean = false,
    val lineCount: Int? = null,
    val durationSeconds: Double? = null,
    val providerName: String? = null,
    val encoding: String? = null,
)

data class SubtitleLanguageCount(val code: String, val count: Int?)

data class SubtitleSearchResult(
    val mode: SubtitleSearchMode,
    val language: String,
    val candidates: List<SubtitleCandidate>,
    val languages: List<SubtitleLanguageCount>,
)

sealed interface AddedSubtitle {
    data class OnDevice(val subtitle: DeviceSubtitle) : AddedSubtitle

    data class OnServer(val fileName: String?, val language: String) : AddedSubtitle
}

class SubtitleDbNotEnabledException : Exception("TheSubtitleDB is not enabled for this profile")

class SubtitleDownloadException(message: String) : Exception(message)

interface SubtitleSearchRepository {

    fun currentMode(): SubtitleSearchMode?

    fun currentScope(): SubtitleScope?

    fun subtitleDbEnabledFlow(): Flow<Boolean>

    suspend fun isSubtitleDbEnabled(): Boolean

    suspend fun setSubtitleDbEnabled(enabled: Boolean)

    suspend fun identity(item: AfinityItem): SubtitleItemIdentity

    suspend fun search(
        item: AfinityItem,
        language: String,
        mode: SubtitleSearchMode,
    ): Result<SubtitleSearchResult>

    suspend fun serverLanguageCounts(item: AfinityItem, languages: List<String>): Map<String, Int>

    suspend fun serverLanguages(): Result<List<String>>

    suspend fun add(
        item: AfinityItem,
        candidate: SubtitleCandidate,
        mode: SubtitleSearchMode,
    ): Result<AddedSubtitle>
}

fun externalSubtitleFileName(path: String?): String? =
    path?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
