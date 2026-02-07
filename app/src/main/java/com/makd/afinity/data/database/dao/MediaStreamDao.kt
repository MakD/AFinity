package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.makd.afinity.data.database.entities.AfinityMediaStreamDto
import java.util.UUID

@Dao
interface MediaStreamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaStream(stream: AfinityMediaStreamDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaStreams(streams: List<AfinityMediaStreamDto>)

    @Update suspend fun updateMediaStream(stream: AfinityMediaStreamDto)

    @Delete suspend fun deleteMediaStream(stream: AfinityMediaStreamDto)

    @Query("DELETE FROM mediastreams WHERE id = :streamId")
    suspend fun deleteMediaStreamById(streamId: UUID)

    @Query("DELETE FROM mediastreams WHERE sourceId = :sourceId")
    suspend fun deleteMediaStreamsBySourceId(sourceId: String)

    @Query("SELECT * FROM mediastreams WHERE id = :streamId")
    suspend fun getMediaStream(streamId: UUID): AfinityMediaStreamDto?

    @Query("SELECT * FROM mediastreams WHERE sourceId = :sourceId")
    suspend fun getMediaStreamsBySourceId(sourceId: String): List<AfinityMediaStreamDto>

    @Query("SELECT * FROM mediastreams WHERE sourceId = :sourceId AND type = :type")
    suspend fun getMediaStreamsByType(sourceId: String, type: String): List<AfinityMediaStreamDto>

    @Query("SELECT COUNT(*) FROM mediastreams") suspend fun getMediaStreamCount(): Int

    @Query("DELETE FROM mediastreams") suspend fun deleteAllMediaStreams()
}
