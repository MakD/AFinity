package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "custom_home_sections", indices = [Index(value = ["sessionKey"])])
data class CustomHomeSectionEntity(
    @PrimaryKey val id: String,
    val sessionKey: String,
    val position: Int,
    val title: String,
    val sourceType: String,
    val sourceValue: String,
    val includeItemTypes: String,
    val itemLimit: Int,
    val sortBy: String,
    val sortDescending: Boolean,
    val randomOrder: Boolean,
    val cardStyle: String,
    val enabled: Boolean,
    val seasonStart: String?,
    val seasonEnd: String?,
    val filtersJson: String? = null,
)
