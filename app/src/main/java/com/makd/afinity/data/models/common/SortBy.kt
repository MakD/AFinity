package com.makd.afinity.data.models.common

import androidx.annotation.StringRes
import com.makd.afinity.R

enum class SortBy(val sortString: String, @param:StringRes val labelRes: Int) {
    NAME("SortName", R.string.sort_option_title),
    IMDB_RATING("CommunityRating", R.string.sort_option_imdb),
    PARENTAL_RATING("CriticRating", R.string.sort_option_parental),
    DATE_ADDED("DateCreated", R.string.sort_option_date_added),
    DATE_PLAYED("DatePlayed", R.string.sort_option_date_played),
    RELEASE_DATE("PremiereDate", R.string.sort_option_release_date),
    SERIES_DATE_PLAYED("SeriesDatePlayed", R.string.sort_option_series_date_played),
    DATE_LAST_CONTENT_ADDED("DateLastContentAdded", R.string.sort_option_date_last_content_added),
    RANDOM("Random", R.string.sort_option_random);

    companion object {
        val defaultValue = NAME

        fun fromString(string: String): SortBy {
            return try {
                valueOf(string)
            } catch (_: IllegalArgumentException) {
                defaultValue
            }
        }
    }
}
