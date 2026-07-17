@file:UnstableApi

package com.makd.afinity.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.makd.afinity.R
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.updater.UpdateManager
import com.makd.afinity.data.websocket.WebSocketState
import com.makd.afinity.ui.admin.identify.IdentifyScreen
import com.makd.afinity.ui.admin.images.EditImagesScreen
import com.makd.afinity.ui.admin.metadata.EditMetadataScreen
import com.makd.afinity.ui.audiobookshelf.genre.AudiobookshelfGenreResultsScreen
import com.makd.afinity.ui.audiobookshelf.item.AudiobookshelfItemScreen
import com.makd.afinity.ui.audiobookshelf.item.series.AudiobookshelfSeriesScreen
import com.makd.afinity.ui.audiobookshelf.libraries.AudiobookshelfLibrariesScreen
import com.makd.afinity.ui.audiobookshelf.libraries.AudiobookshelfLibraryScreen
import com.makd.afinity.ui.audiobookshelf.libraries.AudiobookshelfSeriesListScreen
import com.makd.afinity.ui.audiobookshelf.player.AudiobookshelfPlayerScreen
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AppNavigationDrawerContent
import com.makd.afinity.ui.favorites.FavoritesCategory
import com.makd.afinity.ui.favorites.FavoritesCategoryScreen
import com.makd.afinity.ui.favorites.FavoritesScreen
import com.makd.afinity.ui.home.HomeScreen
import com.makd.afinity.ui.item.ItemDetailScreen
import com.makd.afinity.ui.libraries.LibrariesScreen
import com.makd.afinity.ui.library.LibraryContentScreen
import com.makd.afinity.ui.login.LoginScreen
import com.makd.afinity.ui.main.MainViewModel
import com.makd.afinity.ui.music.album.MusicAlbumScreen
import com.makd.afinity.ui.music.artist.MusicArtistScreen
import com.makd.afinity.ui.music.genre.MusicGenreScreen
import com.makd.afinity.ui.music.library.LibraryFilter
import com.makd.afinity.ui.music.library.MusicBrowseScreen
import com.makd.afinity.ui.music.library.MusicLibraryScreen
import com.makd.afinity.ui.music.player.MusicPlayerScreen
import com.makd.afinity.ui.music.playlist.MusicPlaylistScreen
import com.makd.afinity.ui.person.PersonScreen
import com.makd.afinity.ui.player.AudioMiniPlayer
import com.makd.afinity.ui.player.AudioMiniPlayerState
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.requests.FilterParams
import com.makd.afinity.ui.requests.FilterType
import com.makd.afinity.ui.requests.FilteredMediaScreen
import com.makd.afinity.ui.requests.RequestsScreen
import com.makd.afinity.ui.search.GenreResultsScreen
import com.makd.afinity.ui.search.SearchScreen
import com.makd.afinity.ui.settings.LicensesScreen
import com.makd.afinity.ui.settings.SettingsScreen
import com.makd.afinity.ui.settings.appearance.AppearanceOptionsScreen
import com.makd.afinity.ui.settings.downloads.DownloadSettingsScreen
import com.makd.afinity.ui.settings.player.PlayerOptionsScreen
import com.makd.afinity.ui.settings.servers.AddEditServerScreen
import com.makd.afinity.ui.settings.servers.ServerManagementScreen
import com.makd.afinity.ui.settings.update.GlobalUpdateDialog
import com.makd.afinity.ui.watchlist.WatchlistCategory
import com.makd.afinity.ui.watchlist.WatchlistCategoryScreen
import com.makd.afinity.ui.watchlist.WatchlistScreen
import kotlinx.coroutines.launch
import timber.log.Timber

val LocalPlayerOffset = compositionLocalOf { 0.dp }
val LocalShowRatings = compositionLocalOf { true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = hiltViewModel(),
    viewModel: MainNavigationViewModel = hiltViewModel(),
    updateManager: UpdateManager,
    offlineModeManager: OfflineModeManager,
    widthSizeClass: WindowWidthSizeClass,
) {
    val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val favoritesCount by viewModel.favoritesCount.collectAsStateWithLifecycle()
    val watchlistCount by viewModel.watchlistCount.collectAsStateWithLifecycle()
    val isJellyseerrAuthenticated by
        viewModel.isJellyseerrAuthenticated.collectAsStateWithLifecycle()
    val isAudiobookshelfAuthenticated by
        viewModel.isAudiobookshelfAuthenticated.collectAsStateWithLifecycle()
    val hasLiveTvAccess by viewModel.hasLiveTvAccess.collectAsStateWithLifecycle()
    val appLoadingState by viewModel.appLoadingState.collectAsStateWithLifecycle()
    val isOffline by offlineModeManager.isOffline.collectAsStateWithLifecycle(initialValue = false)
    val connectionType by offlineModeManager.connectionType.collectAsStateWithLifecycle()
    val audiobookshelfPlaybackState by
        viewModel.audiobookshelfPlaybackManager.playbackState.collectAsStateWithLifecycle()
    val musicPlaybackState by viewModel.musicPlaybackManager.state.collectAsStateWithLifecycle()
    val showRatings by viewModel.showRatings.collectAsStateWithLifecycle()
    val navigationDrawerEnabled by viewModel.navigationDrawerEnabled.collectAsStateWithLifecycle()
    val librariesInDrawer by viewModel.librariesInDrawer.collectAsStateWithLifecycle()
    val serverName by viewModel.serverName.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val railState = rememberWideNavigationRailState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val coroutineScope = rememberCoroutineScope()
    val pendingNavigationRoute by viewModel.pendingNavigationRoute.collectAsStateWithLifecycle()

    LaunchedEffect(pendingNavigationRoute) {
        pendingNavigationRoute?.let { route ->
            navController.navigate(route) { launchSingleTop = true }
            viewModel.consumePendingNavigation()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val webSocketState by mainViewModel.webSocketState.collectAsStateWithLifecycle()
    val serverRestartingMessage = stringResource(R.string.websocket_server_restarting)
    val serverShutdownMessage = stringResource(R.string.websocket_server_shutdown)

    LaunchedEffect(webSocketState) {
        when (webSocketState) {
            WebSocketState.SERVER_RESTARTING ->
                snackbarHostState.showSnackbar(
                    message = serverRestartingMessage,
                    duration = SnackbarDuration.Long,
                )
            WebSocketState.SERVER_SHUTDOWN ->
                snackbarHostState.showSnackbar(
                    message = serverShutdownMessage,
                    duration = SnackbarDuration.Indefinite,
                )
            else -> snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    val shouldShowNavigation =
        currentDestination?.route?.let { route -> Destination.entries.any { it.route == route } }
            ?: true

    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact
    val onMenuClick: (() -> Unit)? =
        if (navigationDrawerEnabled) {
            { coroutineScope.launch { railState.expand() } }
        } else null

    LaunchedEffect(isOffline) {
        if (isOffline) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != null && currentRoute != Destination.HOME.route) {
                Timber.d("Switching to offline mode, navigating to HOME")
                navController.navigate(Destination.HOME.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    LaunchedEffect(navigationDrawerEnabled, librariesInDrawer, currentDestination) {
        if (
            currentDestination?.route == Destination.LIBRARIES.route &&
                !(navigationDrawerEnabled && librariesInDrawer)
        ) {
            Timber.d("Libraries menu entry removed, navigating to HOME")
            navController.navigate(Destination.HOME.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    AnimatedContent(
        targetState = appLoadingState.isLoading,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
        },
        label = "MainNavigationLoadingState",
    ) { isLoading ->
        if (isLoading) {
            Box(modifier = modifier.fillMaxSize())
        } else {
            NavigationSuiteScaffold(
                layoutType =
                    when {
                        !shouldShowNavigation -> NavigationSuiteType.None
                        navigationDrawerEnabled -> NavigationSuiteType.None
                        useNavRail -> NavigationSuiteType.NavigationRail
                        else -> NavigationSuiteType.NavigationBar
                    },
                navigationSuiteItems = {
                    Destination.entries.forEach { destination ->
                        if (isOffline && destination != Destination.HOME) {
                            return@forEach
                        }

                        if (destination == Destination.LIBRARIES) {
                            return@forEach
                        }

                        if (destination == Destination.FAVORITES && favoritesCount == 0) {
                            return@forEach
                        }

                        if (destination == Destination.WATCHLIST && watchlistCount == 0) {
                            return@forEach
                        }

                        if (destination == Destination.REQUESTS && !isJellyseerrAuthenticated) {
                            return@forEach
                        }

                        if (
                            destination == Destination.AUDIOBOOKS && !isAudiobookshelfAuthenticated
                        ) {
                            return@forEach
                        }

                        if (destination == Destination.LIVE_TV && !hasLiveTvAccess) {
                            return@forEach
                        }

                        val selected =
                            currentDestination?.hierarchy?.any { it.route == destination.route } ==
                                true

                        item(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter =
                                        painterResource(
                                            id =
                                                if (selected) {
                                                    destination.selectedIconRes
                                                } else {
                                                    destination.unselectedIconRes
                                                }
                                        ),
                                    contentDescription = destination.title,
                                )
                            },
                            label = {
                                if (selected) {
                                    Text(
                                        text = destination.title,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                        )
                    }
                },
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                navigationSuiteColors =
                    androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
                        .colors(
                            navigationBarContainerColor = MaterialTheme.colorScheme.surface,
                            navigationRailContainerColor = MaterialTheme.colorScheme.surface,
                        ),
            ) {
                val isOnAudiobookshelfPlayer =
                    currentDestination?.route?.startsWith("audiobookshelf/player/") == true
                val isOnMusicPlayer = currentDestination?.route == Destination.MUSIC_PLAYER_ROUTE
                val isOnAnyPlayer = isOnAudiobookshelfPlayer || isOnMusicPlayer
                val currentTrack = musicPlaybackState.currentTrack
                val miniPlayerState: AudioMiniPlayerState? =
                    when {
                        isOnAnyPlayer -> null
                        audiobookshelfPlaybackState.sessionId != null ->
                            AudioMiniPlayerState.Abs(
                                title = audiobookshelfPlaybackState.displayTitle,
                                author = audiobookshelfPlaybackState.displayAuthor,
                                currentChapter = audiobookshelfPlaybackState.currentChapter,
                                coverUrl = audiobookshelfPlaybackState.coverUrl,
                                currentTime = audiobookshelfPlaybackState.currentTime,
                                duration = audiobookshelfPlaybackState.duration,
                                isPlaying = audiobookshelfPlaybackState.isPlaying,
                                isBuffering = audiobookshelfPlaybackState.isBuffering,
                            )
                        currentTrack != null ->
                            AudioMiniPlayerState.Music(
                                title = currentTrack.name,
                                artist = currentTrack.artist ?: currentTrack.artists.firstOrNull(),
                                coverUrl = currentTrack.images?.primary?.toString(),
                                blurHash = currentTrack.images?.primaryImageBlurHash,
                                positionMs = musicPlaybackState.positionMs,
                                durationMs = musicPlaybackState.durationMs,
                                isPlaying = musicPlaybackState.isPlaying,
                                isBuffering = musicPlaybackState.isBuffering,
                            )
                        else -> null
                    }
                val globalPlayerOffset by
                    animateDpAsState(
                        targetValue = if (miniPlayerState != null) 112.dp else 0.dp,
                        label = "globalPlayerOffset",
                    )
                val drawerBody: @Composable () -> Unit = {
                    CompositionLocalProvider(
                        LocalPlayerOffset provides globalPlayerOffset,
                        LocalShowRatings provides showRatings,
                    ) {
                        SharedTransitionLayout {
                            Box(modifier = Modifier.fillMaxSize()) {
                                NavHost(
                                    navController = navController,
                                    startDestination = Destination.HOME.route,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    composable(Destination.HOME.route) {
                                        HomeScreen(
                                            onItemClick = { item ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = item.id.toString(),
                                                        itemType =
                                                            when (item) {
                                                                is AfinityShow -> "Series"
                                                                is AfinitySeason -> "Season"
                                                                else -> null
                                                            },
                                                        seriesId =
                                                            (item as? AfinitySeason)
                                                                ?.seriesId
                                                                ?.toString(),
                                                    )
                                                navController.navigate(route)
                                            },
                                            onPlayClick = { item ->
                                                coroutineScope.launch {
                                                    try {
                                                        val playableItem =
                                                            viewModel.resolvePlayableItem(item)

                                                        if (playableItem == null) {
                                                            Timber.w(
                                                                "Could not resolve playable item for: ${item.name}"
                                                            )
                                                            return@launch
                                                        }

                                                        PlayerLauncher.launch(
                                                            context = navController.context,
                                                            itemId = playableItem.id,
                                                            mediaSourceId =
                                                                playableItem.sources
                                                                    .firstOrNull()
                                                                    ?.id ?: "",
                                                            audioStreamIndex = null,
                                                            subtitleStreamIndex = null,
                                                            startPositionMs = 0L,
                                                        )
                                                    } catch (e: Exception) {
                                                        Timber.e(
                                                            e,
                                                            "Failed to handle play click for: ${item.name}",
                                                        )
                                                    }
                                                }
                                            },
                                            onProfileClick = {
                                                val route = Destination.createSettingsRoute()
                                                navController.navigate(route)
                                            },
                                            onAbsItemClick = { itemId ->
                                                navController.navigate(
                                                    Destination.createAudiobookshelfItemRoute(
                                                        itemId
                                                    )
                                                )
                                            },
                                            navController = navController,
                                            snackbarHostState = snackbarHostState,
                                            mainUiState = mainUiState,
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                            onMenuClick = onMenuClick,
                                            hideLibrariesSection =
                                                navigationDrawerEnabled && librariesInDrawer,
                                        )
                                    }

                                    composable(Destination.LIBRARIES.route) {
                                        LibrariesScreen(
                                            onMenuClick = onMenuClick,
                                            onLibraryClick = { library ->
                                                val route =
                                                    if (library.type == CollectionType.Music) {
                                                        Destination.createMusicLibraryRoute(
                                                            libraryId = library.id.toString(),
                                                            libraryName = library.name,
                                                        )
                                                    } else {
                                                        Destination.createLibraryContentRoute(
                                                            libraryId = library.id.toString(),
                                                            libraryName = library.name,
                                                        )
                                                    }
                                                navController.navigate(route)
                                            },
                                            onProfileClick = {
                                                val route = Destination.createSettingsRoute()
                                                navController.navigate(route)
                                            },
                                            navController = navController,
                                            mainUiState = mainUiState,
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.LIBRARY_CONTENT_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("libraryId") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("libraryName") {
                                                    type = NavType.StringType
                                                },
                                            ),
                                    ) {
                                        LibraryContentScreen(
                                            onItemClick = { item ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = item.id.toString(),
                                                        itemType =
                                                            when (item) {
                                                                is AfinityShow -> "Series"
                                                                is AfinitySeason -> "Season"
                                                                else -> null
                                                            },
                                                        seriesId =
                                                            (item as? AfinitySeason)
                                                                ?.seriesId
                                                                ?.toString(),
                                                    )
                                                navController.navigate(route)
                                            },
                                            onProfileClick = {
                                                val route = Destination.createSettingsRoute()
                                                navController.navigate(route)
                                            },
                                            navController = navController,
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.STUDIO_CONTENT_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("studioName") {
                                                    type = NavType.StringType
                                                }
                                            ),
                                    ) {
                                        LibraryContentScreen(
                                            onItemClick = { item ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = item.id.toString(),
                                                        itemType =
                                                            when (item) {
                                                                is AfinityShow -> "Series"
                                                                is AfinitySeason -> "Season"
                                                                else -> null
                                                            },
                                                        seriesId =
                                                            (item as? AfinitySeason)
                                                                ?.seriesId
                                                                ?.toString(),
                                                    )
                                                navController.navigate(route)
                                            },
                                            onProfileClick = {
                                                val route = Destination.createSettingsRoute()
                                                navController.navigate(route)
                                            },
                                            navController = navController,
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.ITEM_DETAIL_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("itemId") { type = NavType.StringType },
                                                navArgument("itemType") {
                                                    type = NavType.StringType
                                                    nullable = true
                                                    defaultValue = null
                                                },
                                                navArgument("seriesId") {
                                                    type = NavType.StringType
                                                    nullable = true
                                                    defaultValue = null
                                                },
                                            ),
                                    ) {
                                        ItemDetailScreen(
                                            navController = navController,
                                            onPlayClick = { item, selection ->
                                                PlayerLauncher.launch(
                                                    context = navController.context,
                                                    itemId = item.id,
                                                    mediaSourceId =
                                                        selection?.mediaSourceId
                                                            ?: item.sources.firstOrNull()?.id
                                                            ?: "",
                                                    audioStreamIndex = selection?.audioStreamIndex,
                                                    subtitleStreamIndex =
                                                        selection?.subtitleStreamIndex,
                                                    startPositionMs =
                                                        selection?.startPositionMs ?: 0L,
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.EPISODE_LIST_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("seasonId") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("seasonName") {
                                                    type = NavType.StringType
                                                },
                                            ),
                                    ) { backStackEntry ->
                                        ItemDetailScreen(
                                            onPlayClick = { item, selection ->
                                                if (selection != null) {
                                                    PlayerLauncher.launch(
                                                        context = navController.context,
                                                        itemId = item.id,
                                                        mediaSourceId = selection.mediaSourceId,
                                                        audioStreamIndex =
                                                            selection.audioStreamIndex,
                                                        subtitleStreamIndex =
                                                            selection.subtitleStreamIndex,
                                                        startPositionMs = selection.startPositionMs,
                                                    )
                                                }
                                            },
                                            navController = navController,
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.PERSON_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("personId") {
                                                    type = NavType.StringType
                                                }
                                            ),
                                    ) {
                                        PersonScreen(
                                            navController = navController,
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.EDIT_METADATA_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("itemId") { type = NavType.StringType }
                                            ),
                                    ) {
                                        EditMetadataScreen(
                                            onNavigateUp = { navController.navigateUp() },
                                            onSaveSuccess = { navController.navigateUp() },
                                        )
                                    }

                                    composable(
                                        route = Destination.IDENTIFY_ITEM_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("itemId") { type = NavType.StringType },
                                                navArgument("itemType") {
                                                    type = NavType.StringType
                                                },
                                            ),
                                    ) {
                                        IdentifyScreen(
                                            onNavigateUp = { navController.navigateUp() },
                                            onApplySuccess = { navController.navigateUp() },
                                        )
                                    }

                                    composable(
                                        route = Destination.EDIT_IMAGES_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("itemId") { type = NavType.StringType }
                                            ),
                                    ) {
                                        EditImagesScreen(
                                            onNavigateUp = { navController.navigateUp() }
                                        )
                                    }

                                    composable(Destination.FAVORITES.route) {
                                        FavoritesScreen(
                                            onMenuClick = onMenuClick,
                                            onItemClick = { item ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = item.id.toString(),
                                                        itemType =
                                                            when (item) {
                                                                is AfinityShow -> "Series"
                                                                is AfinitySeason -> "Season"
                                                                else -> null
                                                            },
                                                        seriesId =
                                                            (item as? AfinitySeason)
                                                                ?.seriesId
                                                                ?.toString(),
                                                    )
                                                navController.navigate(route)
                                            },
                                            onPersonClick = { personId ->
                                                val route = Destination.createPersonRoute(personId)
                                                navController.navigate(route)
                                            },
                                            onViewAllClick = { category ->
                                                navController.navigate(
                                                    Destination.createFavoritesCategoryRoute(
                                                        category.name
                                                    )
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            mainUiState = mainUiState,
                                            navController = navController,
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(Destination.WATCHLIST.route) {
                                        WatchlistScreen(
                                            onMenuClick = onMenuClick,
                                            onItemClick = { item ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = item.id.toString(),
                                                        itemType =
                                                            when (item) {
                                                                is AfinityShow -> "Series"
                                                                is AfinitySeason -> "Season"
                                                                else -> null
                                                            },
                                                        seriesId =
                                                            (item as? AfinitySeason)
                                                                ?.seriesId
                                                                ?.toString(),
                                                    )
                                                navController.navigate(route)
                                            },
                                            onViewAllClick = { category ->
                                                navController.navigate(
                                                    Destination.createWatchlistCategoryRoute(
                                                        category.name
                                                    )
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            mainUiState = mainUiState,
                                            navController = navController,
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(Destination.REQUESTS.route) {
                                        RequestsScreen(
                                            onMenuClick = onMenuClick,
                                            onSearchClick = {
                                                navController.navigate(Destination.SEARCH_ROUTE)
                                            },
                                            onProfileClick = {
                                                val route = Destination.createSettingsRoute()
                                                navController.navigate(route)
                                            },
                                            mainUiState = mainUiState,
                                            onNavigateToFilteredMedia = { filterParams ->
                                                val route =
                                                    Destination.createFilteredMediaRoute(
                                                        filterType = filterParams.type.name,
                                                        filterId = filterParams.id,
                                                        filterName = filterParams.name,
                                                    )
                                                navController.navigate(route)
                                            },
                                            onItemClick = { jellyfinItemId, itemType ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = jellyfinItemId,
                                                        itemType = itemType,
                                                    )
                                                navController.navigate(route)
                                            },
                                            onNavigateToSeerrMedia = { seerrItem ->
                                                navController.navigate(
                                                    Destination.createSeerrMediaRoute(
                                                        mediaType = seerrItem.mediaType,
                                                        tmdbId = seerrItem.id,
                                                        title = seerrItem.getDisplayTitle(),
                                                        backdropUrl =
                                                            seerrItem.backdropPath?.let {
                                                                "https://image.tmdb.org/t/p/w1280$it"
                                                            },
                                                        posterUrl = seerrItem.getPosterUrl(),
                                                    )
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(Destination.LIVE_TV.route) {
                                        com.makd.afinity.ui.livetv.LiveTvScreen(
                                            onMenuClick = onMenuClick,
                                            navController = navController,
                                            mainUiState = mainUiState,
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.FILTERED_MEDIA_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("filterType") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("filterId") { type = NavType.IntType },
                                                navArgument("filterName") {
                                                    type = NavType.StringType
                                                },
                                            ),
                                    ) { backStackEntry ->
                                        val filterTypeString =
                                            backStackEntry.arguments?.getString("filterType")
                                                ?: return@composable
                                        val filterId =
                                            backStackEntry.arguments?.getInt("filterId")
                                                ?: return@composable
                                        val filterName =
                                            backStackEntry.arguments?.getString("filterName")
                                                ?: return@composable

                                        val filterType = FilterType.valueOf(filterTypeString)
                                        val filterParams =
                                            FilterParams(filterType, filterId, filterName)

                                        FilteredMediaScreen(
                                            filterParams = filterParams,
                                            onSearchClick = {
                                                navController.navigate(Destination.SEARCH_ROUTE)
                                            },
                                            onProfileClick = {
                                                val route = Destination.createSettingsRoute()
                                                navController.navigate(route)
                                            },
                                            mainUiState = mainUiState,
                                            onItemClick = { jellyfinItemId, itemType ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = jellyfinItemId,
                                                        itemType = itemType,
                                                    )
                                                navController.navigate(route)
                                            },
                                            onNavigateToSeerrMedia = { seerrItem ->
                                                navController.navigate(
                                                    Destination.createSeerrMediaRoute(
                                                        mediaType = seerrItem.mediaType,
                                                        tmdbId = seerrItem.id,
                                                        title = seerrItem.getDisplayTitle(),
                                                        backdropUrl =
                                                            seerrItem.backdropPath?.let {
                                                                "https://image.tmdb.org/t/p/w1280$it"
                                                            },
                                                        posterUrl = seerrItem.getPosterUrl(),
                                                    )
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.FAVORITES_CATEGORY_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("category") {
                                                    type = NavType.StringType
                                                }
                                            ),
                                    ) { backStackEntry ->
                                        val categoryName =
                                            backStackEntry.arguments?.getString("category")
                                                ?: return@composable
                                        val category =
                                            FavoritesCategory.valueOf(categoryName)

                                        FavoritesCategoryScreen(
                                            category = category,
                                            mainUiState = mainUiState,
                                            onItemClick = { item ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = item.id.toString(),
                                                        itemType =
                                                            when (item) {
                                                                is AfinityShow -> "Series"
                                                                is AfinitySeason -> "Season"
                                                                else -> null
                                                            },
                                                        seriesId =
                                                            (item as? AfinitySeason)
                                                                ?.seriesId
                                                                ?.toString(),
                                                    )
                                                navController.navigate(route)
                                            },
                                            onPersonClick = { personId ->
                                                val route = Destination.createPersonRoute(personId)
                                                navController.navigate(route)
                                            },
                                            navController = navController,
                                            widthSizeClass = widthSizeClass,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }

                                    composable(
                                        route = Destination.WATCHLIST_CATEGORY_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("category") {
                                                    type = NavType.StringType
                                                }
                                            ),
                                    ) { backStackEntry ->
                                        val categoryName =
                                            backStackEntry.arguments?.getString("category")
                                                ?: return@composable
                                        val category =
                                            WatchlistCategory.valueOf(categoryName)

                                        WatchlistCategoryScreen(
                                            category = category,
                                            mainUiState = mainUiState,
                                            onItemClick = { item ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = item.id.toString(),
                                                        itemType =
                                                            when (item) {
                                                                is AfinityShow -> "Series"
                                                                is AfinitySeason -> "Season"
                                                                else -> null
                                                            },
                                                        seriesId =
                                                            (item as? AfinitySeason)
                                                                ?.seriesId
                                                                ?.toString(),
                                                    )
                                                navController.navigate(route)
                                            },
                                            navController = navController,
                                            widthSizeClass = widthSizeClass,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }

                                    composable(
                                        route = Destination.SEERR_MEDIA_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("seerrMediaType") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("seerrTmdbId") {
                                                    type = NavType.IntType
                                                },
                                                navArgument("seerrTitle") {
                                                    type = NavType.StringType
                                                    defaultValue = ""
                                                },
                                                navArgument("seerrBackdrop") {
                                                    type = NavType.StringType
                                                    defaultValue = ""
                                                },
                                                navArgument("seerrPoster") {
                                                    type = NavType.StringType
                                                    defaultValue = ""
                                                },
                                            ),
                                    ) {
                                        com.makd.afinity.ui.requests.SeerrMediaDetailScreen(
                                            onItemClick = { jellyfinItemId, itemType ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = jellyfinItemId,
                                                        itemType = itemType,
                                                    )
                                                navController.navigate(route)
                                            },
                                            onNavigateToFilteredMedia = { filterParams ->
                                                val route =
                                                    Destination.createFilteredMediaRoute(
                                                        filterType = filterParams.type.name,
                                                        filterId = filterParams.id,
                                                        filterName = filterParams.name,
                                                    )
                                                navController.navigate(route)
                                            },
                                            onNavigateToSeerrMedia = { seerrItem ->
                                                navController.navigate(
                                                    Destination.createSeerrMediaRoute(
                                                        mediaType = seerrItem.mediaType,
                                                        tmdbId = seerrItem.id,
                                                        title = seerrItem.getDisplayTitle(),
                                                        backdropUrl =
                                                            seerrItem.backdropPath?.let {
                                                                "https://image.tmdb.org/t/p/w1280$it"
                                                            },
                                                        posterUrl = seerrItem.getPosterUrl(),
                                                    )
                                                )
                                            },
                                            onNavigateHome = {
                                                navController.navigate(Destination.HOME.route) {
                                                    popUpTo(Destination.HOME.route) {
                                                        inclusive = false
                                                    }
                                                    launchSingleTop = true
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(Destination.SEARCH_ROUTE) {
                                        SearchScreen(
                                            onBackClick = dropUnlessResumed { navController.popBackStack() },
                                            onItemClick = { item ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = item.id.toString(),
                                                        itemType =
                                                            when (item) {
                                                                is AfinityShow -> "Series"
                                                                is AfinitySeason -> "Season"
                                                                else -> null
                                                            },
                                                        seriesId =
                                                            (item as? AfinitySeason)
                                                                ?.seriesId
                                                                ?.toString(),
                                                    )
                                                navController.navigate(route)
                                            },
                                            onSeriesClick = { seriesId ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = seriesId,
                                                        itemType = "Series",
                                                    )
                                                navController.navigate(route)
                                            },
                                            onGenreClick = { genre ->
                                                val route =
                                                    Destination.createGenreResultsRoute(genre)
                                                navController.navigate(route)
                                            },
                                            onAudiobookshelfItemClick = { itemId ->
                                                navController.navigate(
                                                    Destination.createAudiobookshelfItemRoute(
                                                        itemId
                                                    )
                                                )
                                            },
                                            onAudiobookshelfGenreClick = { genre ->
                                                navController.navigate(
                                                    Destination
                                                        .createAudiobookshelfGenreResultsRoute(
                                                            genre
                                                        )
                                                )
                                            },
                                            onNavigateToSeerrMedia = { seerrItem ->
                                                navController.navigate(
                                                    Destination.createSeerrMediaRoute(
                                                        mediaType = seerrItem.mediaType,
                                                        tmdbId = seerrItem.id,
                                                        title = seerrItem.getDisplayTitle(),
                                                        backdropUrl =
                                                            seerrItem.backdropPath?.let {
                                                                "https://image.tmdb.org/t/p/w1280$it"
                                                            },
                                                        posterUrl = seerrItem.getPosterUrl(),
                                                    )
                                                )
                                            },
                                            onMusicAlbumClick = { albumId ->
                                                navController.navigate(
                                                    Destination.createMusicAlbumRoute(albumId)
                                                )
                                            },
                                            onMusicArtistClick = { artistId ->
                                                navController.navigate(
                                                    Destination.createMusicArtistRoute(artistId)
                                                )
                                            },
                                            onMusicPlaylistClick = { playlistId ->
                                                navController.navigate(
                                                    Destination.createMusicPlaylistRoute(playlistId)
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.GENRE_RESULTS_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("genre") { type = NavType.StringType }
                                            ),
                                    ) {
                                        GenreResultsScreen(
                                            genre = it.arguments?.getString("genre") ?: "",
                                            onBackClick = dropUnlessResumed { navController.popBackStack() },
                                            onItemClick = { item ->
                                                val route =
                                                    Destination.createItemDetailRoute(
                                                        itemId = item.id.toString(),
                                                        itemType =
                                                            when (item) {
                                                                is AfinityShow -> "Series"
                                                                is AfinitySeason -> "Season"
                                                                else -> null
                                                            },
                                                        seriesId =
                                                            (item as? AfinitySeason)
                                                                ?.seriesId
                                                                ?.toString(),
                                                    )
                                                navController.navigate(route)
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.AUDIOBOOKSHELF_GENRE_RESULTS_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("genre") { type = NavType.StringType }
                                            ),
                                    ) {
                                        AudiobookshelfGenreResultsScreen(
                                            genre = it.arguments?.getString("genre") ?: "",
                                            onBackClick = dropUnlessResumed { navController.popBackStack() },
                                            onItemClick = { itemId ->
                                                navController.navigate(
                                                    Destination.createAudiobookshelfItemRoute(
                                                        itemId
                                                    )
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(Destination.SETTINGS_ROUTE) {
                                        SettingsScreen(
                                            navController = navController,
                                            onBackClick = dropUnlessResumed { navController.popBackStack() },
                                            onLogoutComplete = {
                                                // Logout handled by MainActivity observing auth
                                                // state
                                            },
                                        )
                                    }

                                    composable(Destination.DOWNLOAD_SETTINGS_ROUTE) {
                                        DownloadSettingsScreen(
                                            onBackClick = dropUnlessResumed { navController.popBackStack() },
                                            onNavigateToAbsItem = { itemId ->
                                                navController.navigate(
                                                    Destination.createAudiobookshelfItemRoute(
                                                        itemId
                                                    )
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }

                                    composable(Destination.PLAYER_OPTIONS_ROUTE) {
                                        PlayerOptionsScreen(
                                            onBackClick = dropUnlessResumed { navController.popBackStack() },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }

                                    composable(Destination.APPEARANCE_OPTIONS_ROUTE) {
                                        AppearanceOptionsScreen(
                                            onBackClick = dropUnlessResumed { navController.popBackStack() }
                                        )
                                    }

                                    composable(Destination.LICENSES_ROUTE) {
                                        LicensesScreen(
                                            onBackClick = dropUnlessResumed { navController.popBackStack() }
                                        )
                                    }

                                    composable(Destination.SERVER_MANAGEMENT_ROUTE) {
                                        ServerManagementScreen(
                                            onBackClick = dropUnlessResumed { navController.popBackStack() },
                                            onAddServerClick = {
                                                val route =
                                                    Destination.createAddEditServerRoute(
                                                        serverId = null
                                                    )
                                                navController.navigate(route)
                                            },
                                            onEditServerClick = { serverId ->
                                                val route =
                                                    Destination.createAddEditServerRoute(
                                                        serverId = serverId
                                                    )
                                                navController.navigate(route)
                                            },
                                        )
                                    }

                                    composable(
                                        route = Destination.ADD_EDIT_SERVER_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("serverId") {
                                                    type = NavType.StringType
                                                    nullable = true
                                                    defaultValue = null
                                                }
                                            ),
                                    ) {
                                        AddEditServerScreen(
                                            onBackClick = dropUnlessResumed { navController.popBackStack() }
                                        )
                                    }

                                    composable(
                                        route = Destination.LOGIN_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("serverUrl") {
                                                    type = NavType.StringType
                                                    nullable = true
                                                    defaultValue = null
                                                }
                                            ),
                                    ) {
                                        LoginScreen(
                                            onLoginSuccess = {
                                                navController.navigate(Destination.HOME.route) {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.MUSIC_LIBRARY_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("libraryId") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("libraryName") {
                                                    type = NavType.StringType
                                                },
                                            ),
                                    ) {
                                        MusicLibraryScreen(navController = navController)
                                    }

                                    composable(
                                        route = Destination.MUSIC_BROWSE_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("libraryId") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("libraryName") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("tab") { type = NavType.StringType },
                                            ),
                                    ) { backStackEntry ->
                                        val tabName =
                                            backStackEntry.arguments?.getString("tab") ?: "Albums"
                                        val tab =
                                            LibraryFilter.entries.firstOrNull { it.name == tabName }
                                                ?: LibraryFilter.Albums
                                        MusicBrowseScreen(
                                            tab = tab,
                                            navController = navController,
                                        )
                                    }

                                    composable(
                                        route = Destination.MUSIC_ALBUM_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("albumId") { type = NavType.StringType }
                                            ),
                                    ) {
                                        MusicAlbumScreen(navController = navController)
                                    }

                                    composable(
                                        route = Destination.MUSIC_ARTIST_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("artistId") {
                                                    type = NavType.StringType
                                                }
                                            ),
                                    ) {
                                        MusicArtistScreen(navController = navController)
                                    }

                                    composable(
                                        route = Destination.MUSIC_PLAYLIST_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("playlistId") {
                                                    type = NavType.StringType
                                                }
                                            ),
                                    ) {
                                        MusicPlaylistScreen(navController = navController)
                                    }

                                    composable(
                                        route = Destination.MUSIC_GENRE_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("genreName") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("imageUrl") {
                                                    type = NavType.StringType
                                                    nullable = true
                                                    defaultValue = null
                                                },
                                                navArgument("genreId") {
                                                    type = NavType.StringType
                                                    nullable = true
                                                    defaultValue = null
                                                },
                                            ),
                                    ) {
                                        MusicGenreScreen(navController = navController)
                                    }

                                    composable(Destination.AUDIOBOOKSHELF_LIBRARIES_ROUTE) {
                                        AudiobookshelfLibrariesScreen(
                                            onMenuClick = onMenuClick,
                                            onNavigateToItem = { itemId ->
                                                navController.navigate(
                                                    Destination.createAudiobookshelfItemRoute(
                                                        itemId
                                                    )
                                                )
                                            },
                                            navController = navController,
                                            mainUiState = mainUiState,
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(Destination.AUDIOBOOKSHELF_SERIES_LIST_ROUTE) {
                                        AudiobookshelfSeriesListScreen(
                                            onNavigateToItem = { itemId ->
                                                navController.navigate(
                                                    Destination.createAudiobookshelfItemRoute(
                                                        itemId
                                                    )
                                                )
                                            },
                                            navController = navController,
                                            mainUiState = mainUiState,
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }

                                    composable(
                                        route = Destination.AUDIOBOOKSHELF_LIBRARY_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("libraryId") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("libraryName") {
                                                    type = NavType.StringType
                                                },
                                            ),
                                    ) {
                                        AudiobookshelfLibraryScreen(
                                            onNavigateToItem = { itemId ->
                                                navController.navigate(
                                                    Destination.createAudiobookshelfItemRoute(
                                                        itemId
                                                    )
                                                )
                                            },
                                            navController = navController,
                                            mainUiState = mainUiState,
                                        )
                                    }

                                    composable(
                                        route = Destination.AUDIOBOOKSHELF_ITEM_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("itemId") { type = NavType.StringType }
                                            ),
                                    ) {
                                        AudiobookshelfItemScreen(
                                            onNavigateHome = {
                                                navController.navigate(Destination.HOME.route) {
                                                    popUpTo(Destination.HOME.route) {
                                                        inclusive = false
                                                    }
                                                    launchSingleTop = true
                                                }
                                            },
                                            onNavigateToPlayer = {
                                                itemId,
                                                episodeId,
                                                startPosition,
                                                episodeSort ->
                                                navController.navigate(
                                                    Destination.createAudiobookshelfPlayerRoute(
                                                        itemId,
                                                        episodeId,
                                                        startPosition,
                                                        episodeSort,
                                                    )
                                                )
                                            },
                                            onNavigateToSeries = { seriesId, libraryId, seriesName
                                                ->
                                                navController.navigate(
                                                    Destination.createAudiobookshelfSeriesRoute(
                                                        seriesId,
                                                        libraryId,
                                                        seriesName,
                                                    )
                                                )
                                            },
                                        )
                                    }

                                    composable(
                                        route = Destination.AUDIOBOOKSHELF_SERIES_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("seriesId") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("libraryId") {
                                                    type = NavType.StringType
                                                },
                                                navArgument("seriesName") {
                                                    type = NavType.StringType
                                                },
                                            ),
                                    ) {
                                        AudiobookshelfSeriesScreen(
                                            onNavigateHome = {
                                                navController.navigate(Destination.HOME.route) {
                                                    popUpTo(Destination.HOME.route) {
                                                        inclusive = false
                                                    }
                                                    launchSingleTop = true
                                                }
                                            },
                                            onNavigateToPlayer = { itemId, episodeId, startPosition
                                                ->
                                                navController.navigate(
                                                    Destination.createAudiobookshelfPlayerRoute(
                                                        itemId = itemId,
                                                        episodeId = episodeId,
                                                        startPosition = startPosition,
                                                    )
                                                )
                                            },
                                        )
                                    }

                                    composable(
                                        route = Destination.AUDIOBOOKSHELF_PLAYER_ROUTE,
                                        arguments =
                                            listOf(
                                                navArgument("itemId") { type = NavType.StringType },
                                                navArgument("episodeId") {
                                                    type = NavType.StringType
                                                    nullable = true
                                                    defaultValue = null
                                                },
                                                navArgument("startPosition") {
                                                    type = NavType.StringType
                                                    nullable = true
                                                    defaultValue = null
                                                },
                                                navArgument("episodeSort") {
                                                    type = NavType.StringType
                                                    nullable = true
                                                    defaultValue = null
                                                },
                                            ),
                                    ) {
                                        AudiobookshelfPlayerScreen(
                                            onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                                            animatedVisibilityScope = this@composable,
                                        )
                                    }

                                    composable(route = Destination.MUSIC_PLAYER_ROUTE) {
                                        MusicPlayerScreen(
                                            onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                                            animatedVisibilityScope = this@composable,
                                        )
                                    }
                                }

                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    snackbar = { data -> AFinitySnackbar(data) },
                                    modifier =
                                        Modifier.align(Alignment.BottomCenter)
                                            .padding(bottom = globalPlayerOffset + 8.dp)
                                            .navigationBarsPadding(),
                                )

                                AnimatedVisibility(
                                    visible = miniPlayerState != null,
                                    enter = slideInVertically { it },
                                    exit = slideOutVertically { it },
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                ) {
                                    if (miniPlayerState != null) {
                                        AudioMiniPlayer(
                                            state = miniPlayerState,
                                            modifier = Modifier.navigationBarsPadding(),
                                            animatedVisibilityScope = this@AnimatedVisibility,
                                            onPlayPauseClick = {
                                                when (miniPlayerState) {
                                                    is AudioMiniPlayerState.Abs ->
                                                        if (
                                                            viewModel.audiobookshelfPlayer
                                                                .isPlaying()
                                                        )
                                                            viewModel.audiobookshelfPlayer.pause()
                                                        else viewModel.audiobookshelfPlayer.play()
                                                    is AudioMiniPlayerState.Music ->
                                                        if (musicPlaybackState.isPlaying)
                                                            viewModel.musicPlaybackManager.pause()
                                                        else viewModel.musicPlaybackManager.play()
                                                }
                                            },
                                            onSkipNext =
                                                if (miniPlayerState is AudioMiniPlayerState.Music) {
                                                    { viewModel.musicPlaybackManager.skipToNext() }
                                                } else null,
                                            onCloseClick = {
                                                when (miniPlayerState) {
                                                    is AudioMiniPlayerState.Abs -> {
                                                        Timber.tag("ABS-MiniPlayer")
                                                            .d(
                                                                "MainNavigation: ABS onCloseClick — calling pause()+closeSession()"
                                                            )
                                                        viewModel.audiobookshelfPlayer.pause()
                                                        viewModel.audiobookshelfPlayer
                                                            .closeSession()
                                                    }
                                                    is AudioMiniPlayerState.Music -> {
                                                        Timber.tag("ABS-MiniPlayer")
                                                            .d(
                                                                "MainNavigation: Music onCloseClick — calling stop()+ACTION_STOP"
                                                            )
                                                        viewModel.musicPlaybackManager.stop()
                                                        navController.context.startService(
                                                            android.content
                                                                .Intent(
                                                                    navController.context,
                                                                    com.makd.afinity.player
                                                                            .AudioService::class
                                                                        .java,
                                                                )
                                                                .setAction(
                                                                    com.makd.afinity.player
                                                                        .AudioService
                                                                        .ACTION_STOP
                                                                )
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                when (miniPlayerState) {
                                                    is AudioMiniPlayerState.Abs -> {
                                                        val itemId =
                                                            audiobookshelfPlaybackState.itemId
                                                        val episodeId =
                                                            audiobookshelfPlaybackState.episodeId
                                                        if (itemId != null) {
                                                            navController.navigate(
                                                                Destination
                                                                    .createAudiobookshelfPlayerRoute(
                                                                        itemId,
                                                                        episodeId,
                                                                    )
                                                            )
                                                        }
                                                    }
                                                    is AudioMiniPlayerState.Music ->
                                                        navController.navigate(
                                                            Destination.MUSIC_PLAYER_ROUTE
                                                        )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    drawerBody()

                    if (navigationDrawerEnabled) {
                        ModalWideNavigationRail(
                            state = railState,
                            hideOnCollapse = true,
                            expandedShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                            colors =
                                WideNavigationRailDefaults.colors()
                                    .copy(
                                        modalContainerColor =
                                            MaterialTheme.colorScheme.primary
                                                .copy(alpha = 0.15f)
                                                .compositeOver(
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                )
                                    ),
                            windowInsets = WindowInsets(0.dp),
                        ) {
                            AppNavigationDrawerContent(
                                currentDestination = currentDestination,
                                userName = mainUiState.userName,
                                userProfileImageUrl = mainUiState.userProfileImageUrl,
                                serverName = serverName,
                                connectionType = connectionType,
                                isAdmin = isAdmin,
                                isOffline = isOffline,
                                favoritesCount = favoritesCount,
                                watchlistCount = watchlistCount,
                                isJellyseerrAuthenticated = isJellyseerrAuthenticated,
                                isAudiobookshelfAuthenticated = isAudiobookshelfAuthenticated,
                                hasLiveTvAccess = hasLiveTvAccess,
                                librariesInDrawer = librariesInDrawer,
                                onDestinationClick = { destination ->
                                    coroutineScope.launch { railState.collapse() }
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onSettingsClick = {
                                    coroutineScope.launch { railState.collapse() }
                                    navController.navigate(Destination.createSettingsRoute())
                                },
                                onAddAccountClick = { server ->
                                    coroutineScope.launch { railState.collapse() }
                                    navController.navigate(
                                        Destination.createLoginRoute(serverUrl = server.address)
                                    )
                                },
                                onCloseDrawer = {
                                    coroutineScope.launch { railState.collapse() }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    GlobalUpdateDialog(updateManager = updateManager)
}
