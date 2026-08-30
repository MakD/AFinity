package com.makd.afinity.data.models.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.sdk.model.api.LyricLine
import org.jellyfin.sdk.model.api.LyricLineCue

private const val TICKS_PER_SECOND = 10_000_000.0

private val lyricJson = Json { ignoreUnknownKeys = true }

@Serializable
data class AfinityLyricCue(
    val startSeconds: Double,
    val endSeconds: Double?,
    val position: Int,
    val endPosition: Int,
)

@Serializable
data class AfinityLyricLine(
    val text: String,
    val startSeconds: Double,
    val cues: List<AfinityLyricCue> = emptyList(),
)

fun LyricLine.toAfinityLyricLine(): AfinityLyricLine? {
    val start = start ?: return null
    return AfinityLyricLine(
        text = text,
        startSeconds = start / TICKS_PER_SECOND,
        cues =
            cues.orEmpty()
                .mapNotNull { it.toAfinityLyricCue(text.length) }
                .sortedBy { it.startSeconds },
    )
}

private fun LyricLineCue.toAfinityLyricCue(textLength: Int): AfinityLyricCue? {
    val from = position.coerceIn(0, textLength)
    val to = endPosition.coerceIn(from, textLength)
    if (to <= from) return null
    return AfinityLyricCue(
        startSeconds = start / TICKS_PER_SECOND,
        endSeconds = end?.let { it / TICKS_PER_SECOND },
        position = from,
        endPosition = to,
    )
}

fun encodeLyricsJson(lines: List<AfinityLyricLine>): String = lyricJson.encodeToString(lines)

fun decodeLyricsJson(json: String): List<AfinityLyricLine>? =
    runCatching {
            val root = lyricJson.parseToJsonElement(json).jsonArray
            if (root.firstOrNull() is JsonArray) {
                root.mapNotNull { element ->
                    val pair = element.jsonArray
                    val text = pair.getOrNull(0)?.jsonPrimitive?.content ?: return@mapNotNull null
                    val seconds =
                        pair.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull()
                            ?: return@mapNotNull null
                    AfinityLyricLine(text = text, startSeconds = seconds)
                }
            } else {
                lyricJson.decodeFromJsonElement<List<AfinityLyricLine>>(root)
            }
        }
        .getOrNull()
