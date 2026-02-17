package com.makd.afinity.data.models.jellyseerr

object Permissions {
    const val NONE = 0
    const val ADMIN = 2
    const val MANAGE_SETTINGS = 4
    const val MANAGE_USERS = 8
    const val MANAGE_REQUESTS = 16
    const val REQUEST = 32
    const val VOTE = 64
    const val AUTO_APPROVE = 128
    const val AUTO_APPROVE_MOVIE = 256
    const val AUTO_APPROVE_TV = 512
    const val REQUEST_4K = 1024
    const val REQUEST_4K_MOVIE = 2048
    const val REQUEST_4K_TV = 4096
    const val REQUEST_ADVANCED = 8192
    const val REQUEST_VIEW = 16384
    const val AUTO_APPROVE_4K = 32768
    const val AUTO_APPROVE_4K_MOVIE = 65536
    const val AUTO_APPROVE_4K_TV = 131072
    const val REQUEST_MOVIE = 262144
    const val REQUEST_TV = 524288
    const val MANAGE_ISSUES = 1048576
    const val VIEW_ISSUES = 2097152
    const val CREATE_ISSUES = 4194304
    const val AUTO_REQUEST = 8388608
    const val AUTO_REQUEST_MOVIE = 16777216
    const val AUTO_REQUEST_TV = 33554432
    const val RECENT_VIEW = 67108864
    const val WATCHLIST_VIEW = 134217728
    const val MANAGE_BLOCKLIST = 268435456
    const val VIEW_BLOCKLIST = 1073741824
}

fun JellyseerrUser.hasPermission(permission: Int): Boolean {
    if ((this.permissions and Permissions.ADMIN) != 0) {
        return true
    }
    return (this.permissions and permission) != 0
}

fun JellyseerrUser.isAdmin(): Boolean {
    return (this.permissions and Permissions.ADMIN) != 0
}
