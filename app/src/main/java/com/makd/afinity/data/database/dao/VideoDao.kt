package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.AfinityMusicVideoDto
import com.makd.afinity.data.database.entities.AfinityVideoDto
import java.util.UUID

@Dao
interface VideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVideo(video: AfinityVideoDto)

    @Query("SELECT * FROM videos WHERE id = :videoId AND serverId = :serverId")
    suspend fun getVideo(videoId: UUID, serverId: String): AfinityVideoDto?

    @Query("SELECT * FROM videos WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getVideos(serverId: String): List<AfinityVideoDto>

    @Query("DELETE FROM videos WHERE id = :videoId") suspend fun deleteVideo(videoId: UUID)

    @Query("DELETE FROM music_videos WHERE id = :musicVideoId")
    suspend fun deleteMusicVideo(musicVideoId: UUID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicVideo(musicVideo: AfinityMusicVideoDto)

    @Query("SELECT * FROM music_videos WHERE id = :musicVideoId AND serverId = :serverId")
    suspend fun getMusicVideo(musicVideoId: UUID, serverId: String): AfinityMusicVideoDto?

    @Query("SELECT * FROM music_videos WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getMusicVideos(serverId: String): List<AfinityMusicVideoDto>
}
