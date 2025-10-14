package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.makd.afinity.data.database.entities.AfinitySourceDto
import java.util.UUID

@Dao
interface SourceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: AfinitySourceDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<AfinitySourceDto>)

    @Update
    suspend fun updateSource(source: AfinitySourceDto)

    @Delete
    suspend fun deleteSource(source: AfinitySourceDto)

    @Query("DELETE FROM sources WHERE id = :sourceId")
    suspend fun deleteSourceById(sourceId: String)

    @Query("DELETE FROM sources WHERE itemId = :itemId")
    suspend fun deleteSourcesByItemId(itemId: UUID)

    @Query("SELECT * FROM sources WHERE id = :sourceId")
    suspend fun getSource(sourceId: String): AfinitySourceDto?

    @Query("SELECT * FROM sources WHERE itemId = :itemId")
    suspend fun getSourcesForItem(itemId: UUID): List<AfinitySourceDto>

    @Query("SELECT * FROM sources WHERE downloadId = :downloadId")
    suspend fun getSourcesByDownloadId(downloadId: Long): List<AfinitySourceDto>

    @Query("SELECT * FROM sources WHERE type = 'LOCAL'")
    suspend fun getLocalSources(): List<AfinitySourceDto>

    @Query("SELECT COUNT(*) FROM sources WHERE type = 'LOCAL'")
    suspend fun getLocalSourceCount(): Int

    @Query("SELECT SUM(length(path)) FROM sources WHERE type = 'LOCAL'")
    suspend fun getTotalLocalStorageSize(): Long?

    @Query("DELETE FROM sources")
    suspend fun deleteAllSources()
}