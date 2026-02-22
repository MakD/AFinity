package com.makd.afinity.ui.settings.player

import android.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.player.MpvAudioOutput
import com.makd.afinity.data.models.player.MpvHwDec
import com.makd.afinity.data.models.player.MpvVideoOutput
import com.makd.afinity.data.models.player.SubtitleHorizontalAlignment
import com.makd.afinity.data.models.player.SubtitleOutlineStyle
import com.makd.afinity.data.models.player.SubtitlePreferences
import com.makd.afinity.data.models.player.SubtitleVerticalPosition
import com.makd.afinity.data.models.player.VideoZoomMode
import com.makd.afinity.di.PreferencesEntryPoint
import com.makd.afinity.ui.settings.SettingsViewModel
import dagger.hilt.android.EntryPointAccessors
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val preferencesRepository = remember {
        EntryPointAccessors.fromApplication(
                context.applicationContext,
                PreferencesEntryPoint::class.java,
            )
            .preferencesRepository()
    }
    val subtitlePrefs by
        preferencesRepository
            .getSubtitlePreferencesFlow()
            .collectAsStateWithLifecycle(initialValue = SubtitlePreferences.DEFAULT)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.player_options_title),
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
                SettingsGroup(title = stringResource(R.string.pref_group_engine)) {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_video_settings),
                        title = stringResource(R.string.pref_use_exoplayer_title),
                        subtitle = stringResource(R.string.pref_use_exoplayer_summary),
                        checked = uiState.useExoPlayer,
                        onCheckedChange = viewModel::toggleUseExoPlayer,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_play_arrow),
                        title = stringResource(R.string.pref_autoplay_title),
                        subtitle = stringResource(R.string.pref_autoplay_summary),
                        checked = uiState.autoPlay,
                        onCheckedChange = viewModel::toggleAutoPlay,
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = !uiState.useExoPlayer,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    SettingsGroup(
                        title = stringResource(R.string.pref_group_mpv),
                        modifier = Modifier.padding(bottom = 24.dp),
                    ) {
                        SubtitleDropdownItem(
                            title = stringResource(R.string.pref_mpv_hwdec_title),
                            selectedOption = uiState.mpvHwDec,
                            options = MpvHwDec.entries.toList(),
                            onValueChange = viewModel::setMpvHwDec,
                            labelProvider = { it.getDisplayName() },
                            icon = painterResource(id = R.drawable.ic_cpu),
                        )
                        SettingsDivider()
                        SubtitleDropdownItem(
                            title = stringResource(R.string.pref_mpv_video_output_title),
                            selectedOption = uiState.mpvVideoOutput,
                            options = MpvVideoOutput.entries.toList(),
                            onValueChange = viewModel::setMpvVideoOutput,
                            labelProvider = { it.getDisplayName() },
                            icon = painterResource(id = R.drawable.ic_video_settings),
                        )
                        SettingsDivider()
                        SubtitleDropdownItem(
                            title = stringResource(R.string.pref_mpv_audio_output_title),
                            selectedOption = uiState.mpvAudioOutput,
                            options = MpvAudioOutput.entries.toList(),
                            onValueChange = viewModel::setMpvAudioOutput,
                            labelProvider = { it.getDisplayName() },
                            icon = painterResource(id = R.drawable.ic_audio),
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.pref_group_language)) {
                    LanguageSelectorItem(
                        title = stringResource(R.string.pref_preferred_audio_language_title),
                        subtitle = stringResource(R.string.pref_preferred_audio_language_summary),
                        selectedCode = uiState.preferredAudioLanguage,
                        onLanguageSelected = viewModel::setPreferredAudioLanguage,
                        icon = painterResource(id = R.drawable.ic_language),
                    )
                    SettingsDivider()
                    LanguageSelectorItem(
                        title = stringResource(R.string.pref_preferred_subtitle_language_title),
                        subtitle =
                            stringResource(R.string.pref_preferred_subtitle_language_summary),
                        selectedCode = uiState.preferredSubtitleLanguage,
                        onLanguageSelected = viewModel::setPreferredSubtitleLanguage,
                        icon = painterResource(id = R.drawable.ic_subtitles),
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.pref_group_pip)) {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_pip),
                        title = stringResource(R.string.pref_pip_gesture_title),
                        subtitle = stringResource(R.string.pref_pip_gesture_summary),
                        checked = uiState.pipGestureEnabled,
                        onCheckedChange = viewModel::togglePipGesture,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_headphones),
                        title = stringResource(R.string.pref_pip_background_title),
                        subtitle = stringResource(R.string.pref_pip_background_summary),
                        checked = uiState.pipBackgroundPlay,
                        onCheckedChange = viewModel::togglePipBackgroundPlay,
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.pref_group_interface)) {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_skip_next),
                        title = stringResource(R.string.pref_skip_intro_title),
                        subtitle = stringResource(R.string.pref_skip_intro_summary),
                        checked = uiState.skipIntroEnabled,
                        onCheckedChange = viewModel::toggleSkipIntro,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_fast_forward),
                        title = stringResource(R.string.pref_skip_outro_title),
                        subtitle = stringResource(R.string.pref_skip_outro_summary),
                        checked = uiState.skipOutroEnabled,
                        onCheckedChange = viewModel::toggleSkipOutro,
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_visibility),
                        title = stringResource(R.string.pref_autohide_logo_title),
                        subtitle = stringResource(R.string.pref_autohide_logo_summary),
                        checked = uiState.logoAutoHide,
                        onCheckedChange = viewModel::toggleLogoAutoHide,
                    )
                    SettingsDivider()
                    VideoZoomModeSelectorItem(
                        selectedMode = uiState.defaultVideoZoomMode,
                        onModeSelected = viewModel::setDefaultVideoZoomMode,
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.pref_group_subtitles)) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        SubtitlePreview(
                            subtitlePrefs = subtitlePrefs,
                            useExoPlayer = uiState.useExoPlayer,
                        )
                    }

                    SettingsDivider()

                    SubtitleCustomizationContent(
                        subtitlePrefs = subtitlePrefs,
                        useExoPlayer = uiState.useExoPlayer,
                        onUpdate = { updatedPrefs ->
                            scope.launch(Dispatchers.IO) {
                                preferencesRepository.setSubtitlePreferences(updatedPrefs)
                            }
                        },
                    )
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.pref_group_chromecast)) {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_cast),
                        title = stringResource(R.string.pref_cast_hevc_title),
                        subtitle = stringResource(R.string.pref_cast_hevc_description),
                        checked = uiState.castHevcEnabled,
                        onCheckedChange = { viewModel.setCastHevcEnabled(it) },
                    )

                    SettingsDivider()

                    var showBitrateMenu by remember { mutableStateOf(false) }
                    val bitrateOptions = listOf(
                        16_000_000 to "16 Mbps",
                        8_000_000 to "8 Mbps",
                        4_000_000 to "4 Mbps",
                        2_000_000 to "2 Mbps",
                        1_000_000 to "1 Mbps",
                    )
                    val currentBitrateLabel = bitrateOptions.find { it.first == uiState.castMaxBitrate }?.second ?: "16 Mbps"

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBitrateMenu = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_speed),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.pref_cast_max_bitrate_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = currentBitrateLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showBitrateMenu,
                            onDismissRequest = { showBitrateMenu = false },
                        ) {
                            bitrateOptions.forEach { (bitrate, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setCastMaxBitrate(bitrate)
                                        showBitrateMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
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
    icon: Painter? = null,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
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
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
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
        } else if (onClick != null) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
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
private fun VideoZoomModeSelectorItem(
    selectedMode: VideoZoomMode,
    onModeSelected: (VideoZoomMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf(VideoZoomMode.FIT, VideoZoomMode.ZOOM, VideoZoomMode.STRETCH)

    Box {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_fullscreen),
            title = stringResource(R.string.pref_default_zoom_title),
            subtitle = getVideoZoomModeDisplayName(selectedMode),
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
            modes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(getVideoZoomModeDisplayName(mode)) },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    },
                    leadingIcon =
                        if (selectedMode == mode) {
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
private fun SubtitleCustomizationContent(
    subtitlePrefs: SubtitlePreferences,
    useExoPlayer: Boolean,
    onUpdate: (SubtitlePreferences) -> Unit,
) {
    ColorPickerItem(
        title = stringResource(R.string.pref_sub_text_color),
        color = subtitlePrefs.textColor,
        onColorChange = { onUpdate(subtitlePrefs.copy(textColor = it)) },
    )

    SettingsDivider()

    SubtitleSliderItem(
        title = stringResource(R.string.pref_sub_text_size),
        value = subtitlePrefs.textSize,
        valueRange = 0.5f..2.0f,
        onValueChange = { onUpdate(subtitlePrefs.copy(textSize = it)) },
    )

    SettingsDivider()

    SettingsSwitchItem(
        icon = painterResource(id = R.drawable.ic_bold),
        title = stringResource(R.string.pref_sub_bold),
        subtitle = stringResource(R.string.pref_sub_bold_summary),
        checked = subtitlePrefs.bold,
        onCheckedChange = { onUpdate(subtitlePrefs.copy(bold = it)) },
    )

    AnimatedVisibility(
        visible = !useExoPlayer,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            SettingsDivider()
            SettingsSwitchItem(
                icon = painterResource(id = R.drawable.ic_italic),
                title = stringResource(R.string.pref_sub_italic),
                subtitle = stringResource(R.string.pref_sub_italic_summary),
                checked = subtitlePrefs.italic,
                onCheckedChange = { onUpdate(subtitlePrefs.copy(italic = it)) },
            )
        }
    }

    SettingsDivider()

    SubtitleDropdownItem(
        title = stringResource(R.string.pref_sub_outline_style),
        selectedOption = subtitlePrefs.outlineStyle,
        options =
            SubtitleOutlineStyle.entries.filter { style ->
                when (style) {
                    SubtitleOutlineStyle.BACKGROUND_BOX -> !useExoPlayer
                    SubtitleOutlineStyle.RAISED,
                    SubtitleOutlineStyle.DEPRESSED -> useExoPlayer
                    else -> true
                }
            },
        onValueChange = { style -> onUpdate(subtitlePrefs.copy(outlineStyle = style)) },
        labelProvider = { getSubtitleOutlineStyleDisplayName(it) },
        icon = painterResource(id = R.drawable.ic_texture),
    )

    AnimatedVisibility(
        visible =
            subtitlePrefs.outlineStyle == SubtitleOutlineStyle.OUTLINE ||
                subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DROP_SHADOW,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            SettingsDivider()
            ColorPickerItem(
                title =
                    if (subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DROP_SHADOW)
                        stringResource(R.string.pref_sub_shadow_color)
                    else stringResource(R.string.pref_sub_outline_color),
                color = subtitlePrefs.outlineColor,
                onColorChange = { onUpdate(subtitlePrefs.copy(outlineColor = it)) },
            )

            if (!useExoPlayer) {
                SettingsDivider()
                SubtitleSliderItem(
                    title =
                        if (subtitlePrefs.outlineStyle == SubtitleOutlineStyle.DROP_SHADOW)
                            stringResource(R.string.pref_sub_shadow_size)
                        else stringResource(R.string.pref_sub_outline_size),
                    value = subtitlePrefs.outlineSize,
                    valueRange = 0f..10f,
                    onValueChange = { onUpdate(subtitlePrefs.copy(outlineSize = it)) },
                )
            }
        }
    }

    AnimatedVisibility(
        visible = subtitlePrefs.outlineStyle == SubtitleOutlineStyle.BACKGROUND_BOX,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            SettingsDivider()
            ColorPickerItem(
                title = stringResource(R.string.pref_sub_background_color),
                color = subtitlePrefs.backgroundColor,
                onColorChange = { onUpdate(subtitlePrefs.copy(backgroundColor = it)) },
            )
        }
    }

    AnimatedVisibility(
        visible = useExoPlayer,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            SettingsDivider()
            ColorPickerItem(
                title = stringResource(R.string.pref_sub_window_color),
                color = subtitlePrefs.windowColor,
                onColorChange = { onUpdate(subtitlePrefs.copy(windowColor = it)) },
            )
        }
    }

    AnimatedVisibility(
        visible = !useExoPlayer,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            SettingsDivider()
            SubtitleDropdownItem(
                title = stringResource(R.string.pref_sub_vertical_pos),
                selectedOption = subtitlePrefs.verticalPosition,
                options = SubtitleVerticalPosition.entries,
                onValueChange = { pos -> onUpdate(subtitlePrefs.copy(verticalPosition = pos)) },
                labelProvider = { getSubtitleVerticalPositionDisplayName(it) },
                icon = painterResource(id = R.drawable.ic_vertical),
            )

            SettingsDivider()

            SubtitleDropdownItem(
                title = stringResource(R.string.pref_sub_horizontal_align),
                selectedOption = subtitlePrefs.horizontalAlignment,
                options = SubtitleHorizontalAlignment.entries,
                onValueChange = { align ->
                    onUpdate(subtitlePrefs.copy(horizontalAlignment = align))
                },
                labelProvider = { getSubtitleHorizontalAlignmentDisplayName(it) },
                icon = painterResource(id = R.drawable.ic_horizontal),
            )
        }
    }

    Box(modifier = Modifier.padding(16.dp)) {
        OutlinedButton(
            onClick = { onUpdate(SubtitlePreferences.DEFAULT) },
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_reset_defaults))
        }
    }
}

@Composable
private fun ColorPickerItem(title: String, color: Int, onColorChange: (Int) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { showDialog = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    if (showDialog) {
        SimpleColorPickerDialog(
            initialColor = color,
            onColorSelected = {
                onColorChange(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun SubtitleSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = String.format(Locale.US, "%.1f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            modifier = Modifier.height(24.dp),
        )
    }
}

@Composable
private fun <T> SubtitleDropdownItem(
    title: String,
    selectedOption: T,
    options: List<T>,
    onValueChange: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    icon: Painter,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsItem(
            icon = icon,
            title = title,
            subtitle = labelProvider(selectedOption),
            onClick = { expanded = true },
            trailing = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelProvider(option)) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    leadingIcon =
                        if (selectedOption == option) {
                            {
                                Icon(
                                    painterResource(id = R.drawable.ic_check),
                                    null,
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
private fun LanguageSelectorItem(
    title: String,
    subtitle: String,
    selectedCode: String,
    onLanguageSelected: (String) -> Unit,
    icon: Painter,
) {
    val context = LocalContext.current
    val languages = remember { context.resources.getStringArray(R.array.languages) }
    val languageCodes = remember { context.resources.getStringArray(R.array.language_values) }

    val selectedIndex = languageCodes.indexOf(selectedCode).coerceAtLeast(0)
    val displayName = languages.getOrElse(selectedIndex) { languages[0] }

    var showDialog by remember { mutableStateOf(false) }

    SettingsItem(
        icon = icon,
        title = title,
        subtitle = displayName,
        onClick = { showDialog = true },
        trailing = {
            Icon(
                painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )

    if (showDialog) {
        LanguagePickerDialog(
            languages = languages,
            languageCodes = languageCodes,
            selectedCode = selectedCode,
            onSelect = { code ->
                onLanguageSelected(code)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    languages: Array<String>,
    languageCodes: Array<String>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredIndices =
        remember(searchQuery) {
            if (searchQuery.isBlank()) {
                languages.indices.toList()
            } else {
                languages.indices.filter { i ->
                    languages[i].contains(searchQuery, ignoreCase = true)
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pref_group_language)) },
        text = {
            Column {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle =
                                MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                            singleLine = true,
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.pref_language_search_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            },
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_clear),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    items(filteredIndices.size) { filterIdx ->
                        val idx = filteredIndices[filterIdx]
                        val isSelected = languageCodes[idx] == selectedCode

                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isSelected)
                                            Modifier.background(
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.3f
                                                )
                                            )
                                        else Modifier
                                    )
                                    .clickable { onSelect(languageCodes[idx]) }
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Spacer(modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = languages[idx],
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SimpleColorPickerDialog(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val presetColors =
        listOf(
            Color.WHITE,
            Color.BLACK,
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.YELLOW,
            Color.CYAN,
            Color.MAGENTA,
            Color.GRAY,
            Color.TRANSPARENT,
        )

    var hexInput by remember { mutableStateOf(String.format("#%08X", initialColor)) }
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
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(
                        stringResource(R.string.color_picker_presets),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presetColors.forEach { presetColor ->
                            Box(
                                modifier =
                                    Modifier.size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(presetColor))
                                        .border(
                                            2.dp,
                                            if (presetColor == initialColor) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            CircleShape,
                                        )
                                        .clickable { onColorSelected(presetColor) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.color_picker_custom),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { newValue ->
                                hexInput = newValue.uppercase()
                                val parsedColor = parseHexColor(newValue)
                                isValidHex = parsedColor != null
                            },
                            label = { Text(stringResource(R.string.color_picker_hex_label)) },
                            placeholder = { Text(stringResource(R.string.color_picker_hex_hint)) },
                            isError = !isValidHex,
                            supportingText =
                                if (!isValidHex) {
                                    {
                                        Text(
                                            stringResource(R.string.color_picker_invalid),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                } else null,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )

                        Box(
                            modifier =
                                Modifier.size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isValidHex) {
                                            Color(parseHexColor(hexInput) ?: initialColor)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }

                    Button(
                        onClick = {
                            parseHexColor(hexInput)?.let { color -> onColorSelected(color) }
                        },
                        enabled = isValidHex,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.color_picker_apply))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SubtitlePreview(
    subtitlePrefs: SubtitlePreferences,
    useExoPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(androidx.compose.ui.graphics.Color.DarkGray, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val textStyle =
            MaterialTheme.typography.headlineSmall.copy(
                fontWeight = if (subtitlePrefs.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (subtitlePrefs.italic) FontStyle.Italic else FontStyle.Normal,
                fontSize =
                    MaterialTheme.typography.headlineSmall.fontSize * 1.15f * subtitlePrefs.textSize,
            )

        Box(
            modifier =
                Modifier.background(
                        if (useExoPlayer) {
                            Color(subtitlePrefs.windowColor)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        RoundedCornerShape(6.dp),
                    )
                    .padding(
                        horizontal = if (useExoPlayer) 4.dp else 0.dp,
                        vertical = if (useExoPlayer) 2.dp else 0.dp,
                    )
        ) {
            Box(
                modifier =
                    Modifier.background(
                            when (subtitlePrefs.outlineStyle) {
                                SubtitleOutlineStyle.BACKGROUND_BOX ->
                                    Color(subtitlePrefs.backgroundColor)
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            },
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (
                    !useExoPlayer &&
                        subtitlePrefs.outlineStyle == SubtitleOutlineStyle.OUTLINE &&
                        subtitlePrefs.outlineSize > 0f
                ) {
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
                            Offset(offsetStep, offsetStep),
                        )
                        .forEach { offset ->
                            Text(
                                text = stringResource(R.string.subtitle_preview_text),
                                style = textStyle,
                                color = outlineColor,
                                modifier = Modifier.offset(offset.x.dp, offset.y.dp),
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
                            Offset(offsetStep, offsetStep),
                        )
                        .forEach { offset ->
                            Text(
                                text = stringResource(R.string.subtitle_preview_text),
                                style = textStyle,
                                color = outlineColor,
                                modifier = Modifier.offset(offset.x.dp, offset.y.dp),
                            )
                        }
                }

                Text(
                    text = stringResource(R.string.subtitle_preview_text),
                    style =
                        textStyle.copy(
                            shadow =
                                when {
                                    !useExoPlayer &&
                                        subtitlePrefs.outlineStyle ==
                                            SubtitleOutlineStyle.DROP_SHADOW &&
                                        subtitlePrefs.outlineSize > 0f -> {
                                        val shadowOffset = subtitlePrefs.outlineSize
                                        Shadow(
                                            color = Color(subtitlePrefs.outlineColor),
                                            offset = Offset(shadowOffset, shadowOffset),
                                            blurRadius = shadowOffset * 1.2f,
                                        )
                                    }

                                    useExoPlayer &&
                                        subtitlePrefs.outlineStyle ==
                                            SubtitleOutlineStyle.DROP_SHADOW -> {
                                        Shadow(
                                            color = Color(subtitlePrefs.outlineColor),
                                            offset = Offset(3f, 3f),
                                            blurRadius = 4f,
                                        )
                                    }

                                    useExoPlayer &&
                                        subtitlePrefs.outlineStyle ==
                                            SubtitleOutlineStyle.RAISED -> {
                                        Shadow(
                                            color =
                                                androidx.compose.ui.graphics.Color.White.copy(
                                                    alpha = 0.5f
                                                ),
                                            offset = Offset(-1f, -1f),
                                            blurRadius = 2f,
                                        )
                                    }

                                    useExoPlayer &&
                                        subtitlePrefs.outlineStyle ==
                                            SubtitleOutlineStyle.DEPRESSED -> {
                                        Shadow(
                                            color =
                                                androidx.compose.ui.graphics.Color.Black.copy(
                                                    alpha = 0.5f
                                                ),
                                            offset = Offset(-1f, -1f),
                                            blurRadius = 2f,
                                        )
                                    }

                                    else -> null
                                }
                        ),
                    color = Color(subtitlePrefs.textColor),
                )
            }
        }
    }
}

@Composable
private fun getVideoZoomModeDisplayName(mode: VideoZoomMode): String {
    return when (mode) {
        VideoZoomMode.FIT -> stringResource(R.string.zoom_fit)
        VideoZoomMode.ZOOM -> stringResource(R.string.zoom_zoom)
        VideoZoomMode.STRETCH -> stringResource(R.string.zoom_stretch)
    }
}

@Composable
private fun getSubtitleOutlineStyleDisplayName(style: SubtitleOutlineStyle): String {
    return when (style) {
        SubtitleOutlineStyle.NONE -> stringResource(R.string.outline_none)
        SubtitleOutlineStyle.OUTLINE -> stringResource(R.string.outline_outline)
        SubtitleOutlineStyle.DROP_SHADOW -> stringResource(R.string.outline_drop_shadow)
        SubtitleOutlineStyle.RAISED -> stringResource(R.string.outline_raised)
        SubtitleOutlineStyle.DEPRESSED -> stringResource(R.string.outline_depressed)
        SubtitleOutlineStyle.BACKGROUND_BOX -> stringResource(R.string.outline_background)
    }
}

@Composable
private fun getSubtitleVerticalPositionDisplayName(position: SubtitleVerticalPosition): String {
    return when (position) {
        SubtitleVerticalPosition.TOP -> stringResource(R.string.align_top)
        SubtitleVerticalPosition.BOTTOM -> stringResource(R.string.align_bottom)
        SubtitleVerticalPosition.CENTER -> stringResource(R.string.align_center)
    }
}

@Composable
private fun getSubtitleHorizontalAlignmentDisplayName(
    alignment: SubtitleHorizontalAlignment
): String {
    return when (alignment) {
        SubtitleHorizontalAlignment.LEFT -> stringResource(R.string.align_left)
        SubtitleHorizontalAlignment.CENTER -> stringResource(R.string.align_center)
        SubtitleHorizontalAlignment.RIGHT -> stringResource(R.string.align_right)
    }
}
