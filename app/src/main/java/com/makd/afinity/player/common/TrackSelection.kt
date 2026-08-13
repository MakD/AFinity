package com.makd.afinity.player.common

import com.makd.afinity.data.models.media.AfinityMediaStream
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import java.util.Locale

data class TrackSelectionResult(val audioPosition: Int?, val subtitlePosition: Int)

object TrackSelection {

    const val NO_SUBTITLE = -1
    const val FOLLOW_SERVER_LANGUAGE = "server"

    private val BIBLIOGRAPHIC_TO_TERMINOLOGIC =
        mapOf(
            "alb" to "sqi",
            "arm" to "hye",
            "baq" to "eus",
            "bur" to "mya",
            "chi" to "zho",
            "cze" to "ces",
            "dut" to "nld",
            "fre" to "fra",
            "geo" to "kat",
            "ger" to "deu",
            "gre" to "ell",
            "ice" to "isl",
            "mac" to "mkd",
            "mao" to "mri",
            "may" to "msa",
            "per" to "fas",
            "rum" to "ron",
            "slo" to "slk",
            "tib" to "bod",
            "wel" to "cym",
        )

    private val UNKNOWN_LANGUAGES = setOf("", "und", "unknown", "none", "undefined", "zxx", "mis")

    private val TERMINOLOGIC_TO_BIBLIOGRAPHIC =
        BIBLIOGRAPHIC_TO_TERMINOLOGIC.entries.associate { (bibliographic, terminologic) ->
            terminologic to bibliographic
        }

    private val TERMINOLOGIC_TO_TWO_LETTER: Map<String, String> by lazy {
        Locale.getISOLanguages().associateBy { twoLetter ->
            runCatching { Locale.forLanguageTag(twoLetter).isO3Language }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() } ?: twoLetter
        }
    }

    fun languageAliases(code: String?): List<String> {
        val base = code.orEmpty().trim().lowercase(Locale.ROOT)
        val normalized = normalizeLanguage(base)
        if (normalized.isEmpty()) return emptyList()
        val aliases = linkedSetOf(normalized)
        TERMINOLOGIC_TO_BIBLIOGRAPHIC[normalized]?.let { aliases.add(it) }
        TERMINOLOGIC_TO_TWO_LETTER[normalized]?.let { aliases.add(it) }
        if (base.isNotEmpty()) aliases.add(base)
        return aliases.toList()
    }

    fun resolveSubtitleMode(
        override: String,
        serverMode: SubtitlePlaybackMode?,
    ): SubtitlePlaybackMode =
        override.takeIf { it.isNotBlank() }?.let { SubtitlePlaybackMode.fromNameOrNull(it) }
            ?: serverMode
            ?: SubtitlePlaybackMode.SMART

    fun usesHearingImpairedPreference(mode: SubtitlePlaybackMode): Boolean =
        mode == SubtitlePlaybackMode.ALWAYS || mode == SubtitlePlaybackMode.SMART

    fun normalizeLanguage(code: String?): String {
        val base =
            code.orEmpty().trim().substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        if (base in UNKNOWN_LANGUAGES) return ""
        return when (base.length) {
            2 ->
                runCatching { Locale.forLanguageTag(base).isO3Language }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() } ?: base
            3 -> BIBLIOGRAPHIC_TO_TERMINOLOGIC[base] ?: base
            else -> base
        }
    }

    fun select(
        audioStreams: List<AfinityMediaStream>,
        subtitleStreams: List<AfinityMediaStream>,
        subtitleMode: SubtitlePlaybackMode,
        preferredAudioLanguage: String,
        preferredSubtitleLanguage: String,
        requestedAudioStreamIndex: Int? = null,
        requestedSubtitleStreamIndex: Int? = null,
        serverDefaultAudioStreamIndex: Int? = null,
        serverDefaultSubtitleStreamIndex: Int? = null,
        playDefaultAudioTrack: Boolean = false,
        allowExternalAudio: Boolean = false,
        preferHearingImpaired: Boolean = false,
    ): TrackSelectionResult {
        val audioPosition =
            selectAudio(
                audioStreams = audioStreams,
                requestedStreamIndex = requestedAudioStreamIndex,
                preferredLanguage = preferredAudioLanguage,
                serverDefaultStreamIndex = serverDefaultAudioStreamIndex,
                playDefaultAudioTrack = playDefaultAudioTrack,
                allowExternal = allowExternalAudio,
            )

        val resolvedAudioLanguage =
            audioPosition?.let { audioStreams.getOrNull(it)?.language }
                ?: serverDefaultAudioStreamIndex?.let { index ->
                    audioStreams.firstOrNull { it.index == index }?.language
                }
                ?: audioStreams.firstOrNull { it.isDefault }?.language
                ?: audioStreams.firstOrNull()?.language
                ?: ""

        val subtitleLanguage = preferredSubtitleLanguage.ifBlank { preferredAudioLanguage }
        val normalizedSubtitleLanguage = normalizeLanguage(subtitleLanguage)
        val normalizedAudioLanguage = normalizeLanguage(resolvedAudioLanguage)

        val audioIsForeign =
            when {
                normalizedSubtitleLanguage.isEmpty() -> false
                normalizedAudioLanguage.isEmpty() -> true
                else -> normalizedAudioLanguage != normalizedSubtitleLanguage
            }

        val subtitlePosition =
            selectSubtitle(
                subtitleStreams = subtitleStreams,
                requestedStreamIndex = requestedSubtitleStreamIndex,
                mode = subtitleMode,
                preferredLanguage = subtitleLanguage,
                audioIsForeign = audioIsForeign,
                serverDefaultStreamIndex = serverDefaultSubtitleStreamIndex,
                preferHearingImpaired = preferHearingImpaired,
            )

        return TrackSelectionResult(audioPosition, subtitlePosition)
    }

    private fun selectAudio(
        audioStreams: List<AfinityMediaStream>,
        requestedStreamIndex: Int?,
        preferredLanguage: String,
        serverDefaultStreamIndex: Int?,
        playDefaultAudioTrack: Boolean,
        allowExternal: Boolean,
    ): Int? {
        val selectable = audioStreams.withIndex().filter { allowExternal || !it.value.isExternal }
        if (selectable.isEmpty()) return null

        if (requestedStreamIndex != null) {
            selectable
                .firstOrNull { it.value.index == requestedStreamIndex }
                ?.let {
                    return it.index
                }
        }

        val preferred = normalizeLanguage(preferredLanguage)
        if (preferred.isNotEmpty()) {
            selectable
                .filter { matches(it.value, preferred) }
                .minByOrNull { audioRank(it.value) }
                ?.let {
                    return it.index
                }
        }

        if (playDefaultAudioTrack && preferred.isEmpty()) {
            selectable
                .firstOrNull { it.value.isDefault }
                ?.let {
                    return it.index
                }
        }

        if (serverDefaultStreamIndex != null) {
            selectable
                .firstOrNull { it.value.index == serverDefaultStreamIndex }
                ?.let {
                    return it.index
                }
        }

        return selectable.minByOrNull { audioRank(it.value) }?.index
    }

    private fun audioRank(stream: AfinityMediaStream): Int =
        (if (stream.isDefault) 0 else 2) + (if (stream.isExternal) 1 else 0)

    private fun selectSubtitle(
        subtitleStreams: List<AfinityMediaStream>,
        requestedStreamIndex: Int?,
        mode: SubtitlePlaybackMode,
        preferredLanguage: String,
        audioIsForeign: Boolean,
        serverDefaultStreamIndex: Int?,
        preferHearingImpaired: Boolean,
    ): Int {
        if (requestedStreamIndex != null) {
            if (requestedStreamIndex < 0) return NO_SUBTITLE
            subtitleStreams
                .indexOfFirst { it.index == requestedStreamIndex }
                .takeIf { it >= 0 }
                ?.let {
                    return it
                }
        }

        if (subtitleStreams.isEmpty() || mode == SubtitlePlaybackMode.NONE) return NO_SUBTITLE

        val candidates = orderedCandidates(subtitleStreams)
        val preferred = normalizeLanguage(preferredLanguage)

        val chosen =
            when (mode) {
                SubtitlePlaybackMode.NONE -> null

                SubtitlePlaybackMode.DEFAULT ->
                    serverDefaultStreamIndex
                        ?.takeIf { it >= 0 }
                        ?.let { index -> candidates.firstOrNull { it.value.index == index } }
                        ?: candidates.firstOrNull {
                            (it.value.isDefault || it.value.isForced) &&
                                matches(it.value, preferred)
                        }
                        ?: candidates.firstOrNull { it.value.isDefault }
                        ?: candidates.firstOrNull { it.value.isForced }

                SubtitlePlaybackMode.ONLY_FORCED -> candidates.firstForced(preferred)

                SubtitlePlaybackMode.ALWAYS ->
                    candidates.firstFull(preferred, preferHearingImpaired)
                        ?: candidates.firstOrNull { matches(it.value, preferred) }
                        ?: candidates.firstOrNull { !it.value.isForced && it.value.isDefault }
                        ?: candidates.firstFull("", preferHearingImpaired)
                        ?: candidates.firstOrNull { it.value.isDefault || it.value.isForced }
                        ?: candidates.firstOrNull()

                SubtitlePlaybackMode.SMART ->
                    if (audioIsForeign) {
                        candidates.firstFull(preferred, preferHearingImpaired)
                            ?: candidates.firstOrNull { matches(it.value, preferred) }
                            ?: candidates.firstForced(preferred)
                    } else {
                        candidates.firstForced(preferred)
                    }
            }

        return chosen?.index ?: NO_SUBTITLE
    }

    private fun orderedCandidates(
        subtitleStreams: List<AfinityMediaStream>
    ): List<IndexedValue<AfinityMediaStream>> =
        subtitleStreams
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<AfinityMediaStream>> { it.value.isExternal }
                    .thenByDescending { codecPriority(it.value.codec) }
                    .thenBy { it.index }
            )

    private fun List<IndexedValue<AfinityMediaStream>>.firstFull(
        language: String,
        preferHearingImpaired: Boolean,
    ): IndexedValue<AfinityMediaStream>? = filter {
        !it.value.isForced && matches(it.value, language)
    }
        .sortedWith(
            compareByDescending<IndexedValue<AfinityMediaStream>> {
                    it.value.isHearingImpaired == preferHearingImpaired
                }
                .thenByDescending { it.value.isDefault }
        )
        .firstOrNull()

    private fun List<IndexedValue<AfinityMediaStream>>.firstForced(
        language: String
    ): IndexedValue<AfinityMediaStream>? =
        firstOrNull { it.value.isForced && matches(it.value, language) }
            ?: firstOrNull { it.value.isForced && it.value.isDefault }
            ?: firstOrNull { it.value.isForced }

    private fun codecPriority(codec: String): Int =
        when (codec.lowercase(Locale.ROOT)) {
            "ass",
            "ssa" -> 4
            "srt",
            "subrip" -> 3
            "pgs",
            "pgssub" -> 2
            else -> 1
        }

    private fun matches(stream: AfinityMediaStream, normalizedLanguage: String): Boolean =
        normalizedLanguage.isNotEmpty() && normalizeLanguage(stream.language) == normalizedLanguage
}
