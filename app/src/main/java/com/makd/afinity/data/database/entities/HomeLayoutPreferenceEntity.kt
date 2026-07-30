package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "home_layout_preferences",
    primaryKeys = ["sessionKey", "sectionKey"],
    indices = [Index(value = ["sessionKey"])],
)
data class HomeLayoutPreferenceEntity(
    val sessionKey: String,
    val sectionKey: String,
    val position: Int,
    val visible: Boolean,
    val maxCount: Int?,
)