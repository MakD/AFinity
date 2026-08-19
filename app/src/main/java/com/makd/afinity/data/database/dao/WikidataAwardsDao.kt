package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.WikidataAwardsCacheEntity

@Dao
interface WikidataAwardsDao {

    @Query(
        "SELECT * FROM wikidata_awards_cache WHERE subjectType = :subjectType AND tmdbId = :tmdbId"
    )
    suspend fun getAwards(subjectType: String, tmdbId: String): WikidataAwardsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAwards(awards: WikidataAwardsCacheEntity)

    @Query("DELETE FROM wikidata_awards_cache") suspend fun clearAll()
}