package com.makd.afinity.data.models

import com.makd.afinity.R

enum class HomeRow(
    val key: String,
    val labelRes: Int,
    val defaultVisible: Boolean = true,
    val mandatory: Boolean = false,
) {
    HERO_CAROUSEL("hero_carousel", R.string.home_row_hero_carousel),
    LIBRARIES("libraries_section", R.string.home_row_libraries, mandatory = true),
    CONTINUE_WATCHING("continue_watching", R.string.home_row_continue_watching, mandatory = true),
    NEXT_UP("next_up", R.string.home_row_next_up),
    LATEST_MOVIES("latest_movies", R.string.home_row_latest_movies, mandatory = true),
    LATEST_TV("latest_tv", R.string.home_row_latest_tv, mandatory = true),
    UPCOMING_EPISODES("upcoming_episodes", R.string.home_row_upcoming),
    CRITICS_CHOICE("critics_choice", R.string.home_row_critics_choice),
    WATCH_AGAIN("watch_again", R.string.home_row_watch_again),
    POPULAR_STUDIOS("popular_studios", R.string.home_row_popular_studios);

    companion object {
        fun fromKey(key: String): HomeRow? = entries.firstOrNull { it.key == key }

        val configurable: List<HomeRow> = entries.filterNot { it.mandatory }

        val alwaysOn: List<HomeRow> = entries.filter { it.mandatory }
    }
}