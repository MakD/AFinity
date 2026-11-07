package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.WikidataFranchiseEntityEntity
import com.makd.afinity.data.database.entities.WikidataFranchiseTitleEntity

@Dao
interface WikidataFranchiseDao {

    @Query("SELECT * FROM wikidata_franchise_titles WHERE imdbId IN (:imdbIds)")
    suspend fun getTitles(imdbIds: List<String>): List<WikidataFranchiseTitleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTitles(titles: List<WikidataFranchiseTitleEntity>)

    @Query("SELECT * FROM wikidata_franchise_entities WHERE entityId IN (:entityIds)")
    suspend fun getEntities(entityIds: List<String>): List<WikidataFranchiseEntityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntities(entities: List<WikidataFranchiseEntityEntity>)
}
