package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.LastFmArtistCacheEntity
import com.makd.afinity.data.database.entities.LastFmPopularCacheEntity

@Dao
interface LastFmArtistDao {

    @Query("SELECT * FROM lastfm_artist_cache WHERE artistKey = :artistKey")
    suspend fun getArtist(artistKey: String): LastFmArtistCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateArtist(artist: LastFmArtistCacheEntity)

    @Query("DELETE FROM lastfm_artist_cache WHERE fetchedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query(
        "SELECT * FROM lastfm_popular_cache WHERE serverId = :serverId AND userId = :userId " +
            "AND artistId = :artistId"
    )
    suspend fun getPopular(
        serverId: String,
        userId: String,
        artistId: String,
    ): LastFmPopularCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePopular(popular: LastFmPopularCacheEntity)

    @Query("DELETE FROM lastfm_popular_cache WHERE updatedAt < :cutoff")
    suspend fun deletePopularOlderThan(cutoff: Long)

    @Query("DELETE FROM lastfm_artist_cache") suspend fun clearAll()
}
