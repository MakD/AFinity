package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "jellyseerr_addresses")
data class JellyseerrAddressEntity(
    @PrimaryKey val id: UUID,
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val address: String,
)