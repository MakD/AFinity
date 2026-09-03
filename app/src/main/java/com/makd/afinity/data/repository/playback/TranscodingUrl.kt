package com.makd.afinity.data.repository.playback

import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.TranscodeReason
import java.net.URLDecoder
import java.net.URLEncoder

object TranscodingUrl {

    const val NO_SUBTITLE = -1

    fun transcodeReasons(url: String): List<TranscodeReason> =
        queryValue(url, "TranscodeReasons")
            ?.split(',')
            ?.mapNotNull { TranscodeReason.fromNameOrNull(it.trim()) }
            .orEmpty()

    fun subtitleStreamIndex(url: String): Int? =
        queryValue(url, "SubtitleStreamIndex")?.toIntOrNull()

    fun subtitleMethod(url: String): SubtitleDeliveryMethod? =
        queryValue(url, "SubtitleMethod")?.let { SubtitleDeliveryMethod.fromNameOrNull(it) }

    fun burnedInSubtitleIndex(url: String): Int? {
        if (subtitleMethod(url) != SubtitleDeliveryMethod.ENCODE) return null
        return subtitleStreamIndex(url)?.takeIf { it >= 0 }
    }

    fun withSubtitleStreamIndex(url: String, index: Int?): String =
        withParam(url, "SubtitleStreamIndex", (index ?: NO_SUBTITLE).toString())

    fun withAudioStreamIndex(url: String, index: Int): String =
        withParam(url, "AudioStreamIndex", index.toString())

    fun withStartTimeTicks(url: String, ticks: Long): String =
        withParam(url, "StartTimeTicks", ticks.toString())

    private fun queryValue(url: String, name: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query
            .split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")
            ?.let { decode(it) }
    }

    private fun withParam(url: String, name: String, value: String): String {
        val path = url.substringBefore('?')
        val query = url.substringAfter('?', "")
        val encoded = "$name=${encode(value)}"
        if (query.isEmpty()) return "$path?$encoded"

        var replaced = false
        val pairs =
            query.split('&').map { pair ->
                if (pair.substringBefore('=') == name) {
                    replaced = true
                    encoded
                } else {
                    pair
                }
            }
        val merged = if (replaced) pairs else pairs + encoded
        return "$path?${merged.joinToString("&")}"
    }

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }

    private fun encode(value: String): String =
        try {
            URLEncoder.encode(value, Charsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
}
