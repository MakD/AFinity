package com.makd.afinity.data.repository

import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfUser
import com.makd.afinity.data.models.audiobookshelf.Library
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.PersonalizedView
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import com.makd.afinity.data.models.audiobookshelf.SearchResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class ItemWithProgress(
    val item: LibraryItem,
    val progress: MediaProgress?
)

interface AudiobookshelfRepository {

    suspend fun setActiveJellyfinSession(serverId: String, userId: UUID)

    fun clearActiveSession()

    val currentSessionId: StateFlow<String?>

    val isAuthenticated: StateFlow<Boolean>

    val currentConfig: StateFlow<AudiobookshelfConfig?>

    suspend fun login(
        serverUrl: String,
        username: String,
        password: String
    ): Result<AudiobookshelfUser>

    suspend fun logout(): Result<Unit>

    suspend fun validateToken(): Result<Boolean>

    suspend fun setServerUrl(url: String)

    suspend fun getServerUrl(): String?

    suspend fun hasValidConfiguration(): Boolean

    fun getLibrariesFlow(): Flow<List<Library>>

    suspend fun refreshLibraries(): Result<List<Library>>

    suspend fun getLibrary(libraryId: String): Result<Library>

    fun getLibraryItemsFlow(libraryId: String): Flow<List<LibraryItem>>

    suspend fun refreshLibraryItems(
        libraryId: String,
        limit: Int = 100,
        page: Int = 0
    ): Result<List<LibraryItem>>

    suspend fun getItemDetails(itemId: String): Result<LibraryItem>

    suspend fun searchLibrary(libraryId: String, query: String): Result<SearchResponse>

    suspend fun getPersonalized(libraryId: String): Result<List<PersonalizedView>>

    fun getInProgressItemsFlow(): Flow<List<ItemWithProgress>>

    suspend fun refreshProgress(): Result<List<MediaProgress>>

    suspend fun updateProgress(
        itemId: String,
        episodeId: String?,
        currentTime: Double,
        duration: Double,
        isFinished: Boolean
    ): Result<MediaProgress>

    fun getProgressForItemFlow(itemId: String): Flow<MediaProgress?>

    suspend fun startPlaybackSession(itemId: String, episodeId: String?): Result<PlaybackSession>

    suspend fun syncPlaybackSession(
        sessionId: String,
        timeListened: Double,
        currentTime: Double,
        duration: Double
    ): Result<Unit>

    suspend fun closePlaybackSession(
        sessionId: String,
        currentTime: Double,
        timeListened: Double,
        duration: Double
    ): Result<Unit>

    suspend fun syncPendingProgress(): Result<Int>
}

data class AudiobookshelfConfig(
    val serverUrl: String,
    val absUserId: String,
    val username: String
)
