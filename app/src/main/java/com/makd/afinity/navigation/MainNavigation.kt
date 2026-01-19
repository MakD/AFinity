package com.makd.afinity.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.data.updater.UpdateManager
import com.makd.afinity.ui.components.AfinitySplashScreen
import com.makd.afinity.ui.favorites.FavoritesScreen
import com.makd.afinity.ui.home.HomeScreen
import com.makd.afinity.ui.item.ItemDetailScreen
import com.makd.afinity.ui.libraries.LibrariesScreen
import com.makd.afinity.ui.library.LibraryContentScreen
import com.makd.afinity.ui.main.MainViewModel
import com.makd.afinity.ui.person.PersonScreen
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
import com.makd.afinity.ui.watchlist.WatchlistScreen
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = hiltViewModel(),
    viewModel: MainNavigationViewModel = hiltViewModel(),
    updateManager: UpdateManager,
    offlineModeManager: OfflineModeManager,
    widthSizeClass: WindowWidthSizeClass
) {
    val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val watchlistRepository: WatchlistRepository =
        hiltViewModel<MainNavigationViewModel>().watchlistRepository
    val watchlistCount by watchlistRepository.getWatchlistCountFlow()
        .collectAsStateWithLifecycle(initialValue = 0)
    val jellyseerrRepository: JellyseerrRepository =
        hiltViewModel<MainNavigationViewModel>().jellyseerrRepository
    val isJellyseerrAuthenticated by jellyseerrRepository.isAuthenticated
        .collectAsStateWithLifecycle()
    val appLoadingState by viewModel.appLoadingState.collectAsStateWithLifecycle()
    val isOffline by offlineModeManager.isOffline.collectAsStateWithLifecycle(initialValue = false)
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val coroutineScope = rememberCoroutineScope()

    if (appLoadingState.isLoading) {
        AfinitySplashScreen(
            statusText = appLoadingState.loadingPhase.ifEmpty { "Loading..." },
            progress = appLoadingState.loadingProgress,
            modifier = modifier
        )
        return
    }

    val shouldShowNavigation = currentDestination?.route?.let { route ->
        !route.startsWith("library_content/") &&
                !route.startsWith("studio_content/") &&
                !route.startsWith("item_detail/") &&
                !route.startsWith("episodes/") &&
                !route.startsWith("player/") &&
                !route.startsWith("person/") &&
                route != "search" &&
                !route.startsWith("genre_results/") &&
                !route.startsWith("filtered_media/") &&
                route != "settings" &&
                route != "download_settings" &&
                route != "player_options" &&
                route != "appearance_options" &&
                route != "licenses" &&
                route != "server_management" &&
                route != "add_edit_server/"
    } ?: true

    val navigationSuiteScaffoldState = rememberNavigationSuiteScaffoldState()

    LaunchedEffect(shouldShowNavigation) {
        if (shouldShowNavigation) {
            navigationSuiteScaffoldState.show()
        } else {
            navigationSuiteScaffoldState.hide()
        }
    }

    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact

    LaunchedEffect(isOffline) {
        if (isOffline) {
            val currentRoute = currentDestination?.route
            if (currentRoute != Destination.HOME.route) {
                Timber.d("Switching to offline mode, navigating to HOME")
                navController.navigate(Destination.HOME.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    NavigationSuiteScaffold(
        layoutType = if (useNavRail) NavigationSuiteType.NavigationRail else NavigationSuiteType.NavigationBar,
        navigationSuiteItems = {
            Destination.entries.forEach { destination ->
                if (isOffline && destination != Destination.HOME) {
                    return@forEach
                }

                if (destination == Destination.WATCHLIST && watchlistCount == 0) {
                    return@forEach
                }

                if (destination == Destination.REQUESTS && !isJellyseerrAuthenticated) {
                    return@forEach
                }

                val selected = currentDestination?.hierarchy?.any {
                    it.route == destination.route
                } == true

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
                            painter = painterResource(
                                id = if (selected) {
                                    destination.selectedIconRes
                                } else {
                                    destination.unselectedIconRes
                                }
                            ),
                            contentDescription = destination.title
                        )

                    },
                    label = {
                        if (selected) {
                            Text(
                                text = destination.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            }
        },
        state = navigationSuiteScaffoldState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        navigationSuiteColors = androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults.colors(
            navigationBarContainerColor = MaterialTheme.colorScheme.surface,
            navigationRailContainerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(
                    onItemClick = { item ->
                        val route = Destination.createItemDetailRoute(item.id.toString())
                        navController.navigate(route)
                    },
                    onPlayClick = { item ->
                        coroutineScope.launch {
                            try {
                                val playableItem = viewModel.resolvePlayableItem(item)

                                if (playableItem == null) {
                                    Timber.w("Could not resolve playable item for: ${item.name}")
                                    return@launch
                                }

                                PlayerLauncher.launch(
                                    context = navController.context,
                                    itemId = playableItem.id,
                                    mediaSourceId = playableItem.sources.firstOrNull()?.id ?: "",
                                    audioStreamIndex = null,
                                    subtitleStreamIndex = null,
                                    startPositionMs = 0L
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to handle play click for: ${item.name}")
                            }
                        }
                    },
                    onProfileClick = {
                        val route = Destination.createSettingsRoute()
                        navController.navigate(route)
                    },
                    navController = navController,
                    mainUiState = mainUiState,
                    modifier = Modifier.fillMaxSize(),
                    widthSizeClass = widthSizeClass
                )
            }

            composable(Destination.LIBRARIES.route) {
                LibrariesScreen(
                    onLibraryClick = { library ->
                        val route = Destination.createLibraryContentRoute(
                            libraryId = library.id.toString(),
                            libraryName = library.name
                        )
                        navController.navigate(route)
                    },
                    onProfileClick = {
                        val route = Destination.createSettingsRoute()
                        navController.navigate(route)
                    },
                    navController = navController,
                    mainUiState = mainUiState,
                    modifier = Modifier.fillMaxSize(),
                    widthSizeClass = widthSizeClass
                )
            }

            composable(
                route = Destination.LIBRARY_CONTENT_ROUTE,
                arguments = listOf(
                    navArgument("libraryId") { type = NavType.StringType },
                    navArgument("libraryName") { type = NavType.StringType }
                )
            ) {
                LibraryContentScreen(
                    onItemClick = { item ->
                        val route = Destination.createItemDetailRoute(item.id.toString())
                        navController.navigate(route)
                    },
                    onProfileClick = {
                        val route = Destination.createSettingsRoute()
                        navController.navigate(route)
                    },
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                    widthSizeClass = widthSizeClass
                )
            }

            composable(
                route = Destination.STUDIO_CONTENT_ROUTE,
                arguments = listOf(
                    navArgument("studioName") { type = NavType.StringType }
                )
            ) {
                LibraryContentScreen(
                    onItemClick = { item ->
                        val route = Destination.createItemDetailRoute(item.id.toString())
                        navController.navigate(route)
                    },
                    onProfileClick = {
                        val route = Destination.createSettingsRoute()
                        navController.navigate(route)
                    },
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                    widthSizeClass = widthSizeClass
                )
            }

            composable(
                route = Destination.ITEM_DETAIL_ROUTE,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.StringType }
                )
            ) {
                ItemDetailScreen(
                    navController = navController,
                    onPlayClick = { item, selection ->
                        PlayerLauncher.launch(
                            context = navController.context,
                            itemId = item.id,
                            mediaSourceId = selection?.mediaSourceId
                                ?: item.sources.firstOrNull()?.id ?: "",
                            audioStreamIndex = selection?.audioStreamIndex,
                            subtitleStreamIndex = selection?.subtitleStreamIndex,
                            startPositionMs = selection?.startPositionMs ?: 0L
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    widthSizeClass = widthSizeClass
                )
            }

            composable(
                route = Destination.EPISODE_LIST_ROUTE,
                arguments = listOf(
                    navArgument("seasonId") { type = NavType.StringType },
                    navArgument("seasonName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                ItemDetailScreen(
                    onPlayClick = { item, selection ->
                        if (selection != null) {
                            PlayerLauncher.launch(
                                context = navController.context,
                                itemId = item.id,
                                mediaSourceId = selection.mediaSourceId,
                                audioStreamIndex = selection.audioStreamIndex,
                                subtitleStreamIndex = selection.subtitleStreamIndex,
                                startPositionMs = selection.startPositionMs
                            )
                        }
                    },
                    navController = navController,
                    widthSizeClass = widthSizeClass
                )
            }

            composable(
                route = Destination.PERSON_ROUTE,
                arguments = listOf(
                    navArgument("personId") { type = NavType.StringType }
                )
            ) {
                PersonScreen(
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                    widthSizeClass = widthSizeClass
                )
            }

            composable(Destination.FAVORITES.route) {
                FavoritesScreen(
                    onItemClick = { item ->
                        val route = Destination.createItemDetailRoute(item.id.toString())
                        navController.navigate(route)
                    },
                    onPersonClick = { personId ->
                        val route = Destination.createPersonRoute(personId)
                        navController.navigate(route)
                    },
                    modifier = Modifier.fillMaxSize(),
                    mainUiState = mainUiState,
                    navController = navController,
                    widthSizeClass = widthSizeClass
                )
            }

            composable(Destination.WATCHLIST.route) {
                WatchlistScreen(
                    onItemClick = { item ->
                        val route = Destination.createItemDetailRoute(item.id.toString())
                        navController.navigate(route)
                    },
                    modifier = Modifier.fillMaxSize(),
                    mainUiState = mainUiState,
                    navController = navController,
                    widthSizeClass = widthSizeClass
                )
            }

            composable(Destination.REQUESTS.route) {
                RequestsScreen(
                    onSearchClick = {
                        navController.navigate(Destination.SEARCH_ROUTE)
                    },
                    onProfileClick = {
                        val route = Destination.createSettingsRoute()
                        navController.navigate(route)
                    },
                    mainUiState = mainUiState,
                    onNavigateToFilteredMedia = { filterParams ->
                        val route = Destination.createFilteredMediaRoute(
                            filterType = filterParams.type.name,
                            filterId = filterParams.id,
                            filterName = filterParams.name
                        )
                        navController.navigate(route)
                    },
                    onItemClick = { jellyfinItemId ->
                        val route = Destination.createItemDetailRoute(jellyfinItemId)
                        navController.navigate(route)
                    },
                    modifier = Modifier.fillMaxSize(),
                    widthSizeClass = widthSizeClass
                )
            }

            composable(
                route = Destination.FILTERED_MEDIA_ROUTE,
                arguments = listOf(
                    navArgument("filterType") { type = NavType.StringType },
                    navArgument("filterId") { type = NavType.IntType },
                    navArgument("filterName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val filterTypeString =
                    backStackEntry.arguments?.getString("filterType") ?: return@composable
                val filterId = backStackEntry.arguments?.getInt("filterId") ?: return@composable
                val filterName =
                    backStackEntry.arguments?.getString("filterName") ?: return@composable

                val filterType = FilterType.valueOf(filterTypeString)
                val filterParams = FilterParams(filterType, filterId, filterName)

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
                    onItemClick = { jellyfinItemId ->
                        val route = Destination.createItemDetailRoute(jellyfinItemId)
                        navController.navigate(route)
                    },
                    modifier = Modifier.fillMaxSize(),
                    widthSizeClass = widthSizeClass
                )
            }

            composable(Destination.SEARCH_ROUTE) {
                SearchScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onItemClick = { item ->
                        val route = Destination.createItemDetailRoute(item.id.toString())
                        navController.navigate(route)
                    },
                    onGenreClick = { genre ->
                        val route = Destination.createGenreResultsRoute(genre)
                        navController.navigate(route)
                    },
                    modifier = Modifier.fillMaxSize(),
                    widthSizeClass = widthSizeClass
                )
            }

            composable(
                route = Destination.GENRE_RESULTS_ROUTE,
                arguments = listOf(
                    navArgument("genre") { type = NavType.StringType }
                )
            ) {
                GenreResultsScreen(
                    genre = it.arguments?.getString("genre") ?: "",
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onItemClick = { item ->
                        val route = Destination.createItemDetailRoute(item.id.toString())
                        navController.navigate(route)
                    },
                    modifier = Modifier.fillMaxSize(),
                    widthSizeClass = widthSizeClass
                )
            }

            composable(Destination.SETTINGS_ROUTE) {
                SettingsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onLogoutComplete = {
                        // Logout is handled by MainViewModel observing auth state
                        // MainActivity will automatically show LoginScreen
                    },
                    onLicensesClick = {
                        val route = Destination.createLicensesRoute()
                        navController.navigate(route)
                    },
                    onDownloadClick = {
                        val route = Destination.createDownloadSettingsRoute()
                        navController.navigate(route)
                    },
                    onPlayerOptionsClick = {
                        val route = Destination.createPlayerOptionsRoute()
                        navController.navigate(route)
                    },
                    onAppearanceOptionsClick = {
                        val route = Destination.createAppearanceOptionsRoute()
                        navController.navigate(route)
                    },
                    onServerManagementClick = {
                        val route = Destination.createServerManagementRoute()
                        navController.navigate(route)
                    }
                )
            }

            composable(Destination.DOWNLOAD_SETTINGS_ROUTE) {
                DownloadSettingsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    offlineModeManager = offlineModeManager,
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(Destination.PLAYER_OPTIONS_ROUTE) {
                PlayerOptionsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(Destination.APPEARANCE_OPTIONS_ROUTE) {
                AppearanceOptionsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Destination.LICENSES_ROUTE) {
                LicensesScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Destination.SERVER_MANAGEMENT_ROUTE) {
                ServerManagementScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onAddServerClick = {
                        val route = Destination.createAddEditServerRoute(serverId = null)
                        navController.navigate(route)
                    },
                    onEditServerClick = { serverId ->
                        val route = Destination.createAddEditServerRoute(serverId = serverId)
                        navController.navigate(route)
                    }
                )
            }

            composable(
                route = Destination.ADD_EDIT_SERVER_ROUTE,
                arguments = listOf(
                    navArgument("serverId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                AddEditServerScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
    GlobalUpdateDialog(updateManager = updateManager)
}