package com.makd.afinity.ui.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.makd.afinity.R
import com.makd.afinity.data.models.common.EpisodeLayout
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
    val episodeLayout by viewModel.episodeLayout.collectAsState()
    val tmdbApiKey by viewModel.tmdbApiKey.collectAsState()
    val mdbListApiKey by viewModel.mdbListApiKey.collectAsState()
    val appFont by viewModel.appFont.collectAsState()
    var showTmdbDialog by remember { mutableStateOf(false) }
    var showMdbListDialog by remember { mutableStateOf(false) }

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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(vertical = 16.dp),
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
                }
            }

            item {
                SettingsGroup(title = "Integrations") {
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_tmdb_short),
                        title = "TMDB API Key",
                        subtitle = if (tmdbApiKey.isNotBlank()) "Configured" else "Not configured",
                        onClick = { showTmdbDialog = true },
                    )

                    SettingsDivider()

                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_mdblist),
                        title = "MDBList API Key",
                        subtitle =
                            if (mdbListApiKey.isNotBlank()) "Configured" else "Not configured",
                        onClick = { showMdbListDialog = true },
                    )
                }
            }
        }
    }

    if (showTmdbDialog) {
        ApiKeyDialog(
            title = "TMDB Configuration",
            initialKey = tmdbApiKey,
            onDismiss = { showTmdbDialog = false },
            onSave = { newKey ->
                viewModel.setTmdbApiKey(newKey)
                showTmdbDialog = false
            },
        )
    }

    if (showMdbListDialog) {
        ApiKeyDialog(
            title = "MDBList Configuration",
            initialKey = mdbListApiKey,
            onDismiss = { showMdbListDialog = false },
            onSave = { newKey ->
                viewModel.setMdbListApiKey(newKey)
                showMdbListDialog = false
            },
        )
    }
}

@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
    )
}

@Composable
private fun SettingsItem(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: Painter,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick =
            if (enabled) {
                { onCheckedChange(!checked) }
            } else null,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                modifier = Modifier.scale(0.8f),
            )
        },
    )
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
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = getThemeModeDisplayName(mode),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (mode.name == currentThemeMode) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        onThemeModeChange(mode.name)
                        expanded = false
                    },
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
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = getEpisodeLayoutDisplayName(layout),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (layout == selectedLayout) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        onLayoutSelected(layout)
                        expanded = false
                    },
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
    onSave: (String) -> Unit,
) {
    var input by remember { mutableStateOf(initialKey) }

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
                    text = "Enter your API key below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(input.trim()) }) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (initialKey.isNotBlank()) {
                    TextButton(
                        onClick = { onSave("") },
                        colors =
                            androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                    ) {
                        Text("Clear Key")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            title = "App Font",
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
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = getFontDisplayName(font),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (font.name == currentFont) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        onFontChange(font.name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun getFontDisplayName(font: AppFont): String {
    return when (font) {
        AppFont.DEFAULT -> "System Default"
        AppFont.GOOGLE_SANS -> "Google Sans Flex"
        AppFont.QUICKSAND -> "Quicksand"
    }
}
