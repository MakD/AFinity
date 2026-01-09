package com.makd.afinity.navigation

import com.makd.afinity.R

enum class Destination(
    val route: String,
    val title: String,
    val selectedIconRes: Int,
    val unselectedIconRes: Int
) {
    HOME(
        route = "home",
        title = "Home",
        selectedIconRes = R.drawable.ic_home_filled,
        unselectedIconRes = R.drawable.ic_home
    ),
    LIBRARIES(
        route = "libraries",
        title = "Libraries",
        selectedIconRes = R.drawable.ic_video_library_filled,
        unselectedIconRes = R.drawable.ic_video_library
    ),
    FAVORITES(
        route = "favorites",
        title = "Favorites",
        selectedIconRes = R.drawable.ic_favorite_filled,
        unselectedIconRes = R.drawable.ic_favorite
    ),
    WATCHLIST(
        route = "watchlist",
        title = "Watchlist",
        selectedIconRes = R.drawable.ic_bookmarks_filled,
        unselectedIconRes = R.drawable.ic_bookmarks
    ),
    REQUESTS(
        route = "requests",
        title = "Requests",
        selectedIconRes = R.drawable.ic_plus_filled,
        unselectedIconRes = R.drawable.ic_plus
    );

    companion object {
        const val LIBRARY_CONTENT_ROUTE = "library_content/{libraryId}/{libraryName}"
        const val STUDIO_CONTENT_ROUTE = "studio_content/{studioName}"
        const val ITEM_DETAIL_ROUTE = "item_detail/{itemId}"
        const val EPISODE_LIST_ROUTE = "episodes/{seasonId}/{seasonName}"
        const val PERSON_ROUTE = "person/{personId}"
        const val SEARCH_ROUTE = "search"
        const val GENRE_RESULTS_ROUTE = "genre_results/{genre}"
        const val SETTINGS_ROUTE = "settings"
        const val DOWNLOAD_SETTINGS_ROUTE = "download_settings"
        const val PLAYER_OPTIONS_ROUTE = "player_options"
        const val APPEARANCE_OPTIONS_ROUTE = "appearance_options"
        const val LICENSES_ROUTE = "licenses"
        const val FILTERED_MEDIA_ROUTE = "filtered_media/{filterType}/{filterId}/{filterName}"

        fun createPersonRoute(personId: String): String {
            return "person/$personId"
        }

        fun createPlayerRoute(
            itemId: String,
            mediaSourceId: String,
            audioStreamIndex: Int? = null,
            subtitleStreamIndex: Int? = null,
            startPositionMs: Long = 0L
        ): String {
            var route = "player/$itemId/$mediaSourceId"
            val params = mutableListOf<String>()

            if (audioStreamIndex != null) params.add("audioStreamIndex=$audioStreamIndex")
            if (subtitleStreamIndex != null) params.add("subtitleStreamIndex=$subtitleStreamIndex")
            if (startPositionMs > 0) params.add("startPositionMs=$startPositionMs")

            if (params.isNotEmpty()) {
                route += "?" + params.joinToString("&")
            }

            return route
        }

        fun createLibraryContentRoute(libraryId: String, libraryName: String): String {
            return "library_content/$libraryId/${libraryName.replace("/", "%2F")}"
        }

        fun createStudioContentRoute(studioName: String): String {
            return "studio_content/${studioName.replace("/", "%2F")}"
        }

        fun createItemDetailRoute(itemId: String): String {
            return "item_detail/$itemId"
        }

        fun createEpisodeListRoute(seasonId: String, seasonName: String): String {
            return createItemDetailRoute(seasonId)
        }

        fun createSearchRoute(): String {
            return SEARCH_ROUTE
        }

        fun createGenreResultsRoute(genre: String): String {
            return "genre_results/${genre.replace("/", "%2F")}"
        }

        fun createSettingsRoute(): String {
            return SETTINGS_ROUTE
        }

        fun createDownloadSettingsRoute(): String {
            return DOWNLOAD_SETTINGS_ROUTE
        }

        fun createPlayerOptionsRoute(): String {
            return PLAYER_OPTIONS_ROUTE
        }

        fun createAppearanceOptionsRoute(): String {
            return APPEARANCE_OPTIONS_ROUTE
        }

        fun createLicensesRoute(): String {
            return LICENSES_ROUTE
        }

        fun createFilteredMediaRoute(filterType: String, filterId: Int, filterName: String): String {
            return "filtered_media/$filterType/$filterId/${filterName.replace("/", "%2F")}"
        }
    }
}