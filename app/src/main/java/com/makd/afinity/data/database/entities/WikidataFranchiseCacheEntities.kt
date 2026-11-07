package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wikidata_franchise_titles")
data class WikidataFranchiseTitleEntity(
    @PrimaryKey val imdbId: String,
    val entityId: String?,
    val groupRefs: List<String>,
    val linkRefs: List<String>,
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "wikidata_franchise_entities")
data class WikidataFranchiseEntityEntity(
    @PrimaryKey val entityId: String,
    val label: String?,
    val types: List<String>,
    val parents: List<String>,
    val logo: String?,
    val fetchedAt: Long = System.currentTimeMillis(),
)
