package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.AudiobookshelfConfigEntity
import com.makd.afinity.data.database.entities.AudiobookshelfItemEntity
import com.makd.afinity.data.database.entities.AudiobookshelfLibraryEntity
import com.makd.afinity.data.database.entities.AudiobookshelfProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookshelfDao {

    @Query("SELECT * FROM audiobookshelf_config WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun getConfig(serverId: String, userId: String): AudiobookshelfConfigEntity?

    @Query("SELECT * FROM audiobookshelf_config WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    fun getConfigFlow(serverId: String, userId: String): Flow<AudiobookshelfConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: AudiobookshelfConfigEntity)

    @Query("DELETE FROM audiobookshelf_config WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun deleteConfig(serverId: String, userId: String)

    @Query("SELECT * FROM audiobookshelf_libraries WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId ORDER BY displayOrder ASC")
    fun getLibrariesFlow(serverId: String, userId: String): Flow<List<AudiobookshelfLibraryEntity>>

    @Query("SELECT * FROM audiobookshelf_libraries WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId ORDER BY displayOrder ASC")
    suspend fun getLibraries(serverId: String, userId: String): List<AudiobookshelfLibraryEntity>

    @Query("SELECT * FROM audiobookshelf_libraries WHERE id = :libraryId AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun getLibrary(
        libraryId: String,
        serverId: String,
        userId: String
    ): AudiobookshelfLibraryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraries(libraries: List<AudiobookshelfLibraryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrary(library: AudiobookshelfLibraryEntity)

    @Query("DELETE FROM audiobookshelf_libraries WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun deleteAllLibraries(serverId: String, userId: String)

    @Query("SELECT * FROM audiobookshelf_items WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId AND libraryId = :libraryId ORDER BY title ASC")
    fun getItemsFlow(
        serverId: String,
        userId: String,
        libraryId: String
    ): Flow<List<AudiobookshelfItemEntity>>

    @Query("SELECT * FROM audiobookshelf_items WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId AND libraryId = :libraryId ORDER BY title ASC")
    suspend fun getItems(
        serverId: String,
        userId: String,
        libraryId: String
    ): List<AudiobookshelfItemEntity>

    @Query("SELECT * FROM audiobookshelf_items WHERE id = :itemId AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun getItem(itemId: String, serverId: String, userId: String): AudiobookshelfItemEntity?

    @Query("SELECT * FROM audiobookshelf_items WHERE id = :itemId AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    fun getItemFlow(
        itemId: String,
        serverId: String,
        userId: String
    ): Flow<AudiobookshelfItemEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<AudiobookshelfItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: AudiobookshelfItemEntity)

    @Query("DELETE FROM audiobookshelf_items WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId AND libraryId = :libraryId")
    suspend fun deleteItemsByLibrary(serverId: String, userId: String, libraryId: String)

    @Query("DELETE FROM audiobookshelf_items WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun deleteAllItems(serverId: String, userId: String)

    @Query("SELECT * FROM audiobookshelf_items WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId AND (title LIKE '%' || :query || '%' OR authorName LIKE '%' || :query || '%') ORDER BY title ASC")
    suspend fun searchItems(
        serverId: String,
        userId: String,
        query: String
    ): List<AudiobookshelfItemEntity>

    @Query("SELECT * FROM audiobookshelf_progress WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId ORDER BY lastUpdate DESC")
    fun getAllProgressFlow(
        serverId: String,
        userId: String
    ): Flow<List<AudiobookshelfProgressEntity>>

    @Query("SELECT * FROM audiobookshelf_progress WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId AND isFinished = 0 ORDER BY lastUpdate DESC")
    fun getInProgressFlow(
        serverId: String,
        userId: String
    ): Flow<List<AudiobookshelfProgressEntity>>

    @Query("SELECT * FROM audiobookshelf_progress WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId AND isFinished = 0 ORDER BY lastUpdate DESC")
    suspend fun getInProgress(serverId: String, userId: String): List<AudiobookshelfProgressEntity>

    @Query("SELECT * FROM audiobookshelf_progress WHERE libraryItemId = :itemId AND jellyfinServerId = :serverId AND jellyfinUserId = :userId AND (episodeId IS NULL OR episodeId = '')")
    suspend fun getProgressForItem(
        itemId: String,
        serverId: String,
        userId: String
    ): AudiobookshelfProgressEntity?

    @Query("SELECT * FROM audiobookshelf_progress WHERE libraryItemId = :itemId AND episodeId = :episodeId AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun getProgressForEpisode(
        itemId: String,
        episodeId: String,
        serverId: String,
        userId: String
    ): AudiobookshelfProgressEntity?

    @Query("SELECT * FROM audiobookshelf_progress WHERE libraryItemId = :itemId AND jellyfinServerId = :serverId AND jellyfinUserId = :userId AND (episodeId IS NULL OR episodeId = '')")
    fun getProgressForItemFlow(
        itemId: String,
        serverId: String,
        userId: String
    ): Flow<AudiobookshelfProgressEntity?>

    @Query("SELECT * FROM audiobookshelf_progress WHERE pendingSync = 1 AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun getPendingSyncProgress(
        serverId: String,
        userId: String
    ): List<AudiobookshelfProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: AudiobookshelfProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressList(progressList: List<AudiobookshelfProgressEntity>)

    @Query("UPDATE audiobookshelf_progress SET pendingSync = 0 WHERE id = :progressId AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun markSynced(progressId: String, serverId: String, userId: String)

    @Query("DELETE FROM audiobookshelf_progress WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun deleteAllProgress(serverId: String, userId: String)

    @Query("DELETE FROM audiobookshelf_items WHERE cachedAt < :expiryTime AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun deleteExpiredItems(expiryTime: Long, serverId: String, userId: String)

    @Query("DELETE FROM audiobookshelf_libraries WHERE cachedAt < :expiryTime AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun deleteExpiredLibraries(expiryTime: Long, serverId: String, userId: String)
}
