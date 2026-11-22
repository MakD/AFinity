package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.annotation.StringRes
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfSeries
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import kotlin.random.Random

enum class AbsItemSort(@StringRes val labelRes: Int, val books: Boolean, val podcasts: Boolean) {
    TITLE(R.string.sort_option_title, books = true, podcasts = true),
    AUTHOR(R.string.abs_sort_option_author, books = false, podcasts = true),
    AUTHOR_FIRST_LAST(R.string.abs_sort_option_author_first_last, books = true, podcasts = false),
    AUTHOR_LAST_FIRST(R.string.abs_sort_option_author_last_first, books = true, podcasts = false),
    PUBLISH_YEAR(R.string.abs_sort_option_publish_year, books = true, podcasts = false),
    ADDED(R.string.sort_option_date_added, books = true, podcasts = true),
    SIZE(R.string.abs_sort_option_size, books = true, podcasts = true),
    DURATION(R.string.abs_sort_option_duration, books = true, podcasts = false),
    EPISODES(R.string.abs_sort_option_episodes, books = false, podcasts = true),
    PROGRESS_UPDATED(R.string.abs_sort_option_progress_updated, books = true, podcasts = false),
    PROGRESS_STARTED(R.string.abs_sort_option_progress_started, books = true, podcasts = false),
    PROGRESS_FINISHED(R.string.abs_sort_option_progress_finished, books = true, podcasts = false),
    RANDOM(R.string.sort_option_random, books = true, podcasts = true);

    fun availableFor(isPodcast: Boolean): Boolean = if (isPodcast) podcasts else books

    companion object {
        fun optionsFor(isPodcast: Boolean): List<AbsItemSort> = entries.filter {
            it.availableFor(isPodcast)
        }
    }
}

enum class AbsSeriesSort(@StringRes val labelRes: Int) {
    NAME(R.string.abs_sort_option_name),
    BOOK_COUNT(R.string.abs_sort_option_book_count),
    ADDED(R.string.sort_option_date_added),
    LAST_BOOK_ADDED(R.string.abs_sort_option_last_book_added),
    LAST_BOOK_UPDATED(R.string.abs_sort_option_last_book_updated),
    TOTAL_DURATION(R.string.abs_sort_option_total_duration),
    RANDOM(R.string.sort_option_random),
}

data class AbsDisplay<T>(val items: List<T> = emptyList(), val orderVersion: Int = 0)

data class AbsSortState<T : Enum<T>>(
    val sort: T,
    val descending: Boolean = false,
    val randomSeed: Long = 0L,
) {
    fun encode(): String = "${sort.name}:${if (descending) 1 else 0}"
}

inline fun <reified T : Enum<T>> decodeAbsSort(raw: String?, fallback: T): AbsSortState<T> {
    val parts = raw?.split(":") ?: return AbsSortState(fallback)
    val sort = enumValues<T>().firstOrNull { it.name == parts.getOrNull(0) } ?: fallback
    return AbsSortState(
        sort,
        descending = parts.getOrNull(1) == "1",
        randomSeed = Random.nextLong(),
    )
}

enum class AbsProgressFilter(@StringRes val labelRes: Int) {
    FINISHED(R.string.abs_finished),
    IN_PROGRESS(R.string.abs_filter_in_progress),
    NOT_STARTED(R.string.abs_filter_not_started),
    NOT_FINISHED(R.string.abs_filter_not_finished);

    companion object {
        val withoutNotFinished: List<AbsProgressFilter> = listOf(FINISHED, IN_PROGRESS, NOT_STARTED)
    }
}

data class AbsFilters(
    val progress: Set<AbsProgressFilter> = emptySet(),
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val narrators: Set<String> = emptySet(),
    val publishers: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val decades: Set<Int> = emptySet(),
    val abridgedOnly: Boolean = false,
    val explicitOnly: Boolean = false,
) {
    val activeCount: Int
        get() =
            listOf(
                    progress,
                    genres,
                    tags,
                    series,
                    authors,
                    narrators,
                    publishers,
                    languages,
                    decades,
                )
                .count { it.isNotEmpty() } +
                (if (abridgedOnly) 1 else 0) +
                (if (explicitOnly) 1 else 0)

    val isActive: Boolean
        get() = activeCount > 0
}

data class AbsFilterOptions(
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val series: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val narrators: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val decades: List<Int> = emptyList(),
    val hasAbridged: Boolean = false,
    val hasExplicit: Boolean = false,
)

private enum class ProgressState {
    FINISHED,
    IN_PROGRESS,
    NOT_STARTED,
}

private val SERIES_SEQUENCE = Regex("""\s+#\S*$""")

private fun String?.splitNames(): List<String> =
    this?.split(", ")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

private fun LibraryItem.authorNames(): List<String> = media.metadata.authorName.splitNames()

private fun LibraryItem.narratorNames(): List<String> = media.metadata.narratorName.splitNames()

private fun LibraryItem.seriesNames(): List<String> =
    media.metadata.seriesName
        .splitNames()
        .map { it.replace(SERIES_SEQUENCE, "").trim() }
        .filter { it.isNotEmpty() }

private fun LibraryItem.decade(): Int? =
    media.metadata.publishedYear?.trim()?.take(4)?.toIntOrNull()?.let { it / 10 * 10 }

private fun MediaProgress?.state(): ProgressState =
    when {
        this == null -> ProgressState.NOT_STARTED
        isFinished -> ProgressState.FINISHED
        currentTime > 0 || (ebookProgress ?: 0.0) > 0 -> ProgressState.IN_PROGRESS
        else -> ProgressState.NOT_STARTED
    }

private fun LibraryItem.progressState(startedPodcastIds: Set<String>): ProgressState =
    if (mediaType == "podcast") {
        val total = media.numEpisodes ?: 0
        when {
            total > 0 && numEpisodesIncomplete == 0 -> ProgressState.FINISHED
            id in startedPodcastIds -> ProgressState.IN_PROGRESS
            else -> ProgressState.NOT_STARTED
        }
    } else {
        userMediaProgress.state()
    }

fun startedPodcastIds(progress: Map<String, MediaProgress>): Set<String> =
    progress.values
        .filter { it.episodeId != null && (it.isFinished || it.currentTime > 0) }
        .mapTo(HashSet()) { it.libraryItemId }

private fun ProgressState.matches(filter: AbsProgressFilter): Boolean =
    when (filter) {
        AbsProgressFilter.FINISHED -> this == ProgressState.FINISHED
        AbsProgressFilter.IN_PROGRESS -> this == ProgressState.IN_PROGRESS
        AbsProgressFilter.NOT_STARTED -> this == ProgressState.NOT_STARTED
        AbsProgressFilter.NOT_FINISHED -> this != ProgressState.FINISHED
    }

private fun Set<String>.matchesAny(values: Collection<String>): Boolean =
    isEmpty() || values.any { it in this }

private fun LibraryItem.matchesFacets(filters: AbsFilters): Boolean {
    val metadata = media.metadata
    return filters.genres.matchesAny(metadata.genres.orEmpty()) &&
        filters.tags.matchesAny(media.tags.orEmpty()) &&
        filters.series.matchesAny(seriesNames()) &&
        filters.authors.matchesAny(authorNames()) &&
        filters.narrators.matchesAny(narratorNames()) &&
        filters.publishers.matchesAny(listOfNotNull(metadata.publisher)) &&
        filters.languages.matchesAny(listOfNotNull(metadata.language)) &&
        (filters.decades.isEmpty() || decade() in filters.decades) &&
        (!filters.abridgedOnly || metadata.abridged == true) &&
        (!filters.explicitOnly || metadata.explicit == true)
}

fun List<LibraryItem>.filteredForAbs(
    filters: AbsFilters,
    startedPodcastIds: Set<String>,
): List<LibraryItem> {
    if (!filters.isActive) return this
    return filter { item ->
        item.matchesFacets(filters) &&
            (filters.progress.isEmpty() ||
                item.progressState(startedPodcastIds).let { state ->
                    filters.progress.any { state.matches(it) }
                })
    }
}

private fun seriesBookStates(series: AudiobookshelfSeries): List<ProgressState> =
    series.books.map { it.userMediaProgress.state() }

private fun AudiobookshelfSeries.matchesProgress(filter: AbsProgressFilter): Boolean {
    val states = seriesBookStates(this)
    return when (filter) {
        AbsProgressFilter.FINISHED ->
            states.isNotEmpty() && states.all { it == ProgressState.FINISHED }
        AbsProgressFilter.IN_PROGRESS -> states.any { it == ProgressState.IN_PROGRESS }
        AbsProgressFilter.NOT_STARTED -> states.all { it == ProgressState.NOT_STARTED }
        AbsProgressFilter.NOT_FINISHED -> states.any { it != ProgressState.FINISHED }
    }
}

fun List<AudiobookshelfSeries>.filteredSeriesForAbs(
    filters: AbsFilters,
): List<AudiobookshelfSeries> {
    if (!filters.isActive) return this
    val facetFilters = filters.copy(progress = emptySet())
    return filter { series ->
        (!facetFilters.isActive || series.books.any { it.matchesFacets(facetFilters) }) &&
            (filters.progress.isEmpty() || filters.progress.any { series.matchesProgress(it) })
    }
}

private fun caseInsensitiveSorted(values: Sequence<String>): List<String> =
    values.filter { it.isNotBlank() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).toList()

fun buildAbsFilterOptions(items: List<LibraryItem>): AbsFilterOptions {
    val seq = items.asSequence()
    return AbsFilterOptions(
        genres = caseInsensitiveSorted(seq.flatMap { it.media.metadata.genres.orEmpty() }),
        tags = caseInsensitiveSorted(seq.flatMap { it.media.tags.orEmpty() }),
        series = caseInsensitiveSorted(seq.flatMap { it.seriesNames() }),
        authors = caseInsensitiveSorted(seq.flatMap { it.authorNames() }),
        narrators = caseInsensitiveSorted(seq.flatMap { it.narratorNames() }),
        publishers = caseInsensitiveSorted(seq.mapNotNull { it.media.metadata.publisher }),
        languages = caseInsensitiveSorted(seq.mapNotNull { it.media.metadata.language }),
        decades = seq.mapNotNull { it.decade() }.distinct().sortedDescending().toList(),
        hasAbridged = items.any { it.media.metadata.abridged == true },
        hasExplicit = items.any { it.media.metadata.explicit == true },
    )
}

fun buildAbsSeriesFilterOptions(series: List<AudiobookshelfSeries>): AbsFilterOptions =
    buildAbsFilterOptions(series.flatMap { it.books }.distinctBy { it.id })
        .copy(series = emptyList(), decades = emptyList(), hasAbridged = false, hasExplicit = false)

private fun stringOrder(descending: Boolean): Comparator<String> =
    if (descending) String.CASE_INSENSITIVE_ORDER.reversed() else String.CASE_INSENSITIVE_ORDER

private fun <T, K : Comparable<K>> keyOrder(descending: Boolean, key: (T) -> K?): Comparator<T> {
    val order: Comparator<K> = if (descending) reverseOrder() else naturalOrder()
    return compareBy(nullsLast(order), key)
}

private fun <T> textOrder(descending: Boolean, key: (T) -> String?): Comparator<T> =
    compareBy(nullsLast(stringOrder(descending))) { item: T ->
        key(item)?.takeIf { it.isNotBlank() }
    }

private fun LibraryItem.sortTitle(ignorePrefix: Boolean): String =
    with(media.metadata) {
        (if (ignorePrefix) titleIgnorePrefix?.takeIf { it.isNotBlank() } ?: title else title)
            .orEmpty()
    }

private fun Long.positiveOrNull(): Long? = takeIf { it > 0 }

fun List<LibraryItem>.sortedForAbs(
    state: AbsSortState<AbsItemSort>,
    ignorePrefix: Boolean,
): List<LibraryItem> {
    if (state.sort == AbsItemSort.RANDOM) {
        return sortedBy { it.id }.shuffled(Random(state.randomSeed))
    }
    val desc = state.descending
    val byTitle = textOrder<LibraryItem>(desc) { it.sortTitle(ignorePrefix) }
    val primary: Comparator<LibraryItem> =
        when (state.sort) {
            AbsItemSort.TITLE -> byTitle
            AbsItemSort.AUTHOR ->
                textOrder(desc) { it.media.metadata.author ?: it.media.metadata.authorName }
            AbsItemSort.AUTHOR_FIRST_LAST -> textOrder(desc) { it.media.metadata.authorName }
            AbsItemSort.AUTHOR_LAST_FIRST -> textOrder(desc) { it.media.metadata.authorNameLF }
            AbsItemSort.PUBLISH_YEAR ->
                keyOrder(desc) { it.media.metadata.publishedYear?.trim()?.take(4)?.toIntOrNull() }
            AbsItemSort.ADDED -> keyOrder(desc) { it.addedAt }
            AbsItemSort.SIZE -> keyOrder(desc) { it.size }
            AbsItemSort.DURATION -> keyOrder(desc) { it.media.duration }
            AbsItemSort.EPISODES -> keyOrder(desc) { it.media.numEpisodes }
            AbsItemSort.PROGRESS_UPDATED ->
                keyOrder(desc) { it.userMediaProgress?.lastUpdate?.positiveOrNull() }
            AbsItemSort.PROGRESS_STARTED ->
                keyOrder(desc) { it.userMediaProgress?.startedAt?.positiveOrNull() }
            AbsItemSort.PROGRESS_FINISHED ->
                keyOrder(desc) { it.userMediaProgress?.finishedAt?.positiveOrNull() }
            AbsItemSort.RANDOM -> byTitle
        }
    return if (state.sort == AbsItemSort.TITLE) sortedWith(primary)
    else sortedWith(primary.then(textOrder<LibraryItem>(false) { it.sortTitle(ignorePrefix) }))
}

fun List<AudiobookshelfSeries>.sortedSeriesForAbs(
    state: AbsSortState<AbsSeriesSort>,
    ignorePrefix: Boolean,
): List<AudiobookshelfSeries> {
    if (state.sort == AbsSeriesSort.RANDOM) {
        return sortedBy { it.id }.shuffled(Random(state.randomSeed))
    }
    val desc = state.descending
    val byName =
        textOrder<AudiobookshelfSeries>(desc) {
            if (ignorePrefix) it.nameIgnorePrefix?.takeIf { n -> n.isNotBlank() } ?: it.name
            else it.name
        }
    val primary: Comparator<AudiobookshelfSeries> =
        when (state.sort) {
            AbsSeriesSort.NAME -> byName
            AbsSeriesSort.BOOK_COUNT -> keyOrder(desc) { it.books.size }
            AbsSeriesSort.ADDED -> keyOrder(desc) { it.addedAt }
            AbsSeriesSort.LAST_BOOK_ADDED ->
                keyOrder(desc) { series -> series.books.mapNotNull { it.addedAt }.maxOrNull() }
            AbsSeriesSort.LAST_BOOK_UPDATED ->
                keyOrder(desc) { series -> series.books.mapNotNull { it.updatedAt }.maxOrNull() }
            AbsSeriesSort.TOTAL_DURATION ->
                keyOrder(desc) { series -> series.books.sumOf { it.media.duration ?: 0.0 } }
            AbsSeriesSort.RANDOM -> byName
        }
    return if (state.sort == AbsSeriesSort.NAME) sortedWith(primary)
    else sortedWith(primary.then(textOrder<AudiobookshelfSeries>(false) { it.name }))
}
