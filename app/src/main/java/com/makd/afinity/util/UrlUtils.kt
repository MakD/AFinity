package com.makd.afinity.util

fun isLocalAddress(url: String): Boolean {
    val host =
        url.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore(":")
            .substringBefore("/")
    return host == "localhost" ||
        host == "127.0.0.1" ||
        host.startsWith("192.168.") ||
        host.startsWith("10.") ||
        host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..+"))
}

fun isTailscaleAddress(url: String): Boolean {
    val host =
        url.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore(":")
            .substringBefore("/")
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
