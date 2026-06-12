package com.makd.afinity.data.models.syncplay

data class SyncPlayMemberInfo(
    val username: String,
    val deviceName: String?,
    val clientName: String?,
    val appVersion: String?,
    val profileImageUrl: String? = null,
)
