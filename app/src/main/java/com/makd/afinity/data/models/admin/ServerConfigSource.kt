package com.makd.afinity.data.models.admin

enum class ServerConfigSource(val path: String) {
    SYSTEM("/System/Configuration"),
    ENCODING("/System/Configuration/encoding"),
    NETWORK("/System/Configuration/network"),
    NFO("/System/Configuration/xbmcmetadata"),
}
