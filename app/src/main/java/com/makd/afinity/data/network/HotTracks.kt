package com.makd.afinity.data.network

import com.makd.afinity.data.models.lastfm.LastFmTrackPopularity
import java.text.Normalizer

object HotTracks {
    private const val MAX_HOT = 3
    private const val MIN_MEDIAN_RATIO = 2.0
    private const val ARTIST_HITS = 10

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val PART = Regex("\\bpt\\b\\.?")
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    private val BRACKETED = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
    private val DASH_SUFFIX = Regex("\\s+-\\s+.*$")

    data class Match(val key: String, val listeners: Long)

    fun index(tracks: List<LastFmTrackPopularity>): Map<String, Long> {
        val index = HashMap<String, Long>()
        for ((name, listeners) in tracks) {
            val key = strictKey(name)
            if (key.isNotEmpty()) index.putIfAbsent(key, listeners)
        }
        return index
    }

    fun match(title: String, index: Map<String, Long>): Match? {
        val strict = strictKey(title)
        index[strict]?.let {
            return Match(strict, it)
        }
        val loose = looseKey(title)
        return index[loose]?.let { Match(loose, it) }
    }

    fun <T> hot(tracks: List<T>, title: (T) -> String, index: Map<String, Long>): Set<T> {
        if (index.isEmpty()) return emptySet()
        val matches = tracks.associateWith { match(title(it), index) }
        val byKey = LinkedHashMap<String, Long>()
        for (track in tracks) {
            val key = matches[track]?.key ?: strictKey(title(track))
            byKey[key] = matches[track]?.listeners ?: 0L
        }
        val values = byKey.values.sorted()
        if (values.isEmpty()) return emptySet()
        val median =
            if (values.size % 2 == 1) values[values.size / 2].toDouble()
            else (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0
        val hotKeys =
            byKey.entries
                .asSequence()
                .sortedByDescending { it.value }
                .take(MAX_HOT)
                .filter { it.value > 0 && it.value >= MIN_MEDIAN_RATIO * median }
                .map { it.key }
                .toSet()
        return tracks.filter { matches[it]?.key in hotKeys }.toSet()
    }

    fun <T> hits(
        tracks: List<T>,
        title: (T) -> String,
        index: Map<String, Long>,
        top: Int = ARTIST_HITS,
    ): Set<T> {
        if (index.isEmpty()) return emptySet()
        val topKeys =
            index.entries
                .asSequence()
                .filter { it.value > 0 }
                .sortedByDescending { it.value }
                .take(top)
                .map { it.key }
                .toSet()
        return tracks.filter { match(title(it), index)?.key in topKeys }.toSet()
    }

    fun <T> rank(tracks: List<T>, title: (T) -> String, index: Map<String, Long>): List<T> {
        if (index.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        return tracks
            .asSequence()
            .mapNotNull { track -> match(title(track), index)?.let { track to it } }
            .filter { it.second.listeners > 0 }
            .sortedByDescending { it.second.listeners }
            .filter { seen.add(it.second.key) }
            .map { it.first }
            .toList()
    }

    fun strictKey(title: String): String {
        val decomposed = Normalizer.normalize(title, Normalizer.Form.NFKD)
        return decomposed
            .replace(COMBINING_MARKS, "")
            .lowercase()
            .replace("&", "and")
            .replace(PART, "part")
            .replace(NON_ALPHANUMERIC, "")
    }

    private fun looseKey(title: String): String {
        val decomposed = Normalizer.normalize(title, Normalizer.Form.NFKD).lowercase()
        return strictKey(decomposed.replace(BRACKETED, "").replace(DASH_SUFFIX, ""))
    }
}
