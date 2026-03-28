package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.JellyfinStatsCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JellyfinStatsDao {
    @Query("SELECT * FROM jellyfin_stats_cache WHERE serverId = :serverId")
    fun getStatsFlow(serverId: String): Flow<JellyfinStatsCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: JellyfinStatsCacheEntity)
}
