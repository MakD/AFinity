package com.makd.afinity.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.makd.afinity.R
import com.makd.afinity.ui.admin.activity.ActivityScreen
import com.makd.afinity.ui.admin.backups.ServerBackupsScreen
import com.makd.afinity.ui.admin.components.AdminDiscardDialog
import com.makd.afinity.ui.admin.components.AdminNavigationGuard
import com.makd.afinity.ui.admin.components.LocalAdminNavigationGuard
import com.makd.afinity.ui.admin.config.ConfigFormScreen
import com.makd.afinity.ui.admin.devices.DevicesScreen
import com.makd.afinity.ui.admin.images.EditImagesScreen
import com.makd.afinity.ui.admin.keys.ApiKeysScreen
import com.makd.afinity.ui.admin.libraries.AddLibraryScreen
import com.makd.afinity.ui.admin.libraries.LibrariesScreen
import com.makd.afinity.ui.admin.libraries.LibraryDetailScreen
import com.makd.afinity.ui.admin.logs.ServerLogViewerScreen
import com.makd.afinity.ui.admin.logs.ServerLogsScreen
import com.makd.afinity.ui.admin.plugins.PluginDetailScreen
import com.makd.afinity.ui.admin.plugins.PluginRepositoriesScreen
import com.makd.afinity.ui.admin.plugins.PluginsScreen
import com.makd.afinity.ui.admin.tasks.TaskDetailScreen
import com.makd.afinity.ui.admin.users.UserDetailScreen
import com.makd.afinity.ui.admin.users.UserParentalScreen
import com.makd.afinity.ui.admin.users.UserPermissionsScreen
import com.makd.afinity.ui.admin.users.UsersScreen
import com.makd.afinity.ui.components.listDetailPaneDirective
import com.makd.afinity.ui.settings.servers.ControlPanelOverviewScreen
import com.makd.afinity.ui.settings.servers.ControlPanelScreen
import com.makd.afinity.ui.settings.servers.ControlPanelViewModel
import com.makd.afinity.ui.settings.servers.ScheduledTasksScreen

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ControlPanelRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ControlPanelViewModel = hiltViewModel(),
) {
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val currentOnBackClick by rememberUpdatedState(onBackClick)

    LaunchedEffect(isAdmin) { if (isAdmin == false) currentOnBackClick() }

    val detailNavController = rememberNavController()
    val navigationGuard = remember { AdminNavigationGuard() }
    var pendingRoute by rememberSaveable { mutableStateOf<String?>(null) }

    val directive = listDetailPaneDirective()
    val scaffoldValue =
        calculateThreePaneScaffoldValue(
            maxHorizontalPartitions = directive.maxHorizontalPartitions,
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
            currentDestination =
                ThreePaneScaffoldDestinationItem<Nothing>(ListDetailPaneScaffoldRole.Detail),
        )
    val isDualPane = scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val currentIsDualPane by rememberUpdatedState(isDualPane)

    val openDetail =
        remember<(String) -> Unit>(detailNavController) {
            { route ->
                detailNavController.navigate(route) {
                    popUpTo(Destination.ADMIN_OVERVIEW_ROUTE)
                    launchSingleTop = true
                }
            }
        }
    val open =
        remember<(String) -> Unit>(navigationGuard, openDetail) {
            { route ->
                if (navigationGuard.hasUnsavedChanges) {
                    pendingRoute = route
                } else {
                    openDetail(route)
                }
            }
        }

    pendingRoute?.let { route ->
        AdminDiscardDialog(
            message = stringResource(R.string.admin_discard_message),
            onDiscard = {
                pendingRoute = null
                openDetail(route)
            },
            onDismiss = { pendingRoute = null },
        )
    }

    CompositionLocalProvider(LocalAdminNavigationGuard provides navigationGuard) {
        ListDetailPaneScaffold(
            directive = directive,
            value = scaffoldValue,
            modifier = modifier,
            listPane = {
                AnimatedPane(modifier = Modifier.preferredWidth(420.dp)) {
                    ControlPanelHub(
                        isDualPane = true,
                        onBackClick = { currentOnBackClick() },
                        onOpen = open,
                        viewModel = viewModel,
                    )
                }
            },
            detailPane = {
                AnimatedPane(
                    modifier =
                        Modifier.windowInsetsPadding(
                            WindowInsets.systemBars
                                .union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                        )
                ) {
                    NavHost(
                        navController = detailNavController,
                        startDestination = Destination.ADMIN_OVERVIEW_ROUTE,
                        modifier = Modifier.fillMaxSize(),
                        predictivePopEnterTransition = { fadeIn(animationSpec = tween(700)) },
                        predictivePopExitTransition = { fadeOut(animationSpec = tween(700)) },
                    ) {
                        composable(Destination.ADMIN_OVERVIEW_ROUTE) {
                            if (currentIsDualPane) {
                                ControlPanelOverviewScreen(
                                    onOpenActivity = { filter ->
                                        open(Destination.createAdminActivityRoute(filter.name))
                                    },
                                    onOpenPlugins = { open(Destination.ADMIN_PLUGINS_ROUTE) },
                                    onOpenTask = { taskId ->
                                        open(Destination.createAdminTaskDetailRoute(taskId))
                                    },
                                    viewModel = viewModel,
                                )
                            } else {
                                ControlPanelHub(
                                    isDualPane = false,
                                    onBackClick = { currentOnBackClick() },
                                    onOpen = open,
                                    viewModel = viewModel,
                                )
                            }
                        }
                        adminDestinations(detailNavController)
                    }
                }
            },
        )
    }
}

@Composable
private fun ControlPanelHub(
    isDualPane: Boolean,
    onBackClick: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: ControlPanelViewModel,
    modifier: Modifier = Modifier,
) {
    ControlPanelScreen(
        onBackClick = onBackClick,
        onOpenUsers = { onOpen(Destination.ADMIN_USERS_ROUTE) },
        onOpenLibraries = { onOpen(Destination.ADMIN_LIBRARIES_ROUTE) },
        onOpenActivity = { filter -> onOpen(Destination.createAdminActivityRoute(filter.name)) },
        onOpenDevices = { onOpen(Destination.ADMIN_DEVICES_ROUTE) },
        onOpenLogs = { onOpen(Destination.ADMIN_LOGS_ROUTE) },
        onOpenPlugins = { onOpen(Destination.ADMIN_PLUGINS_ROUTE) },
        onOpenTasks = { onOpen(Destination.ADMIN_TASKS_ROUTE) },
        onOpenTask = { taskId -> onOpen(Destination.createAdminTaskDetailRoute(taskId)) },
        onOpenApiKeys = { onOpen(Destination.ADMIN_API_KEYS_ROUTE) },
        onOpenBackups = { onOpen(Destination.ADMIN_BACKUPS_ROUTE) },
        onOpenSettings = { formId -> onOpen(Destination.createAdminSettingsRoute(formId)) },
        modifier = modifier,
        isDualPane = isDualPane,
        viewModel = viewModel,
    )
}

private fun NavGraphBuilder.adminDestinations(navController: NavHostController) {
    composable(
        route = Destination.ADMIN_SETTINGS_ROUTE,
        arguments = listOf(navArgument("formId") { type = NavType.StringType }),
    ) {
        ConfigFormScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(Destination.ADMIN_BACKUPS_ROUTE) {
        ServerBackupsScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(Destination.ADMIN_API_KEYS_ROUTE) {
        ApiKeysScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(Destination.ADMIN_TASKS_ROUTE) {
        ScheduledTasksScreen(
            onBackClick = dropUnlessResumed { navController.popBackStack() },
            onOpenTask = { taskId ->
                navController.navigate(Destination.createAdminTaskDetailRoute(taskId)) {
                    launchSingleTop = true
                }
            },
        )
    }

    composable(
        route = Destination.ADMIN_TASK_DETAIL_ROUTE,
        arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
    ) {
        TaskDetailScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(Destination.ADMIN_USERS_ROUTE) {
        UsersScreen(
            onBackClick = dropUnlessResumed { navController.popBackStack() },
            onOpenUser = { userId ->
                navController.navigate(Destination.createAdminUserDetailRoute(userId.toString())) {
                    launchSingleTop = true
                }
            },
        )
    }

    composable(
        route = Destination.ADMIN_USER_DETAIL_ROUTE,
        arguments = listOf(navArgument("userId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val userId = backStackEntry.arguments?.getString("userId").orEmpty()
        UserDetailScreen(
            onBackClick = dropUnlessResumed { navController.popBackStack() },
            onOpenPermissions = {
                navController.navigate(Destination.createAdminUserPermissionsRoute(userId)) {
                    launchSingleTop = true
                }
            },
            onOpenParentalControls = {
                navController.navigate(Destination.createAdminUserParentalRoute(userId)) {
                    launchSingleTop = true
                }
            },
        )
    }

    composable(
        route = Destination.ADMIN_USER_PARENTAL_ROUTE,
        arguments = listOf(navArgument("userId") { type = NavType.StringType }),
    ) {
        UserParentalScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(
        route = Destination.ADMIN_USER_PERMISSIONS_ROUTE,
        arguments = listOf(navArgument("userId") { type = NavType.StringType }),
    ) {
        UserPermissionsScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(Destination.ADMIN_LIBRARIES_ROUTE) {
        LibrariesScreen(
            onBackClick = dropUnlessResumed { navController.popBackStack() },
            onOpenLibrary = { name ->
                navController.navigate(Destination.createAdminLibraryDetailRoute(name)) {
                    launchSingleTop = true
                }
            },
            onAddLibrary = {
                navController.navigate(Destination.ADMIN_LIBRARY_ADD_ROUTE) {
                    launchSingleTop = true
                }
            },
            onEditImage = { itemId ->
                navController.navigate(Destination.createEditImagesRoute(itemId.toString())) {
                    launchSingleTop = true
                }
            },
        )
    }

    composable(
        route = Destination.EDIT_IMAGES_ROUTE,
        arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
    ) {
        EditImagesScreen(onNavigateUp = dropUnlessResumed { navController.popBackStack() })
    }

    composable(Destination.ADMIN_LIBRARY_ADD_ROUTE) {
        AddLibraryScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(
        route = Destination.ADMIN_LIBRARY_DETAIL_ROUTE,
        arguments = listOf(navArgument("libraryName") { type = NavType.StringType }),
    ) {
        LibraryDetailScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(
        route = Destination.ADMIN_ACTIVITY_ROUTE,
        arguments =
            listOf(
                navArgument("filter") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
    ) {
        ActivityScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(Destination.ADMIN_PLUGINS_ROUTE) {
        PluginsScreen(
            onBackClick = dropUnlessResumed { navController.popBackStack() },
            onOpenPlugin = { pluginId ->
                navController.navigate(
                    Destination.createAdminPluginDetailRoute(pluginId.toString())
                ) {
                    launchSingleTop = true
                }
            },
            onOpenRepositories = {
                navController.navigate(Destination.ADMIN_PLUGIN_REPOSITORIES_ROUTE) {
                    launchSingleTop = true
                }
            },
        )
    }

    composable(
        route = Destination.ADMIN_PLUGIN_DETAIL_ROUTE,
        arguments = listOf(navArgument("pluginId") { type = NavType.StringType }),
    ) {
        PluginDetailScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(Destination.ADMIN_PLUGIN_REPOSITORIES_ROUTE) {
        PluginRepositoriesScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(Destination.ADMIN_LOGS_ROUTE) {
        ServerLogsScreen(
            onBackClick = dropUnlessResumed { navController.popBackStack() },
            onOpenLog = { name ->
                navController.navigate(Destination.createAdminLogViewerRoute(name)) {
                    launchSingleTop = true
                }
            },
        )
    }

    composable(
        route = Destination.ADMIN_LOG_VIEWER_ROUTE,
        arguments = listOf(navArgument("name") { type = NavType.StringType }),
    ) {
        ServerLogViewerScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }

    composable(Destination.ADMIN_DEVICES_ROUTE) {
        DevicesScreen(onBackClick = dropUnlessResumed { navController.popBackStack() })
    }
}
