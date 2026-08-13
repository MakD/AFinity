package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.HomeLayoutPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeLayoutPreferenceDao {

    @Query("SELECT * FROM home_layout_preferences WHERE sessionKey = :sessionKey")
    fun observeForSession(sessionKey: String): Flow<List<HomeLayoutPreferenceEntity>>

    @Query("SELECT * FROM home_layout_preferences WHERE sessionKey = :sessionKey")
    suspend fun getForSession(sessionKey: String): List<HomeLayoutPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HomeLayoutPreferenceEntity)

    @Query("DELETE FROM home_layout_preferences WHERE sessionKey = :sessionKey")
    suspend fun deleteForSession(sessionKey: String)
}
