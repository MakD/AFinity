package com.makd.afinity.ui.settings.player

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.player.SubtitleHorizontalAlignment
import com.makd.afinity.data.models.player.SubtitleOutlineStyle
import com.makd.afinity.data.models.player.SubtitlePreferences
import com.makd.afinity.data.models.player.SubtitleVerticalPosition
import com.makd.afinity.data.models.player.VideoZoomMode
import com.makd.afinity.di.PreferencesEntryPoint
import com.makd.afinity.ui.settings.SettingsViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val preferencesRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PreferencesEntryPoint::class.java
        ).preferencesRepository()
    }
    val subtitlePrefs by preferencesRepository.getSubtitlePreferencesFlow()
        .collectAsStateWithLifecycle(initialValue = SubtitlePreferences.DEFAULT)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Player Options",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsGroup(title = "Engine & Behavior") {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_video_settings),
                        title = "Use ExoPlayer",
                        subtitle = "Uses LibMPV when disabled",
                        checked = uiState.useExoPlayer,
                        onCheckedChange = viewModel::toggleUseExoPlayer
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_play_arrow),
                        title = "Auto-play",
                        subtitle = "Automatically play next episode",
                        checked = uiState.autoPlay,
                        onCheckedChange = viewModel::toggleAutoPlay
                    )
                }
            }

            item {
                SettingsGroup(title = "Picture-in-Picture") {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_pip),
                        title = "Home Gesture",
                        subtitle = "Enter PiP when swiping home",
                        checked = uiState.pipGestureEnabled,
                        onCheckedChange = viewModel::togglePipGesture
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_headphones),
                        title = "Background Audio",
                        subtitle = "Keep playing audio when screen is off",
                        checked = uiState.pipBackgroundPlay,
                        onCheckedChange = viewModel::togglePipBackgroundPlay
                    )
                }
            }

            item {
                SettingsGroup(title = "Interface") {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_skip_next),
                        title = "Skip Intro Button",
                        subtitle = "Show button when intro is detected",
                        checked = uiState.skipIntroEnabled,
                        onCheckedChange = viewModel::toggleSkipIntro
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_fast_forward),
                        title = "Skip Outro Button",
                        subtitle = "Show button when outro is detected",
                        checked = uiState.skipOutroEnabled,
                        onCheckedChange = viewModel::toggleSkipOutro
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_visibility),
                        title = "Auto-hide Logo",
                        subtitle = "Hide title logo with player controls",
                        checked = uiState.logoAutoHide,
                        onCheckedChange = viewModel::toggleLogoAutoHide
                    )
                    SettingsDivider()
                    VideoZoomModeSelectorItem(
                        selectedMode = uiState.defaultVideoZoomMode,
                        onModeSelected = viewModel::setDefaultVideoZoomMode
                    )
                }
            }

            item {
                SettingsGroup(title = "Subtitles") {
                    Box(modifier = Modifier.padding(16.dp)) {
                        SubtitlePreview(
                            subtitlePrefs = subtitlePrefs,
                            useExoPlayer = uiState.useExoPlayer
                        )
                    }

                    SettingsDivider()

                    SubtitleCustomizationContent(
                        subtitlePrefs = subtitlePrefs,
                        useExoPlayer = uiState.useExoPlayer,
                        onUpdate = { updatedPrefs ->
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                                .launch {
                                    preferencesRepository.setSubtitlePreferences(updatedPrefs)
                                }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    )
}

@Composable
private fun SettingsItem(
    icon: Painter? = null,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
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
    enabled: Boolean = true
) {
    SettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else null,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.scale(0.8f)
            )
        }
    )
}

@Composable
private fun VideoZoomModeSelectorItem(
    selectedMode: VideoZoomMode,
    onModeSelected: (VideoZoomMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf(VideoZoomMode.FIT, VideoZoomMode.ZOOM, VideoZoomMode.STRETCH)

    Box {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_fullscreen),
            title = "Default Zoom",
            subtitle = selectedMode.getDisplayName(),
            onClick = { expanded = true },
            trailing = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            modes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.getDisplayName()) },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    },
                    leadingIcon = if (selectedMode == mode) {
                        {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun SubtitleCustomizationContent(
    subtitlePrefs: SubtitlePreferences,
    useExoPlayer: Boolean,
    onUpdate: (SubtitlePreferences) -> Unit
) {
    ColorPickerItem(
        title = "Text Color",
        color = subtitlePrefs.textColor,
        onColorChange = { onUpdate(subtitlePrefs.copy(textColor = it)) }
    )

    SettingsDivider()

    SubtitleSliderItem(
        title = "Text Size",
        value = subtitlePrefs.textSize,
        valueRange = 0.5f..2.0f,
        onValueChange = { onUpdate(subtitlePrefs.copy(textSize = it)) }
    )

    SettingsDivider()

    SettingsSwitchItem(
        icon = painterResource(id = R.drawable.ic_bold),
        title = "Bold Text",
        subtitle = "Make subtitles thicker",
        checked = subtitlePrefs.bold,
        onCheckedChange = { onUpdate(subtitlePrefs.copy(bold = it)) }
    )

    if (!useExoPlayer) {
        SettingsDivider()
        SettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_italic),
            title = "Italic Text",
            subtitle = "Slant text style",
            checked = subtitlePrefs.italic,
            onCheckedChange = { onUpdate(subtitlePrefs.copy(italic = it)) }
        )
    }

    SettingsDivider()

    SubtitleDropdownItem(
        title = "Outline Style",
        selectedValue = subtitlePrefs.outlineStyle.displayName,
        options = SubtitleOutlineStyle.entries
            .filter { style ->
                when (style) {
                    SubtitleOutlineStyle.BACKGROUND_BOX -> !useExoPlayer
                    SubtitleOutlineStyle.RAISED, SubtitleOutlineStyle.DEPRESSED -> useExoPlayer
                    else -> true
                }
            }
            .map { it.displayName },
        onValueChange = { selected ->
            val style = SubtitleOutlineStyle.entries.first { it.displayName == selected }
            onUpdate(subtitlePrefs.copy(outlineStyle = style))
        },
        icon = painterResource(id = R.drawable.ic_texture)
    )

    if (subtitlePrefs.outlineStyle == SubtitleOutlineStyle.OUTLINE || subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DROP_SHADOW) {
        SettingsDivider()
        ColorPickerItem(
            title = if (subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DROP_SHADOW) "Shadow Color" else "Outline Color",
            color = subtitlePrefs.outlineColor,
            onColorChange = { onUpdate(subtitlePrefs.copy(outlineColor = it)) }
        )

        if (!useExoPlayer) {
            SettingsDivider()
            SubtitleSliderItem(
                title = if (subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DROP_SHADOW) "Shadow Size" else "Outline Size",
                value = subtitlePrefs.outlineSize,
                valueRange = 0f..10f,
                onValueChange = { onUpdate(subtitlePrefs.copy(outlineSize = it)) }
            )
        }
    } else if (subtitlePrefs.outlineStyle == SubtitleOutlineStyle.BACKGROUND_BOX) {
        SettingsDivider()
        ColorPickerItem(
            title = "Background Color",
            color = subtitlePrefs.backgroundColor,
            onColorChange = { onUpdate(subtitlePrefs.copy(backgroundColor = it)) }
        )
    }

    if (useExoPlayer) {
        SettingsDivider()
        ColorPickerItem(
            title = "Window Color",
            color = subtitlePrefs.windowColor,
            onColorChange = { onUpdate(subtitlePrefs.copy(windowColor = it)) }
        )
    }

    if (!useExoPlayer) {
        SettingsDivider()
        SubtitleDropdownItem(
            title = "Vertical Position",
            selectedValue = subtitlePrefs.verticalPosition.displayName,
            options = SubtitleVerticalPosition.entries.map { it.displayName },
            onValueChange = { selected ->
                val position = SubtitleVerticalPosition.entries.first { it.displayName == selected }
                onUpdate(subtitlePrefs.copy(verticalPosition = position))
            },
            icon = painterResource(id = R.drawable.ic_vertical)
        )

        SettingsDivider()

        SubtitleDropdownItem(
            title = "Horizontal Alignment",
            selectedValue = subtitlePrefs.horizontalAlignment.displayName,
            options = SubtitleHorizontalAlignment.entries.map { it.displayName },
            onValueChange = { selected ->
                val alignment =
                    SubtitleHorizontalAlignment.entries.first { it.displayName == selected }
                onUpdate(subtitlePrefs.copy(horizontalAlignment = alignment))
            },
            icon = painterResource(id = R.drawable.ic_horizontal)
        )
    }

    Box(modifier = Modifier.padding(16.dp)) {
        OutlinedButton(
            onClick = { onUpdate(SubtitlePreferences.DEFAULT) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset Defaults")
        }
    }
}

@Composable
private fun ColorPickerItem(
    title: String,
    color: Int,
    onColorChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    if (showDialog) {
        SimpleColorPickerDialog(
            initialColor = color,
            onColorSelected = {
                onColorChange(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun SubtitleSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format(Locale.US, "%.1f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun SubtitleDropdownItem(
    title: String,
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    icon: Painter
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsItem(
            icon = icon,
            title = title,
            subtitle = selectedValue,
            onClick = { expanded = true },
            trailing = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    leadingIcon = if (selectedValue == option) {
                        {
                            Icon(
                                painterResource(id = R.drawable.ic_check),
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun SimpleColorPickerDialog(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val presetColors = listOf(
        Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE,
        Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.GRAY,
        Color.TRANSPARENT
    )

    var hexInput by remember {
        mutableStateOf(String.format("#%08X", initialColor))
    }
    var isValidHex by remember { mutableStateOf(true) }

    fun parseHexColor(hex: String): Int? {
        return try {
            val cleanHex = hex.removePrefix("#")
            when (cleanHex.length) {
                6 -> "#FF$cleanHex".toColorInt()
                8 -> "#$cleanHex".toColorInt()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        "Preset Colors",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetColors.forEach { presetColor ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(presetColor))
                                    .border(
                                        2.dp,
                                        if (presetColor == initialColor) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        CircleShape
                                    )
                                    .clickable { onColorSelected(presetColor) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Custom Color (Hex)",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { newValue ->
                                hexInput = newValue.uppercase()
                                val parsedColor = parseHexColor(newValue)
                                isValidHex = parsedColor != null
                            },
                            label = { Text("Hex Code") },
                            placeholder = { Text("#AARRGGBB") },
                            isError = !isValidHex,
                            supportingText = if (!isValidHex) {
                                {
                                    Text(
                                        "Invalid hex format",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else null,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isValidHex) {
                                        Color(parseHexColor(hexInput) ?: initialColor)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                        )
                    }

                    Button(
                        onClick = {
                            parseHexColor(hexInput)?.let { color ->
                                onColorSelected(color)
                            }
                        },
                        enabled = isValidHex,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply Custom Color")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SubtitlePreview(
    subtitlePrefs: SubtitlePreferences,
    useExoPlayer: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(
                androidx.compose.ui.graphics.Color.DarkGray,
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = if (subtitlePrefs.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (subtitlePrefs.italic) FontStyle.Italic else FontStyle.Normal,
            fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.15f * subtitlePrefs.textSize
        )

        Box(
            modifier = Modifier
                .background(
                    if (useExoPlayer) {
                        Color(subtitlePrefs.windowColor)
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    RoundedCornerShape(6.dp)
                )
                .padding(
                    horizontal = if (useExoPlayer) 4.dp else 0.dp,
                    vertical = if (useExoPlayer) 2.dp else 0.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .background(
                        when (subtitlePrefs.outlineStyle) {
                            SubtitleOutlineStyle.BACKGROUND_BOX -> Color(subtitlePrefs.backgroundColor)
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        },
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (!useExoPlayer && subtitlePrefs.outlineStyle == SubtitleOutlineStyle.OUTLINE && subtitlePrefs.outlineSize > 0f) {
                    val outlineColor = Color(subtitlePrefs.outlineColor)
                    val offsetStep = (subtitlePrefs.outlineSize * 0.3f).coerceAtMost(2f)

                    listOf(
                        Offset(-offsetStep, -offsetStep),
                        Offset(0f, -offsetStep),
                        Offset(offsetStep, -offsetStep),
                        Offset(-offsetStep, 0f),
                        Offset(offsetStep, 0f),
                        Offset(-offsetStep, offsetStep),
                        Offset(0f, offsetStep),
                        Offset(offsetStep, offsetStep)
                    ).forEach { offset ->
                        Text(
                            text = "Subtitle Preview",
                            style = textStyle,
                            color = outlineColor,
                            modifier = Modifier.offset(offset.x.dp, offset.y.dp)
                        )
                    }
                }

                if (useExoPlayer && subtitlePrefs.outlineStyle == SubtitleOutlineStyle.OUTLINE) {
                    val outlineColor = Color(subtitlePrefs.outlineColor)
                    val offsetStep = 1.5f

                    listOf(
                        Offset(-offsetStep, -offsetStep),
                        Offset(0f, -offsetStep),
                        Offset(offsetStep, -offsetStep),
                        Offset(-offsetStep, 0f),
                        Offset(offsetStep, 0f),
                        Offset(-offsetStep, offsetStep),
                        Offset(0f, offsetStep),
                        Offset(offsetStep, offsetStep)
                    ).forEach { offset ->
                        Text(
                            text = "Subtitle Preview",
                            style = textStyle,
                            color = outlineColor,
                            modifier = Modifier.offset(offset.x.dp, offset.y.dp)
                        )
                    }
                }

                Text(
                    text = "Subtitle Preview",
                    style = textStyle.copy(
                        shadow = when {
                            !useExoPlayer && subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DROP_SHADOW && subtitlePrefs.outlineSize > 0f -> {
                                val shadowOffset = subtitlePrefs.outlineSize
                                Shadow(
                                    color = Color(subtitlePrefs.outlineColor),
                                    offset = Offset(shadowOffset, shadowOffset),
                                    blurRadius = shadowOffset * 1.2f
                                )
                            }

                            useExoPlayer && subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DROP_SHADOW -> {
                                Shadow(
                                    color = Color(subtitlePrefs.outlineColor),
                                    offset = Offset(3f, 3f),
                                    blurRadius = 4f
                                )
                            }

                            useExoPlayer && subtitlePrefs.outlineStyle == SubtitleOutlineStyle.RAISED -> {
                                Shadow(
                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                                    offset = Offset(-1f, -1f),
                                    blurRadius = 2f
                                )
                            }

                            useExoPlayer && subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DEPRESSED -> {
                                Shadow(
                                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                                    offset = Offset(-1f, -1f),
                                    blurRadius = 2f
                                )
                            }

                            else -> null
                        }
                    ),
                    color = Color(subtitlePrefs.textColor)
                )
            }
        }
    }
}