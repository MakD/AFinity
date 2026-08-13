package com.makd.afinity.ui.components.ratings

import androidx.annotation.DrawableRes
import com.makd.afinity.R
import com.makd.afinity.data.models.mdblist.MdbListRating
import java.util.Locale

data class MdbListRatingDisplay(
    val sourceName: String,
    @param:DrawableRes val iconRes: Int?,
    val score: String,
    val subtext: String,
)

const val RATING_SOURCE_COMMUNITY = "community"
const val RATING_SOURCE_IMDB = "imdb"
const val RATING_SOURCE_TOMATOES = "tomatoes"

fun communityRatingOf(rating: Float?): MdbListRating? =
    rating?.let { MdbListRating(source = RATING_SOURCE_COMMUNITY, value = it.toDouble()) }

fun criticRatingOf(rating: Float?): MdbListRating? =
    rating?.let { MdbListRating(source = RATING_SOURCE_TOMATOES, value = it.toDouble()) }

fun List<MdbListRating>.excludingSupersededBy(criticRating: Float?): List<MdbListRating> =
    if (criticRating == null) this
    else filter { !it.source.equals(RATING_SOURCE_TOMATOES, ignoreCase = true) }

fun MdbListRating.displayPriority(): Int =
    when (source.lowercase()) {
        RATING_SOURCE_IMDB -> 0
        RATING_SOURCE_TOMATOES -> 1
        "popcorn" -> 2
        else -> 3
    }

fun MdbListRating.toDisplay(): MdbListRatingDisplay? {
    val sourceLower = source.lowercase()
    val rawValue =
        if (sourceLower == "metacriticuser") {
            score ?: value?.times(10.0) ?: return null
        } else {
            value ?: return null
        }

    val formattedScore =
        when {
            sourceLower == "metacriticuser" -> String.format(Locale.US, "%.1f", rawValue / 10.0)
            sourceLower == RATING_SOURCE_COMMUNITY || sourceLower == RATING_SOURCE_IMDB ->
                String.format(Locale.US, "%.1f", rawValue)
            rawValue % 1.0 == 0.0 -> rawValue.toInt().toString()
            else -> rawValue.toString()
        }

    val isPercentage = sourceLower in listOf("trakt", "tmdb", "popcorn", RATING_SOURCE_TOMATOES)

    val iconRes =
        when (sourceLower) {
            RATING_SOURCE_COMMUNITY -> R.drawable.ic_community_rating
            RATING_SOURCE_IMDB -> R.drawable.ic_imdb_logo
            RATING_SOURCE_TOMATOES ->
                if (rawValue > 60.0) R.drawable.ic_rotten_tomato_fresh
                else R.drawable.ic_rotten_tomato_rotten
            "trakt" -> R.drawable.ic_trakt
            "tmdb" -> R.drawable.ic_tmdb
            "letterboxd" -> R.drawable.ic_letterboxd
            "popcorn" ->
                if (rawValue >= 60.0) R.drawable.ic_rt_fresh_popcorn
                else R.drawable.ic_rt_stale_popcorn
            "metacritic" ->
                when {
                    rawValue >= 75.0 -> R.drawable.ic_metacritic_green
                    rawValue >= 50.0 -> R.drawable.ic_metacritic_yellow
                    else -> R.drawable.ic_metacritic_red
                }
            "metacriticuser" ->
                when {
                    rawValue >= 75.0 -> R.drawable.ic_metacritic_user_green
                    rawValue >= 50.0 -> R.drawable.ic_metacritic_user_yellow
                    else -> R.drawable.ic_metacritic_user_red
                }
            "rogerebert" -> R.drawable.ic_ebert
            "myanimelist" -> R.drawable.ic_mal
            else -> null
        }

    val subtext =
        when (sourceLower) {
            RATING_SOURCE_COMMUNITY -> "/ 10"
            RATING_SOURCE_IMDB -> "/ 10"
            RATING_SOURCE_TOMATOES -> if (rawValue > 60.0) "Fresh" else "Rotten"
            "popcorn" -> if (rawValue >= 60.0) "Hot" else "Stale"
            "metacritic" -> "/ 100"
            "metacriticuser" -> "/ 10"
            "letterboxd" -> "/ 5"
            "rogerebert" -> "/ 4"
            "myanimelist" -> "/ 10"
            "trakt",
            "tmdb" -> "Score"
            else -> "/ 10"
        }

    val sourceName =
        when (sourceLower) {
            RATING_SOURCE_COMMUNITY -> "Community"
            RATING_SOURCE_IMDB -> "IMDb"
            RATING_SOURCE_TOMATOES -> "Rotten Tomatoes"
            else -> source.replaceFirstChar { it.uppercase() }
        }

    return MdbListRatingDisplay(
        sourceName = sourceName,
        iconRes = iconRes,
        score = if (isPercentage) "$formattedScore%" else formattedScore,
        subtext = subtext,
    )
}