package com.makd.afinity.data.network

object UrlCandidates {

    val JELLYFIN_HTTPS_PORTS = listOf(8920, 8096)
    val JELLYFIN_HTTP_PORTS = listOf(8096)

    val JELLYSEERR_PORTS = listOf(5055)

    val AUDIOBOOKSHELF_PORTS = listOf(13378)

    fun generate(input: String, httpsPorts: List<Int>, httpPorts: List<Int>): List<String> {
        val clean = input.trim().trimEnd('/')
        if (clean.isBlank()) return emptyList()

        val parts = parseAddressParts(clean)
        val host = parts.host
        val path = parts.path
        val hasScheme = parts.scheme != null

        return when {
            parts.port != null && hasScheme -> listOf(clean)
            parts.port != null -> listOf("https://$clean", "http://$clean")
            parts.scheme == "https" ->
                (listOf(clean) + httpsPorts.map { "https://$host:$it$path" }).distinct()
            parts.scheme == "http" ->
                (listOf(clean) + httpPorts.map { "http://$host:$it$path" }).distinct()
            else ->
                buildList {
                        add("https://$host$path")
                        httpsPorts.forEach { add("https://$host:$it$path") }
                        httpPorts.forEach { add("http://$host:$it$path") }
                        add("http://$host$path")
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
