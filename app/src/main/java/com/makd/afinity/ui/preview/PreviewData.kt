package com.makd.afinity.ui.preview

import androidx.core.net.toUri
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import java.time.LocalDateTime
import java.util.UUID

object PreviewData {

    const val SHORT_TITLE = "Dune"
    const val LONG_TITLE = "The Lord of the Rings: The Fellowship of the Ring (Extended Edition)"

    private const val TICKS_PER_MINUTE = 600_000_000L

    private fun id(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    private fun images(seed: String): AfinityImages =
        AfinityImages(
            primary = "preview://primary/$seed".toUri(),
            backdrop = "preview://backdrop/$seed".toUri(),
            thumb = "preview://thumb/$seed".toUri(),
        )

    fun movie(
        name: String = SHORT_TITLE,
        year: Int? = 2021,
        rating: Float? = 8.0f,
        criticRating: Float? = 83f,
        played: Boolean = false,
        progress: Float = 0f,
    ): AfinityMovie {
        val runtime = 155 * TICKS_PER_MINUTE
        return AfinityMovie(
            id = id("movie-$name"),
            name = name,
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            played = played,
            favorite = false,
            liked = false,
            canPlay = true,
            canDownload = true,
            runtimeTicks = runtime,
            playbackPositionTicks = (runtime * progress).toLong(),
            premiereDate = null,
            dateCreated = null,
            people = emptyList(),
            genres = listOf("Science Fiction", "Adventure"),
            communityRating = rating,
            officialRating = "PG-13",
            criticRating = criticRating,
            status = "Released",
            productionYear = year,
            endDate = null,
            trailer = null,
            tagline = null,
            images = images("movie-$name"),
            chapters = emptyList(),
            trickplayInfo = null,
            providerIds = null,
            externalUrls = null,
        )
    }

    fun show(
        name: String = "Severance",
        unplayed: Int? = 8,
        episodeCount: Int? = 19,
        played: Boolean = false,
    ): AfinityShow =
        AfinityShow(
            id = id("show-$name-$unplayed"),
            name = name,
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            seasons = emptyList(),
            played = played,
            favorite = false,
            liked = false,
            canPlay = true,
            canDownload = true,
            unplayedItemCount = unplayed,
            genres = listOf("Drama", "Mystery"),
            people = emptyList(),
            runtimeTicks = 0L,
            communityRating = 8.7f,
            officialRating = "TV-MA",
            status = "Continuing",
            productionYear = 2022,
            premiereDate = null,
            dateCreated = null,
            dateLastContentAdded = null,
            endDate = null,
            trailer = null,
            tagline = null,
            seasonCount = 2,
            episodeCount = episodeCount,
            images = images("show-$name-$unplayed"),
            providerIds = null,
            externalUrls = null,
        )

    fun season(name: String = "Season 1", unplayed: Int? = 12): AfinitySeason =
        AfinitySeason(
            id = id("season-$name-$unplayed"),
            name = name,
            seriesId = id("show-Severance"),
            seriesName = "Severance",
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            indexNumber = 1,
            episodes = emptyList(),
            episodeCount = 12,
            productionYear = 2022,
            premiereDate = null,
            people = emptyList(),
            played = false,
            favorite = false,
            liked = false,
            canPlay = true,
            canDownload = true,
            unplayedItemCount = unplayed,
            images = images("season-$name-$unplayed"),
            providerIds = null,
            externalUrls = null,
        )

    fun episode(
        name: String = "Good News About Hell",
        seriesName: String = "Severance",
        season: Int = 1,
        index: Int = 1,
        progress: Float = 0.4f,
        played: Boolean = false,
        missing: Boolean = false,
        premiereDate: LocalDateTime? = null,
    ): AfinityEpisode {
        val runtime = 57 * TICKS_PER_MINUTE
        return AfinityEpisode(
            id = id("episode-$seriesName-$season-$index-$name"),
            name = name,
            originalTitle = null,
            overview = "",
            indexNumber = index,
            indexNumberEnd = null,
            parentIndexNumber = season,
            sources = emptyList(),
            played = played,
            favorite = false,
            liked = false,
            canPlay = !missing,
            canDownload = !missing,
            runtimeTicks = runtime,
            playbackPositionTicks = (runtime * progress).toLong(),
            premiereDate = premiereDate,
            seriesName = seriesName,
            seriesId = id("show-$seriesName"),
            seriesLogo = null,
            seriesLogoBlurHash = null,
            seasonId = id("season-$seriesName-$season"),
            communityRating = 8.1f,
            people = emptyList(),
            missing = missing,
            images = images("episode-$seriesName-$season-$index"),
            chapters = emptyList(),
            trickplayInfo = null,
            providerIds = null,
            externalUrls = null,
        )
    }

    fun boxSet(name: String = "The Matrix Collection", count: Int = 4): AfinityBoxSet =
        AfinityBoxSet(
            id = id("boxset-$name"),
            name = name,
            itemCount = count,
            images = images("boxset-$name"),
            providerIds = null,
            externalUrls = null,
        )

    val posterRow: List<AfinityItem>
        get() =
            listOf(
                movie(),
                movie(name = LONG_TITLE, year = 2001, rating = 8.9f, criticRating = 91f),
                movie(name = "Oppenheimer", played = true),
                show(),
                show(name = "One Piece", unplayed = 140, episodeCount = 1100),
                season(),
                boxSet(),
            )

    val landscapeRow: List<AfinityItem>
        get() =
            listOf(
                episode(),
                episode(
                    name = "The You You Are, and the Long Name That Keeps Going On",
                    index = 4,
                    progress = 0.85f,
                ),
                movie(name = "Interstellar", year = 2014, progress = 0.2f),
                episode(
                    name = "Cold Harbor",
                    index = 10,
                    progress = 0f,
                    missing = true,
                    premiereDate = LocalDateTime.now().plusDays(7),
                ),
                episode(name = "Hide and Seek", index = 3, progress = 0f, missing = true),
            )
}
