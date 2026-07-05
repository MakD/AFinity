package com.makd.afinity.util

private fun extractHost(url: String): String {
    val authority =
        url.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringAfterLast("@")
    return if (authority.startsWith("[")) {
        authority.substringAfter("[").substringBefore("]")
    } else {
        authority.substringBefore(":")
    }
}

fun isLocalAddress(url: String): Boolean {
    val host = extractHost(url)
    return host == "localhost" ||
        host.endsWith(".local") ||
        host.startsWith("127.") ||
        host.startsWith("169.254.") ||
        host.startsWith("192.168.") ||
        host.startsWith("10.") ||
        host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..+")) ||
        host == "::1" ||
        host.startsWith("fe80:") ||
        host.startsWith("fc") && host.contains(":") ||
        host.startsWith("fd") && host.contains(":")
}

fun isTailscaleAddress(url: String): Boolean {
    val host = extractHost(url)
    if (host.endsWith(".ts.net")) return true
    if (!host.startsWith("100.")) return false
    val secondOctet = host.removePrefix("100.").substringBefore(".").toIntOrNull() ?: return false
    return secondOctet in 64..127
}

fun isInsecurePublicUrl(url: String): Boolean {
    val trimmedUrl = url.trim().lowercase()
    if (trimmedUrl.startsWith("https://")) return false
    if (!trimmedUrl.startsWith("http://")) return false
    return !isLocalAddress(trimmedUrl) && !isTailscaleAddress(trimmedUrl)
}