package com.makd.afinity.data.models.jellyseerr

object Permissions {
    const val NONE = 0
    const val ADMIN = 2
    const val MANAGE_USERS = 8
    const val MANAGE_REQUESTS = 16
    const val REQUEST = 32
    const val AUTO_APPROVE = 128
    const val AUTO_APPROVE_MOVIE = 256
    const val AUTO_APPROVE_TV = 512
    const val REQUEST_4K = 1024
    const val MANAGE_ISSUES = 2048
    const val REQUEST_VIEW = 4096
    const val AUTO_APPROVE_4K = 8192
    const val AUTO_APPROVE_4K_MOVIE = 16384
    const val AUTO_APPROVE_4K_TV = 32768
    const val REQUEST_ADVANCED = 65536
    const val MANAGE_SETTINGS = 131072
}

fun JellyseerrUser.hasPermission(permission: Int): Boolean {
    return (this.permissions and permission) == permission
}

fun JellyseerrUser.isAdmin(): Boolean {
    return hasPermission(Permissions.ADMIN)
}