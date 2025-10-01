package com.makd.afinity.data.models.server

import androidx.room.Embedded
import androidx.room.Relation
import com.makd.afinity.data.models.user.User

data class ServerWithAddressAndUser(
    @Embedded
    val server: Server,
    @Relation(
        parentColumn = "currentServerAddressId",
        entityColumn = "id",
    )
    val address: ServerAddress?,
    @Relation(
        parentColumn = "currentUserId",
        entityColumn = "id",
    )
    val user: User?,
)