package com.makd.afinity.data.network

data class AddressParts(
    val scheme: String?,
    val host: String,
    val path: String,
    val port: Int?,
) {
    val hostWithPath: String
        get() = host + path
}

fun parseAddressParts(input: String): AddressParts {
    val clean = input.trim().trimEnd('/')
    val hasScheme = clean.startsWith("http://") || clean.startsWith("https://")
    val scheme = if (hasScheme) clean.substring(0, clean.indexOf("://")) else null
    val remainder = if (hasScheme) clean.substring(clean.indexOf("://") + 3) else clean

    val pathStart = remainder.indexOf('/')
    val authority = if (pathStart < 0) remainder else remainder.substring(0, pathStart)
    val path = if (pathStart < 0) "" else remainder.substring(pathStart)

    if (authority.startsWith("[")) {
        val close = authority.indexOf(']')
        if (close < 0) return AddressParts(scheme, authority, path, null)
        val host = authority.substring(0, close + 1)
        val port = authority.substring(close + 1).removePrefix(":").toIntOrNull()
        return AddressParts(scheme, host, path, port)
    }

    val colon = authority.lastIndexOf(':')
    if (colon < 0) return AddressParts(scheme, authority, path, null)
    val port = authority.substring(colon + 1).toIntOrNull()
        ?: return AddressParts(scheme, authority, path, null)
    return AddressParts(scheme, authority.substring(0, colon), path, port)
}
