package com.makd.afinity.data.network

import java.net.URI

object UrlCandidates {

    val JELLYFIN_HTTPS_PORTS = listOf(8096, 8920)
    val JELLYFIN_HTTP_PORTS = listOf(8096)

    val JELLYSEERR_PORTS = listOf(5055)

    val AUDIOBOOKSHELF_PORTS = listOf(13378)

    fun generate(input: String, httpsPorts: List<Int>, httpPorts: List<Int>): List<String> {
        val clean = input.trim().removeSuffix("/")
        if (clean.isBlank()) return emptyList()

        val hasScheme = clean.startsWith("http://") || clean.startsWith("https://")
        val withScheme = if (hasScheme) clean else "http://$clean"
        val uri = runCatching { URI(withScheme) }.getOrNull()
        val host = uri?.host?.takeIf { it.isNotBlank() } ?: clean
        val port = uri?.port ?: -1
        val scheme = if (hasScheme) uri?.scheme else null

        return when {
            hasScheme && port != -1 -> listOf(clean)
            !hasScheme && port != -1 -> listOf("https://$clean", "http://$clean")
            hasScheme && scheme == "https" ->
                (listOf(clean) + httpsPorts.map { "https://$host:$it" }).distinct()
            hasScheme && scheme == "http" ->
                (listOf(clean) + httpPorts.map { "http://$host:$it" }).distinct()
            else ->
                buildList {
                        add("https://$host")
                        httpsPorts.forEach { add("https://$host:$it") }
                        httpPorts.forEach { add("http://$host:$it") }
                        add("http://$host")
                    }
                    .distinct()
        }
    }

    fun jellyfin(input: String): List<String> =
        generate(input, JELLYFIN_HTTPS_PORTS, JELLYFIN_HTTP_PORTS)

    fun jellyseerr(input: String): List<String> =
        generate(input, JELLYSEERR_PORTS, JELLYSEERR_PORTS)

    fun audiobookshelf(input: String): List<String> =
        generate(input, AUDIOBOOKSHELF_PORTS, AUDIOBOOKSHELF_PORTS)
}