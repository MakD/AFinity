package com.makd.afinity.data.models

import com.makd.afinity.R

enum class HomeRow(val key: String, val labelRes: Int, val defaultVisible: Boolean = true) {
    HERO_CAROUSEL("hero_carousel", R.string.home_row_hero_carousel),
    LIBRARIES("libraries_section", R.string.home_row_libraries),
    CONTINUE_WATCHING("continue_watching", R.string.home_row_continue_watching),
    NEXT_UP("next_up", R.string.home_row_next_up),
    LATEST_MOVIES("latest_movies", R.string.home_row_latest_movies),
    LATEST_TV("latest_tv", R.string.home_row_latest_tv),
    UPCOMING_EPISODES("upcoming_episodes", R.string.home_row_upcoming),
    CRITICS_CHOICE("critics_choice", R.string.home_row_critics_choice),
    WATCH_AGAIN("watch_again", R.string.home_row_watch_again);

    companion object {
        fun fromKey(key: String): HomeRow? = entries.firstOrNull { it.key == key }
    }
}