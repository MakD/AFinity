package com.makd.afinity.ui.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.makd.afinity.R
import com.makd.afinity.data.models.common.EpisodeLayout
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.ui.settings.SettingsViewModel
import com.makd.afinity.ui.theme.AppFont
import com.makd.afinity.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceOptionsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val combineLibrarySections by viewModel.combineLibrarySections.collectAsState()
    val homeSortByDateAdded by viewModel.homeSortByDateAdded.collectAsState()
    val navigationDrawerEnabled by viewModel.navigationDrawerEnabled.collectAsState()
    val librariesInDrawer by viewModel.librariesInDrawer.collectAsState()
    val episodeLayout by viewModel.episodeLayout.collectAsState()
    val showRatings by viewModel.showRatings.collectAsState()
    val tmdbApiKey by viewModel.tmdbApiKey.collectAsState()
    val mdbListApiKey by viewModel.mdbListApiKey.collectAsState()
    val omdbApiKey by viewModel.omdbApiKey.collectAsState()
    val appFont by viewModel.appFont.collectAsState()
    var showTmdbDialog by remember { mutableStateOf(false) }
    var showMdbListDialog by remember { mutableStateOf(false) }
    var showOmdbDialog by remember { mutableStateOf(false) }
    val playerOffset = LocalPlayerOffset.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.appearance_title),
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
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val customPadding =
            PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = max(innerPadding.calculateBottomPadding(), playerOffset),
            )
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
            item {
                SettingsGroup(title = stringResource(R.string.settings_group_theme)) {
                    ThemeSelectorItem(
                        currentThemeMode = uiState.themeMode,
                        onThemeModeChange = viewModel::setThemeMode,
                    )
                    SettingsDivider()
                    FontSelectorItem(currentFont = appFont, onFontChange = viewModel::setAppFont)
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_colorize),
                        title = stringResource(R.string.pref_dynamic_colors_title),
                        subtitle = stringResource(R.string.pref_dynamic_colors_summary),
                        checked = uiState.dynamicColors,
                        onCheckedChange = viewModel::toggleDynamicColors,
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.settings_group_navigation)) {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_menu),
                        title = stringResource(R.string.pref_navigation_drawer_title),
                        subtitle = stringResource(R.string.pref_navigation_drawer_summary),
                        checked = navigationDrawerEnabled,
                        onCheckedChange = viewModel::toggleNavigationDrawer,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_video_library),
                        title = stringResource(R.string.pref_libraries_in_drawer_title),
                        subtitle = stringResource(R.string.pref_libraries_in_drawer_summary),
                        checked = librariesInDrawer,
                        onCheckedChange = viewModel::toggleLibrariesInDrawer,
                        enabled = navigationDrawerEnabled,
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.settings_group_home_screen)) {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_view_module),
                        title = stringResource(R.string.pref_combine_library_title),
                        subtitle = stringResource(R.string.pref_combine_library_summary),
                        checked = combineLibrarySections,
                        onCheckedChange = viewModel::toggleCombineLibrarySections,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_calendar),
                        title = stringResource(R.string.pref_sort_date_added_title),
                        subtitle = stringResource(R.string.pref_sort_date_added_summary),
                        checked = homeSortByDateAdded,
                        onCheckedChange = viewModel::toggleHomeSortByDateAdded,
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.settings_group_content_layout)) {
                    EpisodeLayoutSelectorItem(
                        selectedLayout = episodeLayout,
                        onLayoutSelected = viewModel::setEpisodeLayout,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_visibility),
                        title = stringResource(R.string.pref_show_ratings_title),
                        subtitle = stringResource(R.string.pref_show_ratings_summary),
                        checked = showRatings,
                        onCheckedChange = viewModel::toggleShowRatings,
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.pref_group_integrations)) {
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_tmdb_short),
                        title = stringResource(R.string.pref_tmdb_api_key_title),
                        subtitle =
                            if (tmdbApiKey.isNotBlank())
                                stringResource(R.string.pref_api_key_configured)
                            else stringResource(R.string.pref_api_key_not_configured),
                        onClick = { showTmdbDialog = true },
                    )

                    SettingsDivider()

                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_mdblist),
                        title = stringResource(R.string.pref_mdblist_api_key_title),
                        subtitle =
                            if (mdbListApiKey.isNotBlank())
                                stringResource(R.string.pref_api_key_configured)
                            else stringResource(R.string.pref_api_key_not_configured),
                        onClick = { showMdbListDialog = true },
                    )

                    SettingsDivider()

                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_omdb_logo),
                        title = stringResource(R.string.pref_omdb_api_key_title),
                        subtitle =
                            if (omdbApiKey.isNotBlank())
                                stringResource(R.string.pref_api_key_configured)
                            else stringResource(R.string.pref_api_key_not_configured),
                        onClick = { showOmdbDialog = true },
                    )
                }
            }
        }
    }

    if (showTmdbDialog) {
        ApiKeyDialog(
            title = stringResource(R.string.pref_tmdb_config_title),
            initialKey = tmdbApiKey,
            isValidationLoading = uiState.isTmdbKeyValidating,
            validationError = uiState.tmdbKeyValidationError,
            onDismiss = {
                showTmdbDialog = false
                viewModel.clearApiValidationErrors()
            },
            onSave = { newKey ->
                viewModel.validateAndSaveTmdbKey(newKey) {
                    showTmdbDialog = false
                }
            },
        )
    }

    if (showMdbListDialog) {
        ApiKeyDialog(
            title = stringResource(R.string.pref_mdblist_config_title),
            initialKey = mdbListApiKey,
            isValidationLoading = uiState.isMdbListKeyValidating,
            validationError = uiState.mdbListKeyValidationError,
            onDismiss = {
                showMdbListDialog = false
                viewModel.clearApiValidationErrors()
            },
            onSave = { newKey ->
                viewModel.validateAndSaveMdbListKey(newKey) {
                    showMdbListDialog = false
                }
            },
        )
    }

    if (showOmdbDialog) {
        ApiKeyDialog(
            title = stringResource(R.string.pref_omdb_config_title),
            initialKey = omdbApiKey,
            isValidationLoading = uiState.isOmdbKeyValidating,
            validationError = uiState.omdbKeyValidationError,
            onDismiss = {
                showOmdbDialog = false
                viewModel.clearApiValidationErrors()
            },
            onSave = { newKey ->
                viewModel.validateAndSaveOmdbKey(newKey) {
                    showOmdbDialog = false
                }
            },
        )
    }
}

@Composable
private fun ThemeSelectorItem(currentThemeMode: String, onThemeModeChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentTheme = ThemeMode.fromString(currentThemeMode)

    Box {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_dark_mode),
            title = stringResource(R.string.pref_theme_mode_title),
            subtitle = getThemeModeDisplayName(currentTheme),
            onClick = { expanded = true },
            trailing = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(getThemeModeDisplayName(mode)) },
                    onClick = {
                        onThemeModeChange(mode.name)
                        expanded = false
                    },
                    leadingIcon =
                        if (mode.name == currentThemeMode) {
                            {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                )
            }
        }
    }
}

@Composable
private fun EpisodeLayoutSelectorItem(
    selectedLayout: EpisodeLayout,
    onLayoutSelected: (EpisodeLayout) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val layouts = listOf(EpisodeLayout.HORIZONTAL, EpisodeLayout.VERTICAL)

    Box {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_episode_layout),
            title = stringResource(R.string.pref_episode_layout_title),
            subtitle = getEpisodeLayoutDisplayName(selectedLayout),
            onClick = { expanded = true },
            trailing = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            layouts.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(getEpisodeLayoutDisplayName(layout)) },
                    onClick = {
                        onLayoutSelected(layout)
                        expanded = false
                    },
                    leadingIcon =
                        if (layout == selectedLayout) {
                            {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                )
            }
        }
    }
}

@Composable
private fun getThemeModeDisplayName(mode: ThemeMode): String {
    return when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
        ThemeMode.AMOLED -> stringResource(R.string.theme_amoled)
    }
}

@Composable
private fun getEpisodeLayoutDisplayName(layout: EpisodeLayout): String {
    return when (layout) {
        EpisodeLayout.HORIZONTAL -> stringResource(R.string.layout_horizontal)
        EpisodeLayout.VERTICAL -> stringResource(R.string.layout_vertical)
    }
}

@Composable
private fun ApiKeyDialog(
    title: String,
    initialKey: String,
    onDismiss: () -> Unit,
    isValidationLoading: Boolean = false,
    validationError: String? = null,
    onSave: (String) -> Unit,
) {
    var input by remember { mutableStateOf(initialKey) }
    var localError by remember(validationError) { mutableStateOf(validationError) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.api_key_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        localError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    isError = localError != null,
                    trailingIcon = {
                        if (localError != null) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_exclamation_circle),
                                contentDescription = stringResource(R.string.cd_invalid_key),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        } else if (isValidationLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    supportingText = {
                        if (localError != null) {
                            Text(
                                text = localError!!,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(input.trim()) },
                enabled = !isValidationLoading,
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (initialKey.isNotBlank()) {
                    TextButton(
                        onClick = { onSave("") },
                        enabled = !isValidationLoading,
                        colors =
                            androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                    ) {
                        Text(stringResource(R.string.action_clear_key))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isValidationLoading,
                ) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun FontSelectorItem(currentFont: String, onFontChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentAppFont = AppFont.fromString(currentFont)

    Box {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_font),
            title = stringResource(R.string.pref_app_font_title),
            subtitle = getFontDisplayName(currentAppFont),
            onClick = { expanded = true },
            trailing = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AppFont.entries.forEach { font ->
                DropdownMenuItem(
                    text = { Text(getFontDisplayName(font)) },
                    onClick = {
                        onFontChange(font.name)
                        expanded = false
                    },
                    leadingIcon =
                        if (font.name == currentFont) {
                            {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                )
            }
        }
    }
}

@Composable
private fun getFontDisplayName(font: AppFont): String {
    return when (font) {
        AppFont.DEFAULT -> stringResource(R.string.font_system_default)
        AppFont.GOOGLE_SANS -> "Google Sans Flex"
        AppFont.QUICKSAND -> "Quicksand"
        AppFont.IBM_PLEX_SANS -> "IBM Plex Sans"
        AppFont.IBM_PLEX_SANS_CONDENSED -> "IBM Plex Sans Condensed"
    }
}
