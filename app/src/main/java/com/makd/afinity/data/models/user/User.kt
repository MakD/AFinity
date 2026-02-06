package com.makd.afinity.data.models.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.server.Server
import java.util.UUID

@Entity(
    tableName = "users",
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
data class User(
    @PrimaryKey val id: UUID,
    val name: String,
    @ColumnInfo(index = true) val serverId: String,
    val accessToken: String? = null,
    val primaryImageTag: String? = null,
)
