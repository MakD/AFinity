package com.makd.afinity.data.models.media

import java.time.LocalDateTime
import java.util.UUID

data class ContinueWatchingOrder(
    val resumedAt: Map<UUID, LocalDateTime> = emptyMap(),
    val seriesPlayedAt: Map<UUID, LocalDateTime> = emptyMap(),
) {
    fun interleave(
        continueWatching: List<AfinityItem>,
        nextUp: List<AfinityEpisode>,
    ): List<AfinityItem> {
        val resumingIds = continueWatching.mapTo(HashSet()) { it.id }
        return merge(continueWatching, nextUp.filter { it.id !in resumingIds })
    }

    private fun merge(
        continueWatching: List<AfinityItem>,
        nextUp: List<AfinityEpisode>,
    ): List<AfinityItem> {
        if (nextUp.isEmpty()) return continueWatching
        if (continueWatching.isEmpty()) return nextUp

        val resumeKeys =
            continueWatching.mapTo(ArrayList()) { resumedAt[it.id] ?: LocalDateTime.MAX }
        for (index in resumeKeys.lastIndex - 1 downTo 0) {
            resumeKeys[index] = maxOf(resumeKeys[index], resumeKeys[index + 1])
        }

        val nextUpKeys =
            nextUp.mapTo(ArrayList()) { seriesPlayedAt[it.seriesId] ?: LocalDateTime.MIN }
        for (index in 1..nextUpKeys.lastIndex) {
            nextUpKeys[index] = minOf(nextUpKeys[index], nextUpKeys[index - 1])
        }

        val merged = ArrayList<AfinityItem>(continueWatching.size + nextUp.size)
        var resumeIndex = 0
        var nextUpIndex = 0
        while (resumeIndex < continueWatching.size && nextUpIndex < nextUp.size) {
            if (resumeKeys[resumeIndex] >= nextUpKeys[nextUpIndex]) {
                merged.add(continueWatching[resumeIndex++])
            } else {
                merged.add(nextUp[nextUpIndex++])
            }
        }
        while (resumeIndex < continueWatching.size) merged.add(continueWatching[resumeIndex++])
        while (nextUpIndex < nextUp.size) merged.add(nextUp[nextUpIndex++])
        return merged
    }
}
