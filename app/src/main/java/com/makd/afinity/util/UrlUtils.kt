package com.makd.afinity.util

fun isInsecurePublicUrl(url: String): Boolean {
    val trimmedUrl = url.trim().lowercase()

    if (trimmedUrl.startsWith("https://")) return false
    if (!trimmedUrl.startsWith("http://")) return false

    val host = trimmedUrl.removePrefix("http://").substringBefore(":")

    val isLocal =
        host == "localhost" ||
            host == "127.0.0.1" ||
            host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..+"))

    return !isLocal
}
