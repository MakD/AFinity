package com.makd.afinity.data.models.audiobookshelf

enum class EpisodeSortKey(val param: String) {
    PUB_DATE("pub_date"),
    TITLE("title"),
    SEASON("season"),
    EPISODE("episode"),
    FILENAME("filename");

    companion object {
        fun fromParam(param: String): EpisodeSortKey? = entries.firstOrNull { it.param == param }
    }
}

data class EpisodeSort(val key: EpisodeSortKey, val ascending: Boolean) {
    val param: String
        get() = "${key.param}_${if (ascending) "asc" else "desc"}"

    fun ascendingOrder(): EpisodeSort = if (ascending) this else copy(ascending = true)

    companion object {
        val Default = EpisodeSort(EpisodeSortKey.PUB_DATE, ascending = false)

        fun parse(param: String?): EpisodeSort? {
            if (param.isNullOrBlank()) return null
            val ascending =
                when {
                    param.endsWith("_asc") -> true
                    param.endsWith("_desc") -> false
                    else -> return null
                }
            val key = EpisodeSortKey.fromParam(param.substringBeforeLast('_')) ?: return null
            return EpisodeSort(key, ascending)
        }
    }
}

data class PodcastUpNext(
    val resume: PodcastEpisode?,
    val nextUnplayed: PodcastEpisode?,
    val latest: PodcastEpisode?,
    val first: PodcastEpisode?,
)

object PodcastEpisodeOrder {

    private val numberOrText = Regex("\\d+|\\D+")

    val naturalOrder: Comparator<String> = Comparator { a, b ->
        val aParts = numberOrText.findAll(a.trim()).map { it.value }.toList()
        val bParts = numberOrText.findAll(b.trim()).map { it.value }.toList()
        for (i in 0 until minOf(aParts.size, bParts.size)) {
            val ap = aParts[i]
            val bp = bParts[i]
            val aNum = ap.toBigIntegerOrNull()
            val bNum = bp.toBigIntegerOrNull()
            val cmp =
                if (aNum != null && bNum != null) aNum.compareTo(bNum)
                else ap.compareTo(bp, ignoreCase = true)
            if (cmp != 0) return@Comparator cmp
        }
        aParts.size - bParts.size
    }

    private val byPublished = compareBy<PodcastEpisode> { it.publishedAt ?: 0L }
    private val bySeason =
        compareBy(naturalOrder) { episode: PodcastEpisode -> episode.season ?: "" }
    private val byEpisode =
        compareBy(naturalOrder) { episode: PodcastEpisode -> episode.episode ?: "" }
    private val byTitle = compareBy(naturalOrder) { episode: PodcastEpisode -> episode.title }
    private val byFilename =
        compareBy(naturalOrder) { episode: PodcastEpisode ->
            episode.audioFile?.metadata?.filename ?: ""
        }
    private val byId = compareBy<PodcastEpisode> { it.id }

    fun listeningComparator(key: EpisodeSortKey): Comparator<PodcastEpisode> =
        when (key) {
            EpisodeSortKey.PUB_DATE ->
                byPublished.then(bySeason).then(byEpisode).then(byTitle).then(byId)

            EpisodeSortKey.TITLE -> byTitle.then(byPublished).then(byId)
            EpisodeSortKey.SEASON ->
                bySeason.then(byEpisode).then(byPublished).then(byTitle).then(byId)

            EpisodeSortKey.EPISODE -> byEpisode.then(byPublished).then(byTitle).then(byId)
            EpisodeSortKey.FILENAME -> byFilename.then(byPublished).then(byId)
        }

    fun sorted(episodes: List<PodcastEpisode>, sort: EpisodeSort): List<PodcastEpisode> {
        val ascending = episodes.sortedWith(listeningComparator(sort.key))
        return if (sort.ascending) ascending else ascending.asReversed()
    }

    fun isTrailer(episode: PodcastEpisode): Boolean {
        val type = episode.episodeType?.trim()?.lowercase()
        return type == TYPE_TRAILER ||
            (type.isNullOrEmpty() && episode.title.contains(TYPE_TRAILER, ignoreCase = true))
    }

    fun isMainEpisode(episode: PodcastEpisode): Boolean =
        !isTrailer(episode) && episode.episodeType?.trim()?.lowercase() != TYPE_BONUS

    fun upNext(
        episodes: List<PodcastEpisode>,
        progress: Map<String, MediaProgress>,
        sort: EpisodeSort,
        isSerial: Boolean,
    ): PodcastUpNext {
        if (episodes.isEmpty()) return PodcastUpNext(null, null, null, null)
        val byIdMap = episodes.associateBy { it.id }
        fun isFinished(episode: PodcastEpisode) = progress[episode.id]?.isFinished == true

        val resume =
            progress.values
                .filter { !it.isFinished && it.currentTime > 0 && it.episodeId in byIdMap }
                .maxByOrNull { it.lastUpdate }
                ?.let { byIdMap[it.episodeId] }

        val listeningOrder = episodes.sortedWith(listeningComparator(sort.key))
        val mainEpisodes = listeningOrder.filter { isMainEpisode(it) }.ifEmpty { listeningOrder }
        val byDate = episodes.sortedWith(listeningComparator(EpisodeSortKey.PUB_DATE))

        val nextUnplayed =
            if (isSerial) mainEpisodes.firstOrNull { !isFinished(it) }
            else byDate.lastOrNull { !isFinished(it) }

        return PodcastUpNext(
            resume = resume,
            nextUnplayed = nextUnplayed,
            latest = byDate.last(),
            first = mainEpisodes.first(),
        )
    }

    private const val TYPE_TRAILER = "trailer"
    private const val TYPE_BONUS = "bonus"
}
