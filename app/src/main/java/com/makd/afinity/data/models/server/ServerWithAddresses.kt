package com.makd.afinity.data.models.server

import androidx.room.Embedded
import androidx.room.Relation
import com.makd.afinity.data.models.user.User

data class ServerWithAddresses(
    @Embedded
    val server: Server,
    @Relation(
        parentColumn = "id",
        entityColumn = "serverId",
    )
    val addresses: List<ServerAddress>,
    @Relation(
        parentColumn = "currentUserId",
        entityColumn = "id",
    )
    val user: User?,
)