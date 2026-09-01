package com.makd.afinity.data.models.auth

data class QuickConnectState(
    val code: String,
    val secret: String,
    val authenticated: Boolean = false,
)

enum class QuickConnectAuthorization {
    APPROVED,
    REFUSED,
    UNKNOWN_CODE,
    FAILED,
}
