package com.makd.afinity.navigation

import androidx.annotation.StringRes
import com.makd.afinity.R
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityCollection
import java.net.URLEncoder

enum class Destination(
    val route: String,
    @param:StringRes val titleRes: Int,
    val selectedIconRes: Int,
    val unselectedIconRes: Int,
) {
    HOME(
        route = "home",
        titleRes = R.string.nav_home,
        selectedIconRes = R.drawable.ic_home_filled,
        unselectedIconRes = R.drawable.ic_home,
    ),
    LIBRARIES(
        route = "libraries",
        titleRes = R.string.libraries_title,
        selectedIconRes = R.drawable.ic_folder_filled,
        unselectedIconRes = R.drawable.ic_folder,
    ),
    LIVE_TV(
        route = "live_tv",
        titleRes = R.string.livetv_title,
        selectedIconRes = R.drawable.ic_live_tv_filled_nav,
        unselectedIconRes = R.drawable.ic_live_tv_nav,
    ),
    FAVORITES(
        route = "favorites",
        titleRes = R.string.favorites_title,
        selectedIconRes = R.drawable.ic_favorite_filled,
        unselectedIconRes = R.drawable.ic_favorite,
    ),
    WATCHLIST(
        route = "watchlist",
        titleRes = R.string.watchlist_title,
        selectedIconRes = R.drawable.ic_bookmarks_filled,
        unselectedIconRes = R.drawable.ic_bookmarks,
    ),
    REQUESTS(
        route = "requests",
        titleRes = R.string.requests_title,
        selectedIconRes = R.drawable.ic_plus_filled,
        unselectedIconRes = R.drawable.ic_plus,
    ),
    AUDIOBOOKS(
        route = "audiobookshelf/libraries",
        titleRes = R.string.nav_audiobooks,
        selectedIconRes = R.drawable.ic_books_filled,
        unselectedIconRes = R.drawable.ic_books,
    );

    companion object {
        const val LIBRARY_CONTENT_ROUTE = "library_content/{libraryId}/{libraryName}"
        const val STUDIO_CONTENT_ROUTE = "studio_content/{studioName}"
        const val FOLDER_CONTENT_ROUTE = "folder_content/{folderId}/{folderName}"
        const val CUSTOM_SECTION_CONTENT_ROUTE = "custom_section_content/{sectionId}"
        const val ITEM_DETAIL_ROUTE = "item_detail/{itemId}?itemType={itemType}&seriesId={seriesId}"
        const val EPISODE_LIST_ROUTE = "episodes/{seasonId}/{seasonName}"
        const val PERSON_ROUTE = "person/{personId}"
        const val SEARCH_ROUTE = "search"
        const val GENRE_RESULTS_ROUTE = "genre_results/{genre}"
        const val SETTINGS_ROUTE = "settings"
        const val DOWNLOAD_SETTINGS_ROUTE = "download_settings"
        const val STORAGE_SETTINGS_ROUTE = "storage_settings"
        const val PLAYER_OPTIONS_ROUTE = "player_options"
        const val APPEARANCE_OPTIONS_ROUTE = "appearance_options"
        const val CUSTOM_SECTIONS_ROUTE = "custom_sections"
        const val LICENSES_ROUTE = "licenses"
        const val LOGS_ROUTE = "logs"

        fun createLogsRoute(): String {
            return LOGS_ROUTE
        }

        const val LIVE_TV_GUIDE_ROUTE = "live_tv/guide"
        const val LIVE_TV_CHANNELS_ROUTE = "live_tv/channels"
        const val LIVE_TV_RECORDINGS_ROUTE = "live_tv/recordings"
        const val LIVE_TV_PROGRAM_ROUTE = "live_tv/program/{programId}"

        fun createLiveTvProgramRoute(programId: String): String {
            return "live_tv/program/$programId"
        }

        const val LIVE_TV_CHANNEL_ROUTE = "live_tv/channel/{channelId}"

        fun createLiveTvChannelRoute(channelId: String): String {
            return "live_tv/channel/$channelId"
        }

        const val FILTERED_MEDIA_ROUTE = "filtered_media/{filterType}/{filterId}/{filterName}"
        const val FAVORITES_CATEGORY_ROUTE = "favorites_category/{category}"
        const val WATCHLIST_CATEGORY_ROUTE = "watchlist_category/{category}"
        const val DOWNLOADED_CATEGORY_ROUTE = "downloaded_category/{category}"
        const val SEERR_MEDIA_ROUTE =
            "seerr_media/{seerrMediaType}/{seerrTmdbId}?seerrTitle={seerrTitle}&seerrBackdrop={seerrBackdrop}&seerrPoster={seerrPoster}"
        const val SERVER_MANAGEMENT_ROUTE = "server_management"
        const val ADD_EDIT_SERVER_ROUTE = "add_edit_server?serverId={serverId}"

        const val SPLASH_ROUTE = "splash"
        const val LOGIN_ROUTE = "login?serverUrl={serverUrl}"
        const val SERVICES_HUB_ROUTE = "services_hub?entry={entry}"

        const val EDIT_METADATA_ROUTE = "admin/edit_metadata/{itemId}"
        const val IDENTIFY_ITEM_ROUTE = "admin/identify/{itemId}/{itemType}"
        const val EDIT_IMAGES_ROUTE = "admin/edit_images/{itemId}"
        const val ADMIN_SERVER_ROUTE = "admin/server"
        const val ADMIN_OVERVIEW_ROUTE = "admin/server/overview"
        const val ADMIN_TASKS_ROUTE = "admin/server/tasks"
        const val ADMIN_TASK_DETAIL_ROUTE = "admin/server/tasks/{taskId}"
        const val ADMIN_USERS_ROUTE = "admin/server/users"
        const val ADMIN_USER_DETAIL_ROUTE = "admin/server/users/{userId}"
        const val ADMIN_USER_PERMISSIONS_ROUTE = "admin/server/users/{userId}/permissions"
        const val ADMIN_USER_PARENTAL_ROUTE = "admin/server/users/{userId}/parental"
        const val ADMIN_LIBRARIES_ROUTE = "admin/server/libraries"
        const val ADMIN_LIBRARY_ADD_ROUTE = "admin/server/library-add"
        const val ADMIN_LIBRARY_DETAIL_ROUTE = "admin/server/libraries/{libraryName}"
        const val ADMIN_DEVICES_ROUTE = "admin/server/devices"
        const val ADMIN_ACTIVITY_ROUTE = "admin/server/activity?filter={filter}"
        const val ADMIN_PLUGINS_ROUTE = "admin/server/plugins"
        const val ADMIN_PLUGIN_DETAIL_ROUTE = "admin/server/plugins/detail/{pluginId}"
        const val ADMIN_PLUGIN_REPOSITORIES_ROUTE = "admin/server/plugins/repositories"
        const val ADMIN_API_KEYS_ROUTE = "admin/server/keys"
        const val ADMIN_BACKUPS_ROUTE = "admin/server/backups"
        const val ADMIN_SETTINGS_ROUTE = "admin/server/settings/{formId}"

        const val ADMIN_LOGS_ROUTE = "admin/server/logs"
        const val ADMIN_LOG_VIEWER_ROUTE = "admin/server/logs/{name}"

        fun createAdminActivityRoute(filter: String): String =
            "admin/server/activity?filter=$filter"

        fun createAdminSettingsRoute(formId: String): String = "admin/server/settings/$formId"

        fun createAdminLibraryDetailRoute(libraryName: String): String =
            "admin/server/libraries/${android.net.Uri.encode(libraryName)}"

        fun createAdminUserDetailRoute(userId: String): String = "admin/server/users/$userId"

        fun createAdminUserPermissionsRoute(userId: String): String =
            "admin/server/users/$userId/permissions"

        fun createAdminUserParentalRoute(userId: String): String =
            "admin/server/users/$userId/parental"

        fun createAdminTaskDetailRoute(taskId: String): String =
            "admin/server/tasks/${android.net.Uri.encode(taskId)}"

        fun createAdminPluginDetailRoute(pluginId: String): String =
            "admin/server/plugins/detail/$pluginId"

        fun createAdminLogViewerRoute(name: String): String =
            "admin/server/logs/${android.net.Uri.encode(name)}"

        fun createEditMetadataRoute(itemId: String): String = "admin/edit_metadata/$itemId"

        fun createIdentifyItemRoute(itemId: String, itemType: String): String =
            "admin/identify/$itemId/$itemType"

        fun createEditImagesRoute(itemId: String): String = "admin/edit_images/$itemId"

        const val MUSIC_LIBRARY_ROUTE = "music/library/{libraryId}/{libraryName}"
        const val MUSIC_BROWSE_ROUTE = "music/library/{libraryId}/{libraryName}/browse/{tab}"
        const val MUSIC_ALBUM_ROUTE = "music/album/{albumId}"
        const val MUSIC_ARTIST_ROUTE = "music/artist/{artistId}"
        const val PLAYLIST_ROUTE = "playlist/{playlistId}?audioOnly={audioOnly}"
        const val MUSIC_PLAYER_ROUTE = "music/player"
        const val MUSIC_GENRE_ROUTE =
            "music/genre/{genreName}?imageUrl={imageUrl}&genreId={genreId}&libraryId={libraryId}"

        fun createMusicGenreRoute(
            genreName: String,
            imageUrl: String? = null,
            genreId: java.util.UUID? = null,
            libraryId: java.util.UUID? = null,
        ): String {
            val encodedName = genreName.replace("/", "%2F")
            val params = buildList {
                if (imageUrl != null) add("imageUrl=${URLEncoder.encode(imageUrl, "UTF-8")}")
                if (genreId != null) add("genreId=$genreId")
                if (libraryId != null) add("libraryId=$libraryId")
            }
            return if (params.isEmpty()) "music/genre/$encodedName"
            else "music/genre/$encodedName?${params.joinToString("&")}"
        }

        fun createMusicLibraryRoute(libraryId: String, libraryName: String): String =
            "music/library/$libraryId/${libraryName.replace("/", "%2F")}"

        fun createMusicBrowseRoute(libraryId: String, libraryName: String, tab: String): String =
            "music/library/$libraryId/${libraryName.replace("/", "%2F")}/browse/$tab"

        fun createMusicAlbumRoute(albumId: String): String = "music/album/$albumId"

        fun createMusicArtistRoute(artistId: String): String = "music/artist/$artistId"

        fun createPlaylistRoute(playlistId: String, audioOnly: Boolean = false): String =
            "playlist/$playlistId?audioOnly=$audioOnly"

        const val AUDIOBOOKSHELF_LOGIN_ROUTE = "audiobookshelf/login"
        const val AUDIOBOOKSHELF_LIBRARIES_ROUTE = "audiobookshelf/libraries"
        const val AUDIOBOOKSHELF_SERIES_LIST_ROUTE = "audiobookshelf/series-browse"
        const val AUDIOBOOKSHELF_LIBRARY_ROUTE = "audiobookshelf/library/{libraryId}/{libraryName}"

        const val AUDIOBOOKSHELF_ITEM_ROUTE = "audiobookshelf/item/{itemId}"
        const val AUDIOBOOKSHELF_SERIES_ROUTE =
            "audiobookshelf/series/{seriesId}/{libraryId}/{seriesName}"
        const val AUDIOBOOKSHELF_GENRE_RESULTS_ROUTE = "audiobookshelf/genre/{genre}"
        const val AUDIOBOOKSHELF_PLAYER_ROUTE =
            "audiobookshelf/player/{itemId}?episodeId={episodeId}&startPosition={startPosition}&episodeSort={episodeSort}&includePlayed={includePlayed}"

        fun createAudiobookshelfLoginRoute(): String {
            return AUDIOBOOKSHELF_LOGIN_ROUTE
        }

        fun createAudiobookshelfLibrariesRoute(): String {
            return AUDIOBOOKSHELF_LIBRARIES_ROUTE
        }

        fun createAudiobookshelfSeriesListRoute(): String {
            return AUDIOBOOKSHELF_SERIES_LIST_ROUTE
        }

        fun createAudiobookshelfLibraryRoute(libraryId: String, libraryName: String): String {
            return "audiobookshelf/library/$libraryId/${libraryName.replace("/", "%2F")}"
        }

        fun createAudiobookshelfItemRoute(itemId: String): String {
            return "audiobookshelf/item/$itemId"
        }

        fun createAudiobookshelfSeriesRoute(
            seriesId: String,
            libraryId: String,
            seriesName: String,
        ): String {
            return "audiobookshelf/series/$seriesId/$libraryId/${seriesName.replace("/", "%2F")}"
        }

        fun createAudiobookshelfGenreResultsRoute(genre: String): String {
            return "audiobookshelf/genre/${genre.replace("/", "%2F")}"
        }

        fun createAudiobookshelfPlayerRoute(
            itemId: String,
            episodeId: String? = null,
            startPosition: Double? = null,
            episodeSort: String? = null,
            includePlayed: Boolean = false,
        ): String {
            val params = buildList {
                if (episodeId != null) add("episodeId=$episodeId")
                if (startPosition != null) add("startPosition=$startPosition")
                if (episodeSort != null) add("episodeSort=$episodeSort")
                if (includePlayed) add("includePlayed=true")
            }
            return if (params.isNotEmpty()) {
                "audiobookshelf/player/$itemId?${params.joinToString("&")}"
            } else {
                "audiobookshelf/player/$itemId"
            }
        }

        fun createPersonRoute(personId: String): String {
            return "person/$personId"
        }

        fun createPlayerRoute(
            itemId: String,
            mediaSourceId: String,
            audioStreamIndex: Int? = null,
            subtitleStreamIndex: Int? = null,
            startPositionMs: Long = 0L,
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

        fun createLibraryRoute(library: AfinityCollection): String =
            if (library.type == CollectionType.Music) {
                createMusicLibraryRoute(
                    libraryId = library.id.toString(),
                    libraryName = library.name,
                )
            } else {
                createLibraryContentRoute(
                    libraryId = library.id.toString(),
                    libraryName = library.name,
                )
            }

        fun createStudioContentRoute(studioName: String): String {
            return "studio_content/${studioName.replace("/", "%2F")}"
        }

        fun createFolderContentRoute(folderId: String, folderName: String): String {
            return "folder_content/$folderId/${URLEncoder.encode(folderName, "UTF-8")}"
        }

        fun createCustomSectionContentRoute(sectionId: String): String {
            return "custom_section_content/$sectionId"
        }

        fun createItemDetailRoute(
            itemId: String,
            itemType: String? = null,
            seriesId: String? = null,
        ): String {
            val params = buildList {
                if (itemType != null) add("itemType=$itemType")
                if (seriesId != null) add("seriesId=$seriesId")
            }
            return if (params.isNotEmpty()) {
                "item_detail/$itemId?${params.joinToString("&")}"
            } else {
                "item_detail/$itemId"
            }
        }

        fun createEpisodeListRoute(seasonId: String, seasonName: String, seriesId: String): String {
            return createItemDetailRoute(
                itemId = seasonId,
                itemType = "Season",
                seriesId = seriesId,
            )
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

        fun createCustomSectionsRoute(): String {
            return CUSTOM_SECTIONS_ROUTE
        }

        fun createLicensesRoute(): String {
            return LICENSES_ROUTE
        }

        fun createFilteredMediaRoute(
            filterType: String,
            filterId: Int,
            filterName: String,
        ): String {
            return "filtered_media/$filterType/$filterId/${filterName.replace("/", "%2F")}"
        }

        fun createFavoritesCategoryRoute(category: String): String {
            return "favorites_category/$category"
        }

        fun createDownloadedCategoryRoute(category: String): String {
            return "downloaded_category/$category"
        }

        fun createWatchlistCategoryRoute(category: String): String {
            return "watchlist_category/$category"
        }

        fun createSeerrMediaRoute(
            mediaType: String,
            tmdbId: Int,
            title: String? = null,
            backdropUrl: String? = null,
            posterUrl: String? = null,
        ): String {
            val base = "seerr_media/${mediaType.lowercase()}/$tmdbId"
            val params = buildList {
                title
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add("seerrTitle=${android.net.Uri.encode(it)}") }
                backdropUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add("seerrBackdrop=${android.net.Uri.encode(it)}") }
                posterUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add("seerrPoster=${android.net.Uri.encode(it)}") }
            }
            return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        }

        fun createServerManagementRoute(): String {
            return SERVER_MANAGEMENT_ROUTE
        }

        fun createAddEditServerRoute(serverId: String? = null): String {
            return if (serverId != null) {
                "add_edit_server?serverId=$serverId"
            } else {
                "add_edit_server"
            }
        }

        fun createLoginRoute(serverUrl: String? = null): String {
            return if (serverUrl != null) {
                "login?serverUrl=$serverUrl"
            } else {
                "login"
            }
        }

        fun createServicesHubRoute(entry: String = "firstRun"): String {
            return "services_hub?entry=$entry"
        }
    }
}
