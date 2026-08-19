package com.makd.afinity.util

private val SensitiveQueryParams = Regex("(?i)(api_?key|token|accessToken)=[^&\\s]+")

fun redactSecrets(value: String): String = value.replace(SensitiveQueryParams, "$1=[REDACTED]")

fun redactUrl(url: String?): String = url?.let { redactSecrets(it) } ?: "null"