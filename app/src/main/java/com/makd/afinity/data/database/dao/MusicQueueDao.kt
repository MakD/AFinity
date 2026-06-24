package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.MusicQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicQueueDao {

    @Query("SELECT * FROM music_queue ORDER BY position ASC")
    fun getQueueFlow(): Flow<List<MusicQueueEntity>>

    @Query("SELECT * FROM music_queue ORDER BY position ASC")
    suspend fun getQueue(): List<MusicQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<MusicQueueEntity>)

    @Query("DELETE FROM music_queue")
    suspend fun clearQueue()

    @Query("DELETE FROM music_queue WHERE position = :position")
    suspend fun removeAt(position: Int)
}