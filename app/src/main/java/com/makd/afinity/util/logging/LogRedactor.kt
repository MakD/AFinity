package com.makd.afinity.util.logging

object LogRedactor {

    private const val MASK = "[REDACTED]"

    private val jsonCredentialFields =
        Regex(
            "\"(token|accessToken|refreshToken|access_token|refresh_token|apiKey|api_key|password|sessionSecret)\"\\s*:\\s*\"[^\"]*\"",
            RegexOption.IGNORE_CASE,
        )

    private val queryCredentials =
        Regex(
            "([?&](?:api_key|apikey|token|access_token|refresh_token|X-Emby-Token)=)[^&\\s\"]+",
            RegexOption.IGNORE_CASE,
        )

    private val bearerHeader =
        Regex("(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}", RegexOption.IGNORE_CASE)

    private val cookieValues =
        Regex("((?:connect\\.sid|XSRF-TOKEN|jellyfin_session)=)[^;,\\s\"]+", RegexOption.IGNORE_CASE)

    private val jsonWebToken = Regex("eyJ[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?")

    fun redact(input: String): String {
        if (input.isEmpty()) return input
        return input
            .replace(jsonCredentialFields) { "\"${it.groupValues[1]}\":\"$MASK\"" }
            .replace(queryCredentials) { "${it.groupValues[1]}$MASK" }
            .replace(bearerHeader) { "${it.groupValues[1]}$MASK" }
            .replace(cookieValues) { "${it.groupValues[1]}$MASK" }
            .replace(jsonWebToken, MASK)
    }
}