package com.makd.afinity.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    LIBRARIES(
        route = "libraries",
        title = "Libraries",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder
    ),
    FAVORITES(
        route = "favorites",
        title = "Favorites",
        selectedIcon = Icons.Filled.Favorite,
        unselectedIcon = Icons.Outlined.FavoriteBorder
    );

    companion object {
        const val LIBRARY_CONTENT_ROUTE = "library_content/{libraryId}/{libraryName}"
        const val ITEM_DETAIL_ROUTE = "item_detail/{itemId}"
        const val EPISODE_LIST_ROUTE = "episodes/{seasonId}/{seasonName}"
        //const val PLAYER_ROUTE = "player/{itemId}/{mediaSourceId}?audioStreamIndex={audioStreamIndex}&subtitleStreamIndex={subtitleStreamIndex}&startPositionMs={startPositionMs}"
        const val PERSON_ROUTE = "person/{personId}"
        const val SEARCH_ROUTE = "search"
        const val GENRE_RESULTS_ROUTE = "genre_results/{genre}"
        const val SETTINGS_ROUTE = "settings"

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
        fun createItemDetailRoute(itemId: String): String {
            return "item_detail/$itemId"
        }
        fun createEpisodeListRoute(seasonId: String, seasonName: String): String {
            return "episodes/$seasonId/${seasonName.replace("/", "%2F")}"
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
    }
}