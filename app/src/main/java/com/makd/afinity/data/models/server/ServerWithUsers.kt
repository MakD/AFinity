package com.makd.afinity.data.models.server

import androidx.room.Embedded
import androidx.room.Relation
import com.makd.afinity.data.models.user.User

data class ServerWithUsers(
    @Embedded
    val server: Server,
    @Relation(
        parentColumn = "id",
        entityColumn = "serverId",
    )
    val users: List<User>,
)