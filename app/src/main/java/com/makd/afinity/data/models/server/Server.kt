package com.makd.afinity.data.models.server

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class Server(
    @PrimaryKey val id: String,
    val name: String,
    val version: String? = null,
    val address: String,
)
