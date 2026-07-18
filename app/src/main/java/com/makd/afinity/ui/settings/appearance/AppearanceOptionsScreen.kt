package com.makd.afinity.ui.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val combineLibrarySections by viewModel.combineLibrarySections.collectAsStateWithLifecycle()
    val homeSortByDateAdded by viewModel.homeSortByDateAdded.collectAsStateWithLifecycle()
    val navigationDrawerEnabled by viewModel.navigationDrawerEnabled.collectAsStateWithLifecycle()
    val librariesInDrawer by viewModel.librariesInDrawer.collectAsStateWithLifecycle()
    val episodeLayout by viewModel.episodeLayout.collectAsStateWithLifecycle()
    val showRatings by viewModel.showRatings.collectAsStateWithLifecycle()
    val appFont by viewModel.appFont.collectAsStateWithLifecycle()
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
        }
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
