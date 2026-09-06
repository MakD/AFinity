package com.makd.afinity.util

import java.security.MessageDigest
import java.util.UUID
import org.jellyfin.sdk.model.DeviceInfo

fun DeviceInfo.forUser(userId: UUID): DeviceInfo = forUser(userId.toString())

fun DeviceInfo.forUser(user: String): DeviceInfo =
    copy(
        id =
            MessageDigest.getInstance("SHA-1").run {
                update("$id+$user".toByteArray())
                digest().joinToString("") { "%02x".format(it) }
            }
    )
