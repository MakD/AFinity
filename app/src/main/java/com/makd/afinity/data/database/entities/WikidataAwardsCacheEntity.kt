package com.makd.afinity.data.database.entities

import androidx.room.Entity
import com.makd.afinity.data.models.wikidata.WikidataAward

@Entity(tableName = "wikidata_awards_cache", primaryKeys = ["subjectType", "tmdbId"])
data class WikidataAwardsCacheEntity(
    val subjectType: String,
    val tmdbId: String,
    val awards: List<WikidataAward> = emptyList(),
    val confirmed: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis(),
)