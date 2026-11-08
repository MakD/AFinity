package com.makd.afinity.data.repository.subtitles

import java.util.Locale
import java.util.MissingResourceException

object SubtitleLanguageCodes {

    private val subtitleDbSpecial =
        mapOf(
            "pb" to "pt-BR",
            "zt" to "zh-Hant",
            "ze" to "zh",
            "ea" to "es-419",
            "sp" to "es-ES",
            "pm" to "pt-MZ",
            "zc" to "yue",
            "me" to "cnr",
            "at" to "ast",
            "ma" to "mni",
            "sb" to "wen",
            "tp" to "tok",
        )

    private val terminologyToBibliographic =
        mapOf(
            "sqi" to "alb",
            "hye" to "arm",
            "eus" to "baq",
            "mya" to "bur",
            "zho" to "chi",
            "ces" to "cze",
            "nld" to "dut",
            "fra" to "fre",
            "kat" to "geo",
            "deu" to "ger",
            "ell" to "gre",
            "isl" to "ice",
            "mkd" to "mac",
            "mri" to "mao",
            "msa" to "may",
            "fas" to "per",
            "ron" to "rum",
            "slk" to "slo",
            "bod" to "tib",
            "cym" to "wel",
        )

    private val bibliographicToTerminology =
        terminologyToBibliographic.entries.associate { (t, b) -> b to t }

    private val legacyIso1 = mapOf("iw" to "he", "in" to "id", "ji" to "yi")

    private val iso3ToIso1: Map<String, String> by lazy {
        Locale.getISOLanguages()
            .mapNotNull { two ->
                try {
                    Locale.forLanguageTag(two)
                        .isO3Language
                        .takeIf { it.isNotBlank() }
                        ?.let { it to (legacyIso1[two] ?: two) }
                } catch (_: MissingResourceException) {
                    null
                }
            }
            .toMap()
    }

    fun toLanguageTag(code: String): String {
        val lower = code.trim().lowercase()
        subtitleDbSpecial[lower]?.let {
            return it
        }
        if (lower.length == 3) {
            val terminology = bibliographicToTerminology[lower] ?: lower
            return iso3ToIso1[terminology] ?: terminology
        }
        return lower
    }

    fun toServer(code: String): String {
        val lower = code.trim().lowercase()
        if (lower.length == 3 && lower !in subtitleDbSpecial) {
            return terminologyToBibliographic[lower] ?: lower
        }
        val iso3 =
            try {
                Locale.forLanguageTag(toLanguageTag(lower)).isO3Language
            } catch (_: MissingResourceException) {
                ""
            }
        return iso3.takeIf { it.isNotBlank() }?.let { terminologyToBibliographic[it] ?: it }
            ?: lower
    }

    fun toSubtitleDb(code: String): String {
        val lower = code.trim().lowercase()
        if (lower in subtitleDbSpecial) return lower
        return when (val tag = toLanguageTag(lower)) {
            "pt-br" -> "pb"
            else -> tag.substringBefore('-')
        }
    }

    fun sameLanguage(a: String, b: String): Boolean =
        toServer(a).equals(toServer(b), ignoreCase = true)
}
