package com.makd.afinity.data.models.auth

data class QuickConnectState(
    val code: String,
    val secret: String,
    val authenticated: Boolean = false
)