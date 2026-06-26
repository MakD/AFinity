package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.MusicAlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicAlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: MusicAlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<MusicAlbumEntity>)

    @Query("SELECT * FROM music_albums WHERE id = :id AND serverId = :serverId AND userId = :userId LIMIT 1")
    suspend fun getById(id: String, serverId: String, userId: String): MusicAlbumEntity?

    @Query("SELECT * FROM music_albums WHERE serverId = :serverId AND userId = :userId ORDER BY name ASC")
    fun getAllFlow(serverId: String, userId: String): Flow<List<MusicAlbumEntity>>

    @Query("SELECT * FROM music_albums WHERE serverId = :serverId AND userId = :userId ORDER BY name ASC")
    suspend fun getAll(serverId: String, userId: String): List<MusicAlbumEntity>

    @Query("UPDATE music_albums SET localImagePath = :path WHERE id = :id AND serverId = :serverId AND userId = :userId")
    suspend fun updateLocalImagePath(id: String, serverId: String, userId: String, path: String)

    @Query("DELETE FROM music_albums WHERE id = :id AND serverId = :serverId AND userId = :userId")
    suspend fun delete(id: String, serverId: String, userId: String)

    @Query("SELECT * FROM music_albums WHERE userId = :userId ORDER BY name ASC")
    fun getAllFlowByUser(userId: String): Flow<List<MusicAlbumEntity>>

    @Query("SELECT * FROM music_albums WHERE userId = :userId ORDER BY name ASC")
    suspend fun getAllByUser(userId: String): List<MusicAlbumEntity>
}