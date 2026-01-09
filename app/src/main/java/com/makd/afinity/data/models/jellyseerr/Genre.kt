package com.makd.afinity.data.models.jellyseerr

import com.makd.afinity.util.BackdropTracker
import com.makd.afinity.util.GenreDuotoneColorGenerator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Genre(
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String
)

@Serializable
data class GenreSliderItem(
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String,
    @SerialName("backdrops")
    val backdrops: List<String>? = null
) {
    fun getBackdropUrl(baseUrl: String = "https://image.tmdb.org/t/p/w780"): String? {
        return backdrops?.firstOrNull()?.let { "$baseUrl$it" }
    }

    fun getDuotoneBackdropUrl(
        backdropTracker: BackdropTracker? = null,
        isMovie: Boolean = true
    ): String? {
        val selectedPath = if (backdropTracker != null) {
            backdropTracker.selectNextBackdrop(backdrops, id, isMovie)
        } else {
            backdrops?.firstOrNull()
        }

        return selectedPath?.let { path ->
            val baseUrl = GenreDuotoneColorGenerator.getDuotoneFilterUrl(id)
            "$baseUrl$path"
        }
    }
}

@Serializable
data class GenreListResponse(
    @SerialName("genres")
    val genres: List<Genre>
)

@Serializable
data class GenreSliderResponse(
    @SerialName("genres")
    val genres: List<GenreSliderItem>
)