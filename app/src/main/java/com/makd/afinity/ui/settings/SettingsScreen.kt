package com.makd.afinity.ui.settings

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.makd.afinity.R
import com.makd.afinity.core.AppConstants
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.ConnectionIndicatorBadge
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.ui.settings.appearance.AppearanceOptionsScreen
import com.makd.afinity.ui.settings.downloads.DownloadSettingsScreen
import com.makd.afinity.ui.settings.player.PlayerOptionsScreen
import com.makd.afinity.ui.settings.servers.ControlPanelView
import com.makd.afinity.ui.settings.servers.ControlPanelViewModel
import com.makd.afinity.ui.settings.servers.ServerManagementScreen
import com.makd.afinity.ui.settings.update.UpdateSection
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    onBackClick: () -> Unit,
    onLogoutComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val effectiveOfflineMode by viewModel.effectiveOfflineMode.collectAsStateWithLifecycle()
    val connectionType by viewModel.connectionType.collectAsStateWithLifecycle()
    val manualOfflineMode by viewModel.manualOfflineMode.collectAsStateWithLifecycle()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val isJellyseerrAuthenticated by
        viewModel.isJellyseerrAuthenticated.collectAsStateWithLifecycle()
    val isAudiobookshelfAuthenticated by
        viewModel.isAudiobookshelfAuthenticated.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val defaultLangString = stringResource(R.string.lang_system_default)
    val appLanguageSubtitle =
        remember(defaultLangString) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val appLocales = localeManager.applicationLocales

            if (appLocales.isEmpty) defaultLangString
            else appLocales.get(0).let { it.getDisplayName(it).replaceFirstChar(Char::uppercase) }
        }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showJellyseerrLogoutDialog by remember { mutableStateOf(false) }
    var showAudiobookshelfLogoutDialog by remember { mutableStateOf(false) }
    var showQuickConnectDialog by remember { mutableStateOf(false) }
    var showJellyseerrBottomSheet by remember { mutableStateOf(false) }
    var showAudiobookshelfBottomSheet by remember { mutableStateOf(false) }
    var showSessionSwitcherSheet by remember { mutableStateOf(false) }
    var showControlPanel by remember { mutableStateOf(false) }
    val jellyseerrSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val audiobookshelfSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sessionSwitcherSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playerOffset = LocalPlayerOffset.current
    val controlPanelViewModel: ControlPanelViewModel = hiltViewModel(key = "settings_control_panel")
    val isAdmin by controlPanelViewModel.isAdmin.collectAsStateWithLifecycle()
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val defaultDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val customDirective =
        PaneScaffoldDirective(
            maxHorizontalPartitions =
                if (isLandscape) 2 else defaultDirective.maxHorizontalPartitions,
            horizontalPartitionSpacerSize = 0.dp,
            maxVerticalPartitions = defaultDirective.maxVerticalPartitions,
            verticalPartitionSpacerSize = defaultDirective.verticalPartitionSpacerSize,
            defaultPanePreferredWidth = 420.dp,
            excludedBounds = defaultDirective.excludedBounds,
        )

    var activeRole by rememberSaveable { mutableStateOf(ListDetailPaneScaffoldRole.List) }
    var activePane by rememberSaveable { mutableStateOf<SettingsPaneDestination?>(null) }

    val navigator =
        key(isLandscape) {
            val initialHistory =
                mutableListOf<ThreePaneScaffoldDestinationItem<SettingsPaneDestination>>(
                    ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List, null)
                )

            if (activePane != null) {
                initialHistory.add(ThreePaneScaffoldDestinationItem(activeRole, activePane))
            }

            rememberListDetailPaneScaffoldNavigator(
                scaffoldDirective = customDirective,
                initialDestinationHistory = initialHistory,
            )
        }

    LaunchedEffect(navigator.currentDestination) {
        navigator.currentDestination?.let { dest ->
            activeRole = dest.pane
            activePane = dest.contentKey
        }
    }

    val scope = rememberCoroutineScope()
    val isDualPane =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
            navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded

    val listEndPadding = if (isDualPane) 0.dp else 16.dp
    val logoutEndPadding = if (isDualPane) 8.dp else 24.dp

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout(onLogoutComplete)
            },
            onDismiss = { showLogoutDialog = false },
        )
    }

    if (showJellyseerrLogoutDialog) {
        JellyseerrLogoutConfirmationDialog(
            onConfirm = {
                showJellyseerrLogoutDialog = false
                viewModel.logoutFromJellyseerr()
            },
            onDismiss = { showJellyseerrLogoutDialog = false },
        )
    }

    if (showAudiobookshelfLogoutDialog) {
        AudiobookshelfLogoutConfirmationDialog(
            onConfirm = {
                showAudiobookshelfLogoutDialog = false
                viewModel.logoutFromAudiobookshelf()
            },
            onDismiss = { showAudiobookshelfLogoutDialog = false },
        )
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(onDismiss = { showLanguageDialog = false })
    }

    if (showQuickConnectDialog) {
        AuthorizeQuickConnectDialog(
            isAuthorizing = uiState.isAuthorizingQuickConnect,
            isSuccess = uiState.quickConnectAuthSuccess,
            errorMessage = uiState.quickConnectAuthError,
            onAuthorize = { code -> viewModel.authorizeQuickConnect(code) },
            onDismiss = {
                showQuickConnectDialog = false
                viewModel.clearQuickConnectAuthState()
            },
        )
    }

    if (showJellyseerrBottomSheet) {
        if (isDualPane) {
            Dialog(
                onDismissRequest = { showJellyseerrBottomSheet = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = Modifier.width(480.dp).padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    JellyseerrLoginContent(onDismiss = { showJellyseerrBottomSheet = false })
                }
            }
        } else {
            JellyseerrBottomSheet(
                onDismiss = { showJellyseerrBottomSheet = false },
                sheetState = jellyseerrSheetState,
            )
        }
    }

    if (showAudiobookshelfBottomSheet) {
        if (isDualPane) {
            Dialog(
                onDismissRequest = { showAudiobookshelfBottomSheet = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = Modifier.width(480.dp).padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    AudiobookshelfLoginContent(
                        onDismiss = { showAudiobookshelfBottomSheet = false }
                    )
                }
            }
        } else {
            AudiobookshelfBottomSheet(
                onDismiss = { showAudiobookshelfBottomSheet = false },
                sheetState = audiobookshelfSheetState,
            )
        }
    }

    if (showSessionSwitcherSheet) {
        SessionSwitcherBottomSheet(
            onDismiss = { showSessionSwitcherSheet = false },
            onAddAccountClick = { server ->
                showSessionSwitcherSheet = false
                navController.navigate(Destination.createLoginRoute(serverUrl = server.address))
            },
            sheetState = sessionSwitcherSheetState,
        )
    }

    if (showControlPanel) {
        LaunchedEffect(uiState.serverId) {
            uiState.serverId?.let { controlPanelViewModel.initialize(it) }
        }
        Dialog(
            onDismissRequest = { showControlPanel = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(750.dp).padding(16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                uiState.activeServer?.let { activeServer ->
                    ControlPanelView(
                        serverWithCount = activeServer,
                        onBack = { showControlPanel = false },
                        viewModel = controlPanelViewModel,
                    )
                }
                    ?: run {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
            }
        }
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) { Timber.e("Settings error: $error") }
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.dialog_error_title)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane(modifier = Modifier.preferredWidth(420.dp)) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.settings_title),
                                    style =
                                        MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onBackClick) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_chevron_left),
                                        contentDescription = stringResource(R.string.cd_back),
                                    )
                                }
                            },
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) { innerPadding ->
                    val layoutDirection = LocalLayoutDirection.current
                    val customPadding =
                        PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = max(innerPadding.calculateBottomPadding(), playerOffset),
                        )
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(customPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    top = customPadding.calculateTopPadding() + 16.dp,
                                    start = customPadding.calculateStartPadding(layoutDirection),
                                    end = customPadding.calculateEndPadding(layoutDirection),
                                    bottom = customPadding.calculateBottomPadding() + 16.dp,
                                ),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            item(key = "profile") {
                                ProfileHeader(
                                    userName =
                                        uiState.currentUser?.name
                                            ?: stringResource(R.string.unknown_user),
                                    serverName = uiState.serverName,
                                    serverUrl = uiState.serverUrl,
                                    serverVersion = uiState.serverVersion,
                                    userProfileImageUrl = uiState.userProfileImageUrl,
                                    connectionType = connectionType,
                                    modifier =
                                        Modifier.padding(
                                            start = 16.dp,
                                            end = listEndPadding,
                                            top = 8.dp,
                                            bottom = 8.dp,
                                        ),
                                    isAdmin = isAdmin == true,
                                    onControlPanelClick = {
                                        if (isDualPane) {
                                            scope.launch {
                                                navigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    SettingsPaneDestination.ControlPanel,
                                                )
                                            }
                                        } else {
                                            showControlPanel = true
                                        }
                                    },
                                )
                            }

                            item {
                                SettingsGroup(
                                    title = stringResource(R.string.pref_group_general),
                                    endPadding = listEndPadding,
                                ) {
                                    SettingsSwitchItem(
                                        icon = painterResource(id = R.drawable.ic_cloud_off),
                                        title = stringResource(R.string.pref_offline_mode),
                                        subtitle =
                                            if (!isNetworkAvailable)
                                                stringResource(R.string.offline_mode_no_connection)
                                            else if (manualOfflineMode)
                                                stringResource(R.string.offline_mode_manual)
                                            else stringResource(R.string.offline_mode_force),
                                        checked = effectiveOfflineMode,
                                        onCheckedChange = viewModel::toggleOfflineMode,
                                        enabled = isNetworkAvailable,
                                    )
                                    SettingsDivider()
                                    SettingsSwitchItem(
                                        icon = painterResource(id = R.drawable.ic_seerr_logo),
                                        title = stringResource(R.string.pref_discovery_requests),
                                        subtitle =
                                            if (isJellyseerrAuthenticated)
                                                stringResource(R.string.discovery_connected)
                                            else stringResource(R.string.discovery_connect),
                                        checked = isJellyseerrAuthenticated,
                                        onCheckedChange = { enabled ->
                                            if (enabled) showJellyseerrBottomSheet = true
                                            else showJellyseerrLogoutDialog = true
                                        },
                                        enabled = !effectiveOfflineMode,
                                    )
                                    SettingsDivider()
                                    SettingsSwitchItem(
                                        icon =
                                            painterResource(
                                                id = R.drawable.ic_audiobookshelf_light
                                            ),
                                        title = stringResource(R.string.pref_audiobookshelf),
                                        subtitle =
                                            if (isAudiobookshelfAuthenticated)
                                                stringResource(R.string.audiobookshelf_connected)
                                            else stringResource(R.string.audiobookshelf_connect),
                                        checked = isAudiobookshelfAuthenticated,
                                        onCheckedChange = { enabled ->
                                            if (enabled) showAudiobookshelfBottomSheet = true
                                            else showAudiobookshelfLogoutDialog = true
                                        },
                                        enabled = !effectiveOfflineMode,
                                    )
                                    SettingsDivider()
                                    SettingsItem(
                                        icon = painterResource(id = R.drawable.ic_database),
                                        title = stringResource(R.string.pref_downloads_and_storage),
                                        subtitle =
                                            stringResource(
                                                R.string.pref_downloads_and_storage_summary
                                            ),
                                        onClick = {
                                            scope.launch {
                                                navigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    SettingsPaneDestination.Downloads,
                                                )
                                            }
                                        },
                                    )
                                    SettingsDivider()
                                    SettingsItem(
                                        icon = painterResource(id = R.drawable.ic_user),
                                        title = stringResource(R.string.pref_switch_session),
                                        subtitle =
                                            stringResource(R.string.pref_switch_session_summary),
                                        onClick =
                                            if (!effectiveOfflineMode) {
                                                {
                                                    if (isDualPane) {
                                                        scope.launch {
                                                            navigator.navigateTo(
                                                                ListDetailPaneScaffoldRole.Detail,
                                                                SettingsPaneDestination
                                                                    .SessionSwitcher,
                                                            )
                                                        }
                                                    } else {
                                                        showSessionSwitcherSheet = true
                                                    }
                                                }
                                            } else null,
                                    )
                                    SettingsDivider()
                                    SettingsItem(
                                        icon = painterResource(id = R.drawable.ic_quickconnect),
                                        title =
                                            stringResource(R.string.pref_authorize_quickconnect),
                                        subtitle =
                                            stringResource(
                                                R.string.pref_authorize_quickconnect_summary
                                            ),
                                        onClick =
                                            if (!effectiveOfflineMode) {
                                                {
                                                    if (isDualPane) {
                                                        scope.launch {
                                                            navigator.navigateTo(
                                                                ListDetailPaneScaffoldRole.Detail,
                                                                SettingsPaneDestination
                                                                    .QuickConnect,
                                                            )
                                                        }
                                                    } else {
                                                        showQuickConnectDialog = true
                                                    }
                                                }
                                            } else null,
                                    )
                                }
                            }

                            item {
                                SettingsGroup(
                                    title = stringResource(R.string.pref_group_connections),
                                    endPadding = listEndPadding,
                                ) {
                                    SettingsItem(
                                        icon = painterResource(id = R.drawable.ic_server),
                                        title = stringResource(R.string.pref_manage_servers),
                                        subtitle =
                                            stringResource(R.string.pref_manage_servers_summary),
                                        onClick = {
                                            scope.launch {
                                                navigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    SettingsPaneDestination.ServerManagement,
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            item {
                                SettingsGroup(
                                    title = stringResource(R.string.pref_group_preferences),
                                    endPadding = listEndPadding,
                                ) {
                                    SettingsItem(
                                        icon = painterResource(id = R.drawable.ic_color_swatch),
                                        title = stringResource(R.string.pref_appearance),
                                        subtitle = stringResource(R.string.pref_appearance_summary),
                                        onClick = {
                                            scope.launch {
                                                navigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    SettingsPaneDestination.Appearance,
                                                )
                                            }
                                        },
                                    )
                                    SettingsDivider()
                                    SettingsItem(
                                        icon = painterResource(id = R.drawable.ic_language),
                                        title = stringResource(R.string.pref_app_language),
                                        subtitle = appLanguageSubtitle,
                                        onClick = {
                                            if (isDualPane) {
                                                scope.launch {
                                                    navigator.navigateTo(
                                                        ListDetailPaneScaffoldRole.Detail,
                                                        SettingsPaneDestination.Language,
                                                    )
                                                }
                                            } else {
                                                showLanguageDialog = true
                                            }
                                        },
                                    )
                                    SettingsDivider()
                                    SettingsItem(
                                        icon =
                                            painterResource(id = R.drawable.ic_playback_settings),
                                        title = stringResource(R.string.pref_playback),
                                        subtitle = stringResource(R.string.pref_playback_summary),
                                        onClick = {
                                            scope.launch {
                                                navigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    SettingsPaneDestination.Player,
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            item { UpdateSection(endPadding = listEndPadding) }

                            item {
                                SettingsGroup(
                                    title = stringResource(R.string.pref_group_about),
                                    endPadding = listEndPadding,
                                ) {
                                    val buildType =
                                        when {
                                            AppConstants.IS_DEBUG ->
                                                stringResource(R.string.build_debug)
                                            AppConstants.IS_NIGHTLY ->
                                                stringResource(R.string.build_nightly)
                                            else -> stringResource(R.string.build_release)
                                        }
                                    SettingsItem(
                                        icon = painterResource(id = R.drawable.ic_versions),
                                        title = stringResource(R.string.pref_version),
                                        subtitle =
                                            stringResource(
                                                R.string.version_fmt,
                                                AppConstants.VERSION_NAME,
                                                buildType,
                                            ),
                                        onClick = null,
                                    )
                                    SettingsDivider()
                                    SettingsItem(
                                        icon = painterResource(id = R.drawable.ic_source_code),
                                        title = stringResource(R.string.pref_licenses),
                                        subtitle = stringResource(R.string.pref_licenses_summary),
                                        onClick = {
                                            scope.launch {
                                                navigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    SettingsPaneDestination.Licenses,
                                                )
                                            }
                                        },
                                    )
                                    SettingsDivider()
                                    SettingsItem(
                                        icon = painterResource(id = R.drawable.ic_logs),
                                        title = stringResource(R.string.pref_send_logs),
                                        subtitle = stringResource(R.string.pref_send_logs_summary),
                                        onClick =
                                            if (uiState.isExportingLogs) null
                                            else ({ viewModel.exportLogs() }),
                                        trailing =
                                            if (uiState.isExportingLogs)
                                                ({
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp,
                                                    )
                                                })
                                            else null,
                                    )
                                }
                            }

                            item {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .padding(start = 24.dp, end = logoutEndPadding)
                                            .padding(bottom = 32.dp)
                                ) {
                                    Button(
                                        onClick = { showLogoutDialog = true },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor =
                                                    MaterialTheme.colorScheme.errorContainer,
                                                contentColor =
                                                    MaterialTheme.colorScheme.onErrorContainer,
                                            ),
                                        shape = RoundedCornerShape(16.dp),
                                        enabled = !uiState.isLoggingOut,
                                    ) {
                                        if (uiState.isLoggingOut) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                painter =
                                                    painterResource(id = R.drawable.ic_logout),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.action_logout),
                                                style =
                                                    MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
                when (navigator.currentDestination?.contentKey) {
                    is SettingsPaneDestination.Appearance ->
                        AppearanceOptionsScreen(
                            onBackClick = { scope.launch { navigator.navigateBack() } }
                        )
                    is SettingsPaneDestination.Player ->
                        PlayerOptionsScreen(
                            onBackClick = { scope.launch { navigator.navigateBack() } }
                        )
                    is SettingsPaneDestination.Downloads ->
                        DownloadSettingsScreen(
                            onBackClick = { scope.launch { navigator.navigateBack() } },
                            onNavigateToAbsItem = { itemId ->
                                navController.navigate(
                                    Destination.createAudiobookshelfItemRoute(itemId)
                                )
                            },
                        )
                    is SettingsPaneDestination.ServerManagement ->
                        ServerManagementScreen(
                            onBackClick = { scope.launch { navigator.navigateBack() } },
                            onAddServerClick = {
                                navController.navigate(
                                    Destination.createAddEditServerRoute(serverId = null)
                                )
                            },
                            onEditServerClick = { serverId ->
                                navController.navigate(
                                    Destination.createAddEditServerRoute(serverId = serverId)
                                )
                            },
                            isDualPane = isDualPane,
                        )
                    is SettingsPaneDestination.Licenses ->
                        LicensesScreen(onBackClick = { scope.launch { navigator.navigateBack() } })
                    is SettingsPaneDestination.SessionSwitcher ->
                        SessionSwitcherContent(
                            onDismiss = { scope.launch { navigator.navigateBack() } },
                            onAddAccountClick = { server ->
                                scope.launch { navigator.navigateBack() }
                                navController.navigate(
                                    Destination.createLoginRoute(serverUrl = server.address)
                                )
                            },
                        )
                    is SettingsPaneDestination.Language ->
                        LanguagePickerPane(
                            onBackClick = { scope.launch { navigator.navigateBack() } }
                        )
                    is SettingsPaneDestination.QuickConnect ->
                        QuickConnectPane(
                            isAuthorizing = uiState.isAuthorizingQuickConnect,
                            isSuccess = uiState.quickConnectAuthSuccess,
                            errorMessage = uiState.quickConnectAuthError,
                            onAuthorize = { code -> viewModel.authorizeQuickConnect(code) },
                            onBackClick = {
                                scope.launch { navigator.navigateBack() }
                                viewModel.clearQuickConnectAuthState()
                            },
                        )
                    is SettingsPaneDestination.ControlPanel -> {
                        LaunchedEffect(uiState.serverId) {
                            uiState.serverId?.let { controlPanelViewModel.initialize(it) }
                        }
                        uiState.activeServer?.let { activeServer ->
                            ControlPanelView(
                                serverWithCount = activeServer,
                                onBack = { scope.launch { navigator.navigateBack() } },
                                viewModel = controlPanelViewModel,
                            )
                        }
                            ?: Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                    }
                    null -> Box(modifier = Modifier.fillMaxSize())
                }
            }
        },
    )
}

@Composable
fun ProfileHeader(
    userName: String,
    serverName: String?,
    serverUrl: String?,
    serverVersion: String? = null,
    userProfileImageUrl: String?,
    connectionType: ConnectionType,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = false,
    onControlPanelClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 24.dp, bottom = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier.size(96.dp).graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (userProfileImageUrl != null) {
                    AsyncImage(
                        imageUrl = userProfileImageUrl,
                        contentDescription = stringResource(R.string.cd_profile_picture),
                        targetWidth = 96.dp,
                        targetHeight = 96.dp,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = userName.take(1).uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ConnectionIndicatorBadge(
                connectionType = connectionType,
                modifier = Modifier.align(Alignment.BottomEnd),
                size = 28.dp,
                iconSize = 14.dp,
                ringPadding = 3.dp,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = userName,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_server),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = serverName ?: stringResource(R.string.unknown_server),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (serverVersion != null) {
                        VerticalDivider(modifier = Modifier.height(12.dp))
                        Text(
                            text = "v$serverVersion",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (isAdmin && onControlPanelClick != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50),
                    modifier =
                        Modifier.size(32.dp).clip(RoundedCornerShape(50)).clickable {
                            onControlPanelClick()
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_admin_panel_settings),
                            contentDescription = "Control Panel",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_logout),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                stringResource(R.string.dialog_logout_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Text(
                stringResource(R.string.dialog_logout_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.action_logout))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun AudiobookshelfLogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_audiobookshelf_light),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                stringResource(R.string.dialog_disconnect_audiobookshelf_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Text(
                stringResource(R.string.dialog_disconnect_audiobookshelf_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.action_disconnect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun JellyseerrLogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_seerr_logo),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                stringResource(R.string.dialog_disconnect_seerr_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Text(
                stringResource(R.string.dialog_disconnect_seerr_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.action_disconnect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun LanguagePickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val localeManager = remember { context.getSystemService(LocaleManager::class.java) }
    val supportedLocales = remember { LocaleConfig(context).supportedLocales }
    val currentLocale = remember {
        val appLocales = localeManager.applicationLocales
        if (appLocales.isEmpty) null else appLocales.get(0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_select_language_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())
            ) {
                LanguageOption(
                    name = stringResource(R.string.lang_system_default),
                    isSelected = currentLocale == null,
                    onClick = {
                        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
                        onDismiss()
                    },
                )
                if (supportedLocales != null) {
                    repeat(supportedLocales.size()) { index ->
                        val locale = supportedLocales.get(index)
                        LanguageOption(
                            name = locale.getDisplayName(locale).replaceFirstChar(Char::uppercase),
                            isSelected =
                                currentLocale != null &&
                                    locale.language == currentLocale.language &&
                                    locale.country == currentLocale.country,
                            onClick = {
                                localeManager.applicationLocales = LocaleList(locale)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerPane(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val localeManager = remember { context.getSystemService(LocaleManager::class.java) }
    val supportedLocales = remember { LocaleConfig(context).supportedLocales }
    var currentLocale by remember {
        mutableStateOf(localeManager.applicationLocales.let { if (it.isEmpty) null else it.get(0) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pref_app_language)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
        ) {
            LanguageOption(
                name = stringResource(R.string.lang_system_default),
                isSelected = currentLocale == null,
                onClick = {
                    localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
                    currentLocale = null
                    onBackClick()
                },
            )
            if (supportedLocales != null) {
                repeat(supportedLocales.size()) { index ->
                    val locale = supportedLocales.get(index)
                    LanguageOption(
                        name = locale.getDisplayName(locale).replaceFirstChar(Char::uppercase),
                        isSelected =
                            currentLocale != null &&
                                locale.language == currentLocale!!.language &&
                                locale.country == currentLocale!!.country,
                        onClick = {
                            localeManager.applicationLocales = LocaleList(locale)
                            currentLocale = locale
                            onBackClick()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickConnectPane(
    isAuthorizing: Boolean,
    isSuccess: Boolean,
    errorMessage: String?,
    onAuthorize: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    val (digits, focusRequesters) = rememberQuickConnectDigits()
    val code = digits.joinToString("") { it.value }

    LaunchedEffect(isSuccess) {
        if (isSuccess) kotlinx.coroutines.delay(1200)
        if (isSuccess) onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pref_authorize_quickconnect)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_security),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp).padding(top = 8.dp),
            )
            QuickConnectDigitEntry(
                digits = digits,
                focusRequesters = focusRequesters,
                isAuthorizing = isAuthorizing,
                isSuccess = isSuccess,
                errorMessage = errorMessage,
            )
            Button(
                onClick = { onAuthorize(code) },
                enabled = code.length == 6 && !isAuthorizing && !isSuccess,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isAuthorizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.action_authorize))
                }
            }
        }
    }
}

@Composable
private fun AuthorizeQuickConnectDialog(
    isAuthorizing: Boolean,
    isSuccess: Boolean,
    errorMessage: String?,
    onAuthorize: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val (digits, focusRequesters) = rememberQuickConnectDigits()
    val code = digits.joinToString("") { it.value }

    LaunchedEffect(isSuccess) {
        if (isSuccess) kotlinx.coroutines.delay(1200)
        if (isSuccess) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_security),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                stringResource(R.string.dialog_quickconnect_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            QuickConnectDigitEntry(
                digits = digits,
                focusRequesters = focusRequesters,
                isAuthorizing = isAuthorizing,
                isSuccess = isSuccess,
                errorMessage = errorMessage,
            )
        },
        confirmButton = {
            Button(
                onClick = { onAuthorize(code) },
                enabled = code.length == 6 && !isAuthorizing && !isSuccess,
            ) {
                if (isAuthorizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.action_authorize))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun LanguageOption(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}
