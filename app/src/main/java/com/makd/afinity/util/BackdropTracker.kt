package com.makd.afinity.util

import kotlin.math.absoluteValue

class BackdropTracker {
    private val lock = Any()

    private val usedBackdrops = mutableSetOf<String>()
    private val genreBackdropAssignments = mutableMapOf<GenreKey, String>()

    private data class GenreKey(val genreId: Int, val isMovie: Boolean)

    fun selectNextBackdrop(
        availableBackdrops: List<String>?,
        genreId: Int,
        isMovie: Boolean = true,
    ): String? {
        synchronized(lock) {
            if (availableBackdrops.isNullOrEmpty()) return null

            val key = GenreKey(genreId, isMovie)

            genreBackdropAssignments[key]?.let {
                return it
            }

            val unusedBackdrop = availableBackdrops.firstOrNull { it !in usedBackdrops }

            val selectedBackdrop =
                if (unusedBackdrop != null) {
                    usedBackdrops.add(unusedBackdrop)
                    unusedBackdrop
                } else {
                    val index =
                        (genreId.absoluteValue + if (isMovie) 0 else 1000) % availableBackdrops.size
                    val backdrop = availableBackdrops[index]
                    usedBackdrops.add(backdrop)
                    backdrop
                }

            genreBackdropAssignments[key] = selectedBackdrop
            return selectedBackdrop
        }
    }

    fun reset() {
        synchronized(lock) {
            usedBackdrops.clear()
            genreBackdropAssignments.clear()
        }
    }

    fun getUsedCount(): Int {
        synchronized(lock) {
            return usedBackdrops.size
        }
    }
}
