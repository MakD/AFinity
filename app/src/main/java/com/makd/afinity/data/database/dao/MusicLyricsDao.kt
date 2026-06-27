package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.MusicLyricsEntity

@Dao
interface MusicLyricsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lyrics: MusicLyricsEntity)

    @Query("SELECT * FROM music_lyrics WHERE trackId = :trackId AND serverId = :serverId AND userId = :userId LIMIT 1")
    suspend fun get(trackId: String, serverId: String, userId: String): MusicLyricsEntity?

    @Query("DELETE FROM music_lyrics WHERE trackId = :trackId AND serverId = :serverId AND userId = :userId")
    suspend fun delete(trackId: String, serverId: String, userId: String)

    @Query("DELETE FROM music_lyrics WHERE serverId = :serverId AND userId = :userId")
    suspend fun deleteAll(serverId: String, userId: String)
}