package com.makd.afinity.util

import kotlin.math.absoluteValue

object GenreDuotoneColorGenerator {

    private val COLOR_PALETTES =
        listOf(
            Pair("991B1B", "FCA5A5"),
            Pair("480c8b", "a96bef"),
            Pair("92400E", "FCD34D"),
            Pair("1F2937", "2864d2"),
            Pair("065F46", "6EE7B7"),
            Pair("9D174D", "F9A8D4"),
            Pair("777e0d", "e4ed55"),
            Pair("1F2937", "60A5FA"),
            Pair("1F2937", "D1D5DB"),
            Pair("032541", "01b4e4"),
            Pair("5B21B6", "C4B5FD"),
            Pair("1F2937", "F87171"),
            Pair("552c01", "d47c1d"),
            Pair("132440", "5A7ACD"),
        )

    fun getColorsForGenre(genreId: Int): Pair<String, String> {
        val hash = (genreId * 31 + genreId.toString().hashCode()).absoluteValue
        val index = hash % COLOR_PALETTES.size
        return COLOR_PALETTES[index]
    }

    fun getDuotoneFilterUrl(genreId: Int, width: String = "w1280"): String {
        val (primary, secondary) = getColorsForGenre(genreId)
        return "https://image.tmdb.org/t/p/${width}_filter(duotone,$primary,$secondary)"
    }
}
