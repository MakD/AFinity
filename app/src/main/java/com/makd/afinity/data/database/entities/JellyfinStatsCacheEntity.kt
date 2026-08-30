package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.ui.settings.servers.JellyfinStats

@Entity(tableName = "jellyfin_stats_cache")
data class JellyfinStatsCacheEntity(
    @PrimaryKey val serverId: String,
    val movieCount: Int,
    val seriesCount: Int,
    val episodeCount: Int,
    val boxsetCount: Int,
    val albumCount: Int = 0,
    val songCount: Int = 0,
    val artistCount: Int = 0,
    val musicVideoCount: Int = 0,
    val bookCount: Int = 0,
    val trailerCount: Int = 0,
    val programCount: Int = 0,
    val itemCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
)

fun JellyfinStatsCacheEntity.toJellyfinStats() =
    JellyfinStats(
        movieCount = movieCount,
        seriesCount = seriesCount,
        episodeCount = episodeCount,
        boxsetCount = boxsetCount,
        albumCount = albumCount,
        songCount = songCount,
        artistCount = artistCount,
        musicVideoCount = musicVideoCount,
        bookCount = bookCount,
        trailerCount = trailerCount,
        programCount = programCount,
        itemCount = itemCount,
    )
