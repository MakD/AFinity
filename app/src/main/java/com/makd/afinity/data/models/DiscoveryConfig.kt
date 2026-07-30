package com.makd.afinity.data.models

import com.makd.afinity.R
import kotlin.math.roundToInt

enum class DiscoveryDensity(val key: String, val factor: Float, val labelRes: Int) {
    LIGHT("light", 0.5f, R.string.discovery_density_light),
    BALANCED("balanced", 1.0f, R.string.discovery_density_balanced),
    FULL("full", 1.5f, R.string.discovery_density_full);

    companion object {
        val default = BALANCED

        fun fromKey(key: String?): DiscoveryDensity =
            entries.firstOrNull { it.key == key } ?: default
    }
}

enum class DiscoverySection(
    val key: String,
    val labelRes: Int,
    val defaultCount: Int,
    val ceiling: Int,
) {
    GENRES("discovery_genres", R.string.discovery_section_genres, 15, 30),
    SPOTLIGHTS("discovery_spotlights", R.string.discovery_section_spotlights, 20, 32),
    STARRING("discovery_starring", R.string.discovery_section_starring, 15, 25),
    DIRECTED_BY("discovery_directed_by", R.string.discovery_section_directed_by, 8, 15),
    WRITTEN_BY("discovery_written_by", R.string.discovery_section_written_by, 7, 15),
    BECAUSE_YOU_WATCHED(
        "discovery_because_watched",
        R.string.discovery_section_because_watched,
        7,
        10,
    ),
    BECAUSE_YOU_LIKED("discovery_because_liked", R.string.discovery_section_because_liked, 3, 10),
    ACTOR_FROM_MOVIE("discovery_actor_from", R.string.discovery_section_actor_from, 3, 6),
    DIRECTOR_FROM_MOVIE("discovery_director_from", R.string.discovery_section_director_from, 2, 6),
    WRITER_FROM_MOVIE("discovery_writer_from", R.string.discovery_section_writer_from, 2, 6),
    POPULAR_STUDIOS("discovery_popular_studios", R.string.discovery_section_popular_studios, 1, 1);

    companion object {
        fun fromKey(key: String): DiscoverySection? = entries.firstOrNull { it.key == key }

        const val DENSITY_KEY = "__discovery_density"
    }
}

data class DiscoveryConfig(
    val density: DiscoveryDensity = DiscoveryDensity.default,
    val disabled: Set<DiscoverySection> = emptySet(),
    val overrides: Map<DiscoverySection, Int> = emptyMap(),
) {
    fun isEnabled(section: DiscoverySection): Boolean = section !in disabled

    fun countFor(section: DiscoverySection): Int {
        if (!isEnabled(section)) return 0
        overrides[section]?.let {
            return it.coerceIn(0, section.ceiling)
        }
        return (section.defaultCount * density.factor).roundToInt().coerceIn(1, section.ceiling)
    }
}