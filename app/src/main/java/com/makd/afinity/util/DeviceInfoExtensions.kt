package com.makd.afinity.util

import org.jellyfin.sdk.model.DeviceInfo
import java.security.MessageDigest
import java.util.UUID

fun DeviceInfo.forUser(userId: UUID): DeviceInfo = forUser(userId.toString())

fun DeviceInfo.forUser(user: String): DeviceInfo =
    copy(
        id =
            MessageDigest.getInstance("SHA-1").run {
                update("$id+$user".toByteArray())
                digest().joinToString("") { "%02x".format(it) }
            }
    )