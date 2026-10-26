package com.makd.afinity.ui.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.common.CardSize
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.models.common.DrawerLibrariesMode
import com.makd.afinity.data.models.common.EpisodeLayout
import com.makd.afinity.data.models.common.HeroStyle
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityBackButton
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.EndAlignedDropdownMenu
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
    onCustomSectionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val combineLibrarySections by viewModel.combineLibrarySections.collectAsStateWithLifecycle()
    val homeSortByDateAdded by viewModel.homeSortByDateAdded.collectAsStateWithLifecycle()
    val latestRowsVisible by viewModel.latestRowsVisible.collectAsStateWithLifecycle()
    val nextUpRowVisible by viewModel.nextUpRowVisible.collectAsStateWithLifecycle()
    val mergeContinueWatchingNextUp by
        viewModel.mergeContinueWatchingNextUp.collectAsStateWithLifecycle()
    val nextUpMaxDays by viewModel.nextUpMaxDays.collectAsStateWithLifecycle()
    val navigationDrawerEnabled by viewModel.navigationDrawerEnabled.collectAsStateWithLifecycle()
    val drawerLibrariesMode by viewModel.drawerLibrariesMode.collectAsStateWithLifecycle()
    val sideSheetEnabled by viewModel.sideSheetEnabled.collectAsStateWithLifecycle()
    val episodeLayout by viewModel.episodeLayout.collectAsStateWithLifecycle()
    val detailLayout by viewModel.detailLayout.collectAsStateWithLifecycle()
    val cardSize by viewModel.cardSize.collectAsStateWithLifecycle()
    val heroStyle by viewModel.heroStyle.collectAsStateWithLifecycle()
    val showRatings by viewModel.showRatings.collectAsStateWithLifecycle()
    val showAwards by viewModel.showAwards.collectAsStateWithLifecycle()
    val lastFmPopularSort by viewModel.lastFmPopularSort.collectAsStateWithLifecycle()
    val appFont by viewModel.appFont.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopBar(
                title = { AfinityTopAppBarTitle(stringResource(R.string.appearance_title)) },
                navigationIcon = { AfinityBackButton(onClick = onBackClick) },
                backgroundOpacity = backgroundOpacity,
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
                    DrawerLibrariesModeSelectorItem(
                        selectedMode = drawerLibrariesMode,
                        onModeSelected = viewModel::setDrawerLibrariesMode,
                        enabled = navigationDrawerEnabled,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_sidesheet),
                        title = stringResource(R.string.pref_side_sheet_title),
                        subtitle = stringResource(R.string.pref_side_sheet_summary),
                        checked = sideSheetEnabled,
                        onCheckedChange = viewModel::toggleSideSheet,
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.settings_group_home_screen)) {
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_row_custom),
                        title = stringResource(R.string.custom_sections_title),
                        subtitle = stringResource(R.string.custom_sections_settings_summary),
                        onClick = onCustomSectionsClick,
                    )
                    SettingsDivider()
                    HeroStyleSelectorItem(
                        selectedStyle = heroStyle,
                        onStyleSelected = viewModel::setHeroStyle,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_view_module),
                        title = stringResource(R.string.pref_combine_library_title),
                        subtitle =
                            if (latestRowsVisible) {
                                stringResource(R.string.pref_combine_library_summary)
                            } else {
                                stringResource(R.string.pref_requires_latest_rows)
                            },
                        checked = combineLibrarySections,
                        onCheckedChange = viewModel::toggleCombineLibrarySections,
                        enabled = latestRowsVisible,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_calendar),
                        title = stringResource(R.string.pref_sort_date_added_title),
                        subtitle =
                            if (latestRowsVisible) {
                                stringResource(R.string.pref_sort_date_added_summary)
                            } else {
                                stringResource(R.string.pref_requires_latest_rows)
                            },
                        checked = homeSortByDateAdded,
                        onCheckedChange = viewModel::toggleHomeSortByDateAdded,
                        enabled = latestRowsVisible,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_arrows_join_2),
                        title = stringResource(R.string.pref_merge_continue_next_up_title),
                        subtitle =
                            if (nextUpRowVisible) {
                                stringResource(R.string.pref_merge_continue_next_up_summary)
                            } else {
                                stringResource(R.string.pref_requires_next_up_row)
                            },
                        checked = mergeContinueWatchingNextUp,
                        onCheckedChange = viewModel::toggleMergeContinueWatchingNextUp,
                        enabled = nextUpRowVisible,
                    )
                    SettingsDivider()
                    NextUpMaxDaysItem(
                        days = nextUpMaxDays,
                        enabled = nextUpRowVisible,
                        onDaysChange = viewModel::setNextUpMaxDays,
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.settings_group_content_layout)) {
                    CardSizeSelectorItem(
                        selectedSize = cardSize,
                        onSizeSelected = viewModel::setCardSize,
                    )
                    SettingsDivider()
                    DetailLayoutSelectorItem(
                        selectedLayout = detailLayout,
                        onLayoutSelected = viewModel::setDetailLayout,
                    )
                    SettingsDivider()
                    if (detailLayout == DetailLayout.CLASSIC) {
                        EpisodeLayoutSelectorItem(
                            selectedLayout = episodeLayout,
                            onLayoutSelected = viewModel::setEpisodeLayout,
                        )
                        SettingsDivider()
                    }
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_visibility),
                        title = stringResource(R.string.pref_show_ratings_title),
                        subtitle = stringResource(R.string.pref_show_ratings_summary),
                        checked = showRatings,
                        onCheckedChange = viewModel::toggleShowRatings,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_laurel),
                        title = stringResource(R.string.pref_show_awards_title),
                        subtitle = stringResource(R.string.pref_show_awards_summary),
                        checked = showAwards,
                        onCheckedChange = viewModel::toggleShowAwards,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_flame_hot_outline),
                        title = stringResource(R.string.pref_lastfm_popular_sort_title),
                        subtitle = stringResource(R.string.pref_lastfm_popular_sort_summary),
                        checked = lastFmPopularSort,
                        onCheckedChange = viewModel::toggleLastFmPopularSort,
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

        EndAlignedDropdownMenu(
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
private fun NextUpMaxDaysItem(days: Int, enabled: Boolean, onDaysChange: (Int) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsItem(
        icon = painterResource(id = R.drawable.ic_hourglass_empty),
        title = stringResource(R.string.pref_next_up_max_days_title),
        subtitle =
            when {
                !enabled -> stringResource(R.string.pref_requires_next_up_row)
                days == 0 -> stringResource(R.string.label_unlimited)
                else -> pluralStringResource(R.plurals.pref_next_up_max_days_fmt, days, days)
            },
        onClick = { showDialog = true },
        enabled = enabled,
    )

    if (showDialog) {
        NextUpMaxDaysDialog(
            days = days,
            onDismiss = { showDialog = false },
            onConfirm = {
                onDaysChange(it)
                showDialog = false
            },
        )
    }
}

@Composable
private fun NextUpMaxDaysDialog(days: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(days.toString()) }
    val parsed = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.pref_next_up_max_days_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AfinityTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(4) },
                    label = stringResource(R.string.pref_next_up_max_days_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.pref_next_up_max_days_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text(text = stringResource(R.string.custom_sections_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CardSizeSelectorItem(selectedSize: CardSize, onSizeSelected: (CardSize) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_layout_grid),
            title = stringResource(R.string.pref_card_size_title),
            subtitle = getCardSizeDisplayName(selectedSize),
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

        EndAlignedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            CardSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = { Text(getCardSizeDisplayName(size)) },
                    onClick = {
                        onSizeSelected(size)
                        expanded = false
                    },
                    leadingIcon =
                        if (size == selectedSize) {
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
private fun getCardSizeDisplayName(size: CardSize): String {
    return when (size) {
        CardSize.EXTRA_SMALL -> stringResource(R.string.card_size_extra_small)
        CardSize.SMALL -> stringResource(R.string.card_size_small)
        CardSize.DEFAULT -> stringResource(R.string.card_size_default)
        CardSize.LARGE -> stringResource(R.string.card_size_large)
    }
}

@Composable
private fun HeroStyleSelectorItem(
    selectedStyle: HeroStyle,
    onStyleSelected: (HeroStyle) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_carousel_horizontal),
            title = stringResource(R.string.pref_hero_style_title),
            subtitle = getHeroStyleDisplayName(selectedStyle),
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

        EndAlignedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            HeroStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(getHeroStyleDisplayName(style)) },
                    onClick = {
                        onStyleSelected(style)
                        expanded = false
                    },
                    leadingIcon =
                        if (style == selectedStyle) {
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
private fun getHeroStyleDisplayName(style: HeroStyle): String {
    return when (style) {
        HeroStyle.FULL_BLEED -> stringResource(R.string.hero_style_full_bleed)
        HeroStyle.PEEK -> stringResource(R.string.hero_style_peek)
        HeroStyle.INSET_CARD -> stringResource(R.string.hero_style_inset_card)
    }
}

@Composable
private fun DetailLayoutSelectorItem(
    selectedLayout: DetailLayout,
    onLayoutSelected: (DetailLayout) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_article),
            title = stringResource(R.string.pref_detail_layout_title),
            subtitle = getDetailLayoutDisplayName(selectedLayout),
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

        EndAlignedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            DetailLayout.entries.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(getDetailLayoutDisplayName(layout)) },
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
private fun DrawerLibrariesModeSelectorItem(
    selectedMode: DrawerLibrariesMode,
    onModeSelected: (DrawerLibrariesMode) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_folder),
            title = stringResource(R.string.pref_libraries_in_drawer_title),
            subtitle = getDrawerLibrariesModeDisplayName(selectedMode),
            onClick = { expanded = true },
            enabled = enabled,
            trailing = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (enabled) 1f else 0.38f
                        ),
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        EndAlignedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            DrawerLibrariesMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(getDrawerLibrariesModeDisplayName(mode)) },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    },
                    leadingIcon =
                        if (mode == selectedMode) {
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
private fun getDrawerLibrariesModeDisplayName(mode: DrawerLibrariesMode): String {
    return when (mode) {
        DrawerLibrariesMode.OFF -> stringResource(R.string.drawer_libraries_mode_off)
        DrawerLibrariesMode.SCREEN -> stringResource(R.string.drawer_libraries_mode_screen)
        DrawerLibrariesMode.EACH -> stringResource(R.string.drawer_libraries_mode_each)
    }
}

@Composable
private fun getDetailLayoutDisplayName(layout: DetailLayout): String {
    return when (layout) {
        DetailLayout.CLASSIC -> stringResource(R.string.detail_layout_classic)
        DetailLayout.MODERN -> stringResource(R.string.detail_layout_modern)
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
            icon = painterResource(id = R.drawable.ic_list_details),
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

        EndAlignedDropdownMenu(
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

        EndAlignedDropdownMenu(
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
