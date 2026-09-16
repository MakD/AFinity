package com.makd.afinity.data.models.server

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "serverAddressMemory",
    primaryKeys = ["serverId", "networkKey"],
    foreignKeys =
        [
            ForeignKey(
                entity = Server::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("serverId"),
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class ServerAddressMemory(
    @ColumnInfo(index = true) val serverId: String,
    val networkKey: String,
    val address: String,
    val lastSucceededAt: Long,
)
