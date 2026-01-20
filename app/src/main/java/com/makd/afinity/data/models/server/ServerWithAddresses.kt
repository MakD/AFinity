package com.makd.afinity.data.models.server

import androidx.room.Embedded
import androidx.room.Relation

data class ServerWithAddresses(
    @Embedded
    val server: Server,
    @Relation(
        parentColumn = "id",
        entityColumn = "serverId",
    )
    val addresses: List<ServerAddress>,
)