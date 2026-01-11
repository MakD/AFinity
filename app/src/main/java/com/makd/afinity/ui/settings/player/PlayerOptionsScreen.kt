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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
        .collectAsStateWithLifecycle(
            initialValue = SubtitlePreferences.DEFAULT
        )

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
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PlaybackOptionsSection(
                    autoPlay = uiState.autoPlay,
                    skipIntroEnabled = uiState.skipIntroEnabled,
                    skipOutroEnabled = uiState.skipOutroEnabled,
                    useExoPlayer = uiState.useExoPlayer,
                    pipGestureEnabled = uiState.pipGestureEnabled,
                    pipBackgroundPlay = uiState.pipBackgroundPlay,
                    onPipBackgroundPlayToggle = viewModel::togglePipBackgroundPlay,
                    onAutoPlayToggle = viewModel::toggleAutoPlay,
                    onSkipIntroToggle = viewModel::toggleSkipIntro,
                    onSkipOutroToggle = viewModel::toggleSkipOutro,
                    onUseExoPlayerToggle = viewModel::toggleUseExoPlayer,
                    onPipGestureToggle = viewModel::togglePipGesture
                )
            }

            item {
                PlayerUISection(
                    logoAutoHide = uiState.logoAutoHide,
                    onLogoAutoHideToggle = viewModel::toggleLogoAutoHide,
                    defaultVideoZoomMode = uiState.defaultVideoZoomMode,
                    onVideoZoomModeChange = viewModel::setDefaultVideoZoomMode
                )
            }

            item {
                SubtitleCustomizationSection(
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

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PlaybackOptionsSection(
    autoPlay: Boolean,
    skipIntroEnabled: Boolean,
    skipOutroEnabled: Boolean,
    useExoPlayer: Boolean,
    pipGestureEnabled: Boolean,
    pipBackgroundPlay: Boolean,
    onAutoPlayToggle: (Boolean) -> Unit,
    onSkipIntroToggle: (Boolean) -> Unit,
    onSkipOutroToggle: (Boolean) -> Unit,
    onUseExoPlayerToggle: (Boolean) -> Unit,
    onPipGestureToggle: (Boolean) -> Unit,
    onPipBackgroundPlayToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerSettingsSection(
        modifier = modifier
    ) {
        PlayerSettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_video_settings),
            title = "Use ExoPlayer",
            subtitle = "Uses LibMPV when disabled",
            checked = useExoPlayer,
            onCheckedChange = onUseExoPlayerToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        PlayerSettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_pip),
            title = "Picture-in-Picture Home Gesture",
            subtitle = "Use home button or gesture to enter picture-in-picture",
            checked = pipGestureEnabled,
            onCheckedChange = onPipGestureToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        PlayerSettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_headphones),
            title = "Background Play in PiP",
            subtitle = "Continue playing audio when screen is turned off while in Picture-in-Picture mode",
            checked = pipBackgroundPlay,
            onCheckedChange = onPipBackgroundPlayToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        PlayerSettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_play_arrow),
            title = "Auto-play",
            subtitle = "Automatically play next episode",
            checked = autoPlay,
            onCheckedChange = onAutoPlayToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        PlayerSettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_skip_next),
            title = "Skip Intro",
            subtitle = "Show the Skip Intro Button",
            checked = skipIntroEnabled,
            onCheckedChange = onSkipIntroToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        PlayerSettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_fast_forward),
            title = "Skip Outro",
            subtitle = "Show the Skip Outro Button",
            checked = skipOutroEnabled,
            onCheckedChange = onSkipOutroToggle
        )
    }
}

@Composable
private fun PlayerUISection(
    logoAutoHide: Boolean,
    onLogoAutoHideToggle: (Boolean) -> Unit,
    defaultVideoZoomMode: VideoZoomMode,
    onVideoZoomModeChange: (VideoZoomMode) -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerSettingsSection(
        modifier = modifier
    ) {
        PlayerSettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_visibility),
            title = "Auto-hide Logo/Title",
            subtitle = "Logo/title auto-hides with controls",
            checked = logoAutoHide,
            onCheckedChange = onLogoAutoHideToggle
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        VideoZoomModeDropdownItem(
            selectedMode = defaultVideoZoomMode,
            onModeSelected = onVideoZoomModeChange
        )
    }
}

@Composable
private fun PlayerSettingsSection(
    title: String? = null,
    icon: Painter? = null,
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            if (title != null && icon != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            content()
        }
    }
}

@Composable
private fun PlayerSettingsSwitchItem(
    icon: Painter,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                disabledCheckedThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                disabledCheckedTrackColor = MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = 0.5f
                )
            )
        )
    }
}

@Composable
private fun SubtitleCustomizationSection(
    subtitlePrefs: SubtitlePreferences,
    useExoPlayer: Boolean,
    onUpdate: (SubtitlePreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerSettingsSection(
        title = "Subtitle Customization",
        icon = painterResource(id = R.drawable.ic_subtitle_format),
        modifier = modifier
    ) {
        SubtitlePreview(
            subtitlePrefs = subtitlePrefs,
            useExoPlayer = useExoPlayer,
            modifier = Modifier.padding(16.dp)
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        ColorPickerItem(
            title = "Text Color",
            color = subtitlePrefs.textColor,
            onColorChange = { onUpdate(subtitlePrefs.copy(textColor = it)) }
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        SubtitleSliderItem(
            title = "Text Size",
            value = subtitlePrefs.textSize,
            valueRange = 0.5f..2.0f,
            onValueChange = { onUpdate(subtitlePrefs.copy(textSize = it)) }
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        PlayerSettingsSwitchItem(
            icon = painterResource(id = R.drawable.ic_bold),
            title = "Bold",
            subtitle = "Make subtitle text bold",
            checked = subtitlePrefs.bold,
            onCheckedChange = { onUpdate(subtitlePrefs.copy(bold = it)) }
        )

        if (!useExoPlayer) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            PlayerSettingsSwitchItem(
                icon = painterResource(id = R.drawable.ic_italic),
                title = "Italic",
                subtitle = "Make subtitle text italic",
                checked = subtitlePrefs.italic,
                onCheckedChange = { onUpdate(subtitlePrefs.copy(italic = it)) }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

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

        when (subtitlePrefs.outlineStyle) {
            SubtitleOutlineStyle.OUTLINE, SubtitleOutlineStyle.DROP_SHADOW -> {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                val colorTitle =
                    if (subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DROP_SHADOW) {
                        "Drop Shadow Color"
                    } else {
                        "Outline Color"
                    }

                ColorPickerItem(
                    title = colorTitle,
                    color = subtitlePrefs.outlineColor,
                    onColorChange = { onUpdate(subtitlePrefs.copy(outlineColor = it)) }
                )

                if (!useExoPlayer) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    val sizeTitle =
                        if (subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DROP_SHADOW) {
                            "Drop Shadow Offset"
                        } else {
                            "Outline Size"
                        }

                    SubtitleSliderItem(
                        title = sizeTitle,
                        value = subtitlePrefs.outlineSize,
                        valueRange = 0f..10f,
                        onValueChange = { onUpdate(subtitlePrefs.copy(outlineSize = it)) }
                    )
                }
            }

            SubtitleOutlineStyle.BACKGROUND_BOX -> {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                ColorPickerItem(
                    title = "Background Color",
                    color = subtitlePrefs.backgroundColor,
                    onColorChange = { onUpdate(subtitlePrefs.copy(backgroundColor = it)) }
                )
            }

            else -> {
                // NONE, RAISED, DEPRESSED - no additional color picker
            }
        }

        if (useExoPlayer) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            ColorPickerItem(
                title = "Window Color",
                color = subtitlePrefs.windowColor,
                onColorChange = { onUpdate(subtitlePrefs.copy(windowColor = it)) }
            )
        }

        if (!useExoPlayer) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            SubtitleDropdownItem(
                title = "Vertical Position",
                selectedValue = subtitlePrefs.verticalPosition.displayName,
                options = SubtitleVerticalPosition.entries.map { it.displayName },
                onValueChange = { selected ->
                    val position =
                        SubtitleVerticalPosition.entries.first { it.displayName == selected }
                    onUpdate(subtitlePrefs.copy(verticalPosition = position))
                },
                icon = painterResource(id = R.drawable.ic_vertical)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

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

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        ResetToDefaultsButton(
            onClick = { onUpdate(SubtitlePreferences.DEFAULT) }
        )
    }
}

@Composable
private fun SubtitleSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format(Locale.US, "%.1f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(8.dp)
                )
            },
            modifier = Modifier
                .height(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun SubtitleDropdownItem(
    title: String,
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter = painterResource(id = R.drawable.ic_settings)
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = selectedValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            Icon(
                painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                        leadingIcon = if (selectedValue == option) {
                            {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPickerItem(
    modifier: Modifier = Modifier,
    title: String,
    color: Int,
    onColorChange: (Int) -> Unit,
    subtitle: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    2.dp,
                    MaterialTheme.colorScheme.outline,
                    CircleShape
                )
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = String.format("#%06X", 0xFFFFFF and color),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDialog) {
        SimpleColorPickerDialog(
            initialColor = color,
            onColorSelected = { selectedColor ->
                onColorChange(selectedColor)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
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
            .height(80.dp)
            .background(
                androidx.compose.ui.graphics.Color.DarkGray,
                RoundedCornerShape(8.dp)
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

@Composable
private fun VideoZoomModeDropdownItem(
    selectedMode: VideoZoomMode,
    onModeSelected: (VideoZoomMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf(VideoZoomMode.FIT, VideoZoomMode.ZOOM, VideoZoomMode.STRETCH)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_fullscreen),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Default Video Mode",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = selectedMode.getDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            Icon(
                painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                modes.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = mode.getDisplayName(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        onClick = {
                            onModeSelected(mode)
                            expanded = false
                        },
                        leadingIcon = if (selectedMode == mode) {
                            {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetToDefaultsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_refresh),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Reset to Defaults")
    }
}
