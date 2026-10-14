package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.lastfm.LastFmTrackPopularity

@Entity(tableName = "lastfm_artist_cache")
data class LastFmArtistCacheEntity(
    @PrimaryKey val artistKey: String,
    val tracks: List<LastFmTrackPopularity> = emptyList(),
    val confirmed: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis(),
)
