package com.makd.afinity.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.makd.afinity.ui.episode.EpisodeListScreen
import com.makd.afinity.ui.library.LibraryContentScreen
import com.makd.afinity.ui.home.HomeScreen
import com.makd.afinity.ui.libraries.LibrariesScreen
import com.makd.afinity.ui.item.ItemDetailScreen
import com.makd.afinity.ui.person.PersonScreen
import com.makd.afinity.ui.player.PlayerScreen
import com.makd.afinity.ui.favorites.FavoritesScreen
import com.makd.afinity.ui.player.PlayerScreenWrapper
import com.makd.afinity.ui.search.SearchScreen
import com.makd.afinity.ui.search.GenreResultsScreen
import com.makd.afinity.ui.settings.SettingsScreen
import org.jellyfin.sdk.model.UUID
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    modifier: Modifier = Modifier,
    viewModel: MainNavigationViewModel = hiltViewModel()
) {
    val appLoadingState by viewModel.appLoadingState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    if (appLoadingState.isLoading) {
        AppSplashScreen(
            progress = appLoadingState.loadingProgress,
            phase = appLoadingState.loadingPhase,
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            val shouldShowBottomBar = currentDestination?.route?.let { route ->
                !route.startsWith("library_content/") &&
                        !route.startsWith("item_detail/") &&
                        !route.startsWith("episodes/") &&
                        !route.startsWith("player/") &&
                        !route.startsWith("person/") &&
                        route != "search" &&
                        !route.startsWith("genre_results/") &&
                        route != "settings"
            } ?: true

            AnimatedVisibility(
                visible = shouldShowBottomBar,
                enter = fadeIn(
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ),
                exit = fadeOut(
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                )
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Destination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = destination.title
                                )
                            },
                            label = {
                                Text(
                                    text = destination.title,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
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
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier
                .fillMaxSize()
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(
                    onItemClick = { item ->
                        val route = Destination.createItemDetailRoute(item.id.toString())
                        navController.navigate(route)
                    },
                    onPlayClick = { item ->
                        val route = Destination.createPlayerRoute(
                            itemId = item.id.toString(),
                            mediaSourceId = item.sources.firstOrNull()?.id ?: "",
                            startPositionMs = if (item.playbackPositionTicks > 0) {
                                item.playbackPositionTicks / 10000
                            } else 0L
                        )
                        navController.navigate(route)
                    },
                    onProfileClick = {
                        val route = Destination.createSettingsRoute()
                        navController.navigate(route)
                    },
                    navController = navController,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
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
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onItemClick = { item ->
                        val route = Destination.createItemDetailRoute(item.id.toString())
                        navController.navigate(route)
                    },
                    onProfileClick = {
                        val route = Destination.createSettingsRoute()
                        navController.navigate(route)
                    },
                    navController = navController,
                    modifier = Modifier.fillMaxSize()
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
                        val route = Destination.createPlayerRoute(
                            itemId = item.id.toString(),
                            mediaSourceId = selection?.mediaSourceId ?: item.sources.firstOrNull()?.id ?: "",
                            audioStreamIndex = selection?.audioStreamIndex,
                            subtitleStreamIndex = selection?.subtitleStreamIndex,
                            startPositionMs = selection?.startPositionMs ?: 0L
                        )
                        navController.navigate(route)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(
                route = Destination.EPISODE_LIST_ROUTE,
                arguments = listOf(
                    navArgument("seasonId") { type = NavType.StringType },
                    navArgument("seasonName") { type = NavType.StringType }
                )
            ) {
                EpisodeListScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onEpisodeClick = { episode ->
                        // TODO: Navigate to player screen
                    },
                    navController = navController,
                    modifier = Modifier.fillMaxSize()
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
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(
                route = Destination.PLAYER_ROUTE,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.StringType },
                    navArgument("mediaSourceId") { type = NavType.StringType },
                    navArgument("audioStreamIndex") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("subtitleStreamIndex") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("startPositionMs") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            )

            { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: return@composable
                val mediaSourceId = backStackEntry.arguments?.getString("mediaSourceId") ?: return@composable
                val audioStreamIndex = backStackEntry.arguments?.getInt("audioStreamIndex")?.takeIf { it != -1 }
                val subtitleStreamIndex = backStackEntry.arguments?.getInt("subtitleStreamIndex")?.takeIf { it != -1 }
                val startPositionMs = backStackEntry.arguments?.getLong("startPositionMs") ?: 0L

                PlayerScreenWrapper(
                    itemId = UUID.fromString(itemId),
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    startPositionMs = startPositionMs,
                    navController = navController,
                    onBackPressed = {
                        navController.popBackStack()
                    }
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
                    modifier = Modifier.fillMaxSize()
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
                    modifier = Modifier
                        .fillMaxSize()
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
                    modifier = Modifier
                        .fillMaxSize()
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
                    }
                )
            }
        }
    }
}

@Composable
private fun AppSplashScreen(
    progress: Float,
    phase: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_monochrome),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.Center)
                .offset(y = (-32).dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "AFinity",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Powered By Jellyfin",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = phase.ifEmpty { "Loading..." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(240.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}