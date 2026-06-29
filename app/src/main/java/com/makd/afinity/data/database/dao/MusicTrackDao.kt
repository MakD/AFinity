package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.MusicTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: MusicTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<MusicTrackEntity>)

    @Query("SELECT * FROM music_tracks WHERE id = :id AND serverId = :serverId AND userId = :userId LIMIT 1")
    suspend fun getById(id: String, serverId: String, userId: String): MusicTrackEntity?

    @Query("SELECT * FROM music_tracks WHERE albumId = :albumId AND serverId = :serverId AND userId = :userId ORDER BY discNumber ASC, indexNumber ASC")
    suspend fun getByAlbum(albumId: String, serverId: String, userId: String): List<MusicTrackEntity>

    @Query("SELECT * FROM music_tracks WHERE serverId = :serverId AND userId = :userId ORDER BY name ASC")
    fun getAllFlow(serverId: String, userId: String): Flow<List<MusicTrackEntity>>

    @Query("SELECT * FROM music_tracks WHERE serverId = :serverId AND userId = :userId ORDER BY name ASC")
    suspend fun getAll(serverId: String, userId: String): List<MusicTrackEntity>

    @Query("UPDATE music_tracks SET localFilePath = :path WHERE id = :id AND serverId = :serverId AND userId = :userId")
    suspend fun updateLocalFilePath(id: String, serverId: String, userId: String, path: String)

    @Query("UPDATE music_tracks SET localFilePath = NULL WHERE id = :id AND serverId = :serverId AND userId = :userId")
    suspend fun clearLocalFilePath(id: String, serverId: String, userId: String)

    @Query("UPDATE music_tracks SET localImagePath = :path WHERE id = :id AND serverId = :serverId AND userId = :userId")
    suspend fun updateLocalImagePath(id: String, serverId: String, userId: String, path: String)

    @Query("DELETE FROM music_tracks WHERE id = :id AND serverId = :serverId AND userId = :userId")
    suspend fun delete(id: String, serverId: String, userId: String)

    @Query("DELETE FROM music_tracks WHERE albumId = :albumId AND serverId = :serverId AND userId = :userId")
    suspend fun deleteByAlbum(albumId: String, serverId: String, userId: String)

    @Query("SELECT * FROM music_tracks WHERE userId = :userId ORDER BY name ASC")
    fun getAllFlowByUser(userId: String): Flow<List<MusicTrackEntity>>

    @Query("SELECT * FROM music_tracks WHERE userId = :userId ORDER BY name ASC")
    suspend fun getAllByUser(userId: String): List<MusicTrackEntity>
}