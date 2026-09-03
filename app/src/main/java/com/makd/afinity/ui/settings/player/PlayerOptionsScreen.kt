package com.makd.afinity.ui.settings.player

import android.graphics.Color
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.player.AssRenderMode
import com.makd.afinity.data.models.player.MpvAudioOutput
import com.makd.afinity.data.models.player.MpvHwDec
import com.makd.afinity.data.models.player.MpvVideoOutput
import com.makd.afinity.data.models.player.MusicQuality
import com.makd.afinity.data.models.player.SkipMode
import com.makd.afinity.data.models.player.SubtitleHorizontalAlignment
import com.makd.afinity.data.models.player.SubtitleOutlineStyle
import com.makd.afinity.data.models.player.SubtitlePreferences
import com.makd.afinity.data.models.player.SubtitleVerticalPosition
import com.makd.afinity.data.models.player.VideoQuality
import com.makd.afinity.data.models.player.VideoZoomMode
import com.makd.afinity.di.PreferencesEntryPoint
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinitySlider
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.ui.player.components.musicQualityLabel
import com.makd.afinity.ui.player.components.settingsQualityLabel
import com.makd.afinity.ui.settings.SettingsViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import java.io.File
import java.util.Locale

private enum class PlaybackSection {
    VideoQuality,
    MusicQuality,
    Tracks,
    SubtitleAppearance,
    Controls,
    Chromecast,
    Advanced,
}

@Composable
private fun playbackSectionTitle(section: PlaybackSection?): String =
    when (section) {
        null -> stringResource(R.string.player_options_title)
        PlaybackSection.VideoQuality ->
            stringResource(R.string.playback_section_video_quality)
        PlaybackSection.MusicQuality ->
            stringResource(R.string.playback_section_music_quality)
        PlaybackSection.Tracks -> stringResource(R.string.playback_section_tracks)
        PlaybackSection.SubtitleAppearance ->
            stringResource(R.string.playback_section_subtitle_appearance)
        PlaybackSection.Controls -> stringResource(R.string.playback_section_controls)
        PlaybackSection.Chromecast -> stringResource(R.string.pref_group_chromecast)
        PlaybackSection.Advanced -> stringResource(R.string.playback_section_advanced)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf<PlaybackSection?>(null) }

    BackHandler(enabled = section != null) { section = null }

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
    val playerOffset = LocalPlayerOffset.current

    var editingConfigFile by remember { mutableStateOf<String?>(null) }
    editingConfigFile?.let { fileName ->
        MpvConfigEditorDialog(fileName = fileName, onDismiss = { editingConfigFile = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playbackSectionTitle(section),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (section != null) section = null else onBackClick() }
                    ) {
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
            if (section == null) item {
                SettingsGroup {
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_video_settings),
                        title = stringResource(R.string.playback_section_video_quality),
                        subtitle =
                            stringResource(R.string.playback_section_video_quality_summary),
                        onClick = { section = PlaybackSection.VideoQuality },
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_audio),
                        title = stringResource(R.string.playback_section_music_quality),
                        subtitle =
                            stringResource(R.string.playback_section_music_quality_summary),
                        onClick = { section = PlaybackSection.MusicQuality },
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_subtitles),
                        title = stringResource(R.string.playback_section_tracks),
                        subtitle = stringResource(R.string.playback_section_tracks_summary),
                        onClick = { section = PlaybackSection.Tracks },
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_subtitles_settings),
                        title = stringResource(R.string.playback_section_subtitle_appearance),
                        subtitle =
                            stringResource(
                                R.string.playback_section_subtitle_appearance_summary
                            ),
                        onClick = { section = PlaybackSection.SubtitleAppearance },
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_player_play_filled),
                        title = stringResource(R.string.playback_section_controls),
                        subtitle = stringResource(R.string.playback_section_controls_summary),
                        onClick = { section = PlaybackSection.Controls },
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_cast),
                        title = stringResource(R.string.pref_group_chromecast),
                        subtitle = stringResource(R.string.playback_section_cast_summary),
                        onClick = { section = PlaybackSection.Chromecast },
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_cpu),
                        title = stringResource(R.string.playback_section_advanced),
                        subtitle = stringResource(R.string.playback_section_advanced_summary),
                        onClick = { section = PlaybackSection.Advanced },
                    )
                }
            }

            if (section == PlaybackSection.Advanced) item {
                Column {
                    SettingsGroup(title = stringResource(R.string.pref_group_engine)) {
                        SettingsSwitchItem(
                            icon = painterResource(id = R.drawable.ic_video_settings),
                            title = stringResource(R.string.pref_use_exoplayer_title),
                            subtitle = stringResource(R.string.pref_use_exoplayer_summary),
                            checked = uiState.useExoPlayer,
                            onCheckedChange = viewModel::toggleUseExoPlayer,
                        )
                        SettingsDivider()
                        BufferSizeSelectorItem(
                            selectedSizeMb = uiState.bufferSizeMb,
                            onSizeSelected = viewModel::setBufferSizeMb,
                        )
                    }

                    AnimatedVisibility(
                        visible = !uiState.useExoPlayer,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(24.dp))
                            SettingsGroup(title = stringResource(R.string.pref_group_mpv)) {
                                SubtitleDropdownItem(
                                    title = stringResource(R.string.pref_mpv_hwdec_title),
                                    selectedOption = uiState.mpvHwDec,
                                    options = MpvHwDec.entries.toList(),
                                    onValueChange = viewModel::setMpvHwDec,
                                    labelProvider = { stringResource(it.labelRes) },
                                    icon = painterResource(id = R.drawable.ic_cpu),
                                )
                                SettingsDivider()
                                SubtitleDropdownItem(
                                    title = stringResource(R.string.pref_mpv_video_output_title),
                                    selectedOption = uiState.mpvVideoOutput,
                                    options = MpvVideoOutput.entries.toList(),
                                    onValueChange = viewModel::setMpvVideoOutput,
                                    labelProvider = { stringResource(it.labelRes) },
                                    icon = painterResource(id = R.drawable.ic_video_settings),
                                )
                                SettingsDivider()
                                SubtitleDropdownItem(
                                    title = stringResource(R.string.pref_mpv_audio_output_title),
                                    selectedOption = uiState.mpvAudioOutput,
                                    options = MpvAudioOutput.entries.toList(),
                                    onValueChange = viewModel::setMpvAudioOutput,
                                    labelProvider = { stringResource(it.labelRes) },
                                    icon = painterResource(id = R.drawable.ic_audio),
                                )
                                SettingsDivider()
                                SettingsItem(
                                    icon = painterResource(id = R.drawable.ic_edit),
                                    title = stringResource(R.string.pref_edit_mpv_conf_title),
                                    subtitle = stringResource(R.string.pref_edit_config_summary),
                                    onClick = { editingConfigFile = "mpv.conf" },
                                )
                                SettingsDivider()
                                SettingsItem(
                                    icon = painterResource(id = R.drawable.ic_edit),
                                    title = stringResource(R.string.pref_edit_input_conf_title),
                                    subtitle =
                                        stringResource(R.string.pref_edit_input_conf_summary),
                                    onClick = { editingConfigFile = "input.conf" },
                                )
                                // Temporarily hidden; backend prefs retained.
                                /*
                                SettingsDivider()
                                SubtitleDropdownItem(
                                    title = stringResource(R.string.pref_mpv_gpu_api_title),
                                    selectedOption = uiState.mpvGpuApi,
                                    options = MpvGpuApi.entries.toList(),
                                    onValueChange = viewModel::setMpvGpuApi,
                                    labelProvider = { stringResource(it.labelRes) },
                                    icon = painterResource(id = R.drawable.ic_cpu),
                                )
                                SettingsDivider()
                                SubtitleDropdownItem(
                                    title = stringResource(R.string.pref_mpv_hdr_output_title),
                                    selectedOption = uiState.mpvHdrOutput,
                                    options = MpvHdrOutput.entries.toList(),
                                    onValueChange = viewModel::setMpvHdrOutput,
                                    labelProvider = { stringResource(it.labelRes) },
                                    icon = painterResource(id = R.drawable.ic_colorize),
                                    hint = stringResource(R.string.pref_mpv_hdr_output_hint),
                                )
                                SettingsDivider()
                                SubtitleDropdownItem(
                                    title = stringResource(R.string.pref_mpv_tone_mapping_title),
                                    selectedOption = uiState.mpvToneMapping,
                                    options = MpvToneMapping.entries.toList(),
                                    onValueChange = viewModel::setMpvToneMapping,
                                    labelProvider = { stringResource(it.labelRes) },
                                    icon = painterResource(id = R.drawable.ic_texture),
                                    hint = stringResource(R.string.pref_mpv_tone_mapping_hint),
                                )
                                SettingsDivider()
                                SettingsSwitchItem(
                                    icon = painterResource(id = R.drawable.ic_visibility),
                                    title = stringResource(R.string.pref_mpv_hdr_peak_title),
                                    subtitle =
                                        stringResource(R.string.pref_mpv_hdr_peak_summary),
                                    checked = uiState.mpvHdrPeakDetection,
                                    onCheckedChange = viewModel::setMpvHdrPeakDetection,
                                )
                                */
                            }
                        }
                    }
                }
            }

            if (section == PlaybackSection.Tracks) item {
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

                    SettingsDivider()

                    SubtitleModeSelectorItem(
                        icon = painterResource(id = R.drawable.ic_subtitles_settings),
                        title = stringResource(R.string.pref_subtitle_mode_title),
                        selectedMode = uiState.subtitleModeOverride,
                        onModeSelected = viewModel::setSubtitleModeOverride,
                    )

                    SettingsDivider()

                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_subtitles),
                        title = stringResource(R.string.pref_prefer_sdh_title),
                        subtitle =
                            if (uiState.sdhPreferenceApplies) {
                                stringResource(R.string.pref_prefer_sdh_summary)
                            } else {
                                stringResource(R.string.pref_prefer_sdh_unavailable)
                            },
                        checked = uiState.preferSdhSubtitles,
                        onCheckedChange = viewModel::togglePreferSdhSubtitles,
                        enabled = uiState.sdhPreferenceApplies,
                    )
                }
            }

            if (section == PlaybackSection.Controls) item {
                SettingsGroup(title = stringResource(R.string.pref_group_interface)) {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_player_play_filled),
                        title = stringResource(R.string.pref_autoplay_title),
                        subtitle = stringResource(R.string.pref_autoplay_summary),
                        checked = uiState.autoPlay,
                        onCheckedChange = viewModel::toggleAutoPlay,
                    )
                    SettingsDivider()
                    SkipModeSelectorItem(
                        icon = painterResource(id = R.drawable.ic_skip_next),
                        title = stringResource(R.string.pref_skip_intro_title),
                        selectedMode = uiState.skipIntroMode,
                        onModeSelected = viewModel::setSkipIntroMode,
                    )
                    SettingsDivider()
                    SkipModeSelectorItem(
                        icon = painterResource(id = R.drawable.ic_fast_forward),
                        title = stringResource(R.string.pref_skip_outro_title),
                        selectedMode = uiState.skipOutroMode,
                        onModeSelected = viewModel::setSkipOutroMode,
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
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_player_pause_filled),
                        title = stringResource(R.string.pref_pause_screen_title),
                        subtitle = stringResource(R.string.pref_pause_screen_summary),
                        checked = uiState.pauseScreenEnabled,
                        onCheckedChange = viewModel::togglePauseScreen,
                    )
                    AnimatedVisibility(
                        visible = uiState.pauseScreenEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            SettingsDivider()
                            PauseScreenDelaySelectorItem(
                                seconds = uiState.pauseScreenDelaySeconds,
                                onSecondsChange = viewModel::setPauseScreenDelaySeconds,
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_fast_forward),
                        title = stringResource(R.string.pref_chapter_skip_gesture_title),
                        subtitle = stringResource(R.string.pref_chapter_skip_gesture_summary),
                        checked = uiState.chapterSkipGesture,
                        onCheckedChange = viewModel::toggleChapterSkipGesture,
                    )
                    SettingsDivider()
                    VideoZoomModeSelectorItem(
                        selectedMode = uiState.defaultVideoZoomMode,
                        onModeSelected = viewModel::setDefaultVideoZoomMode,
                    )
                }
            }

            if (section == PlaybackSection.Controls) item {
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

            if (section == PlaybackSection.SubtitleAppearance) item {
                SettingsGroup(title = stringResource(R.string.pref_group_subtitles)) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        SubtitlePreview(
                            subtitlePrefs = subtitlePrefs,
                            useExoPlayer = uiState.useExoPlayer,
                        )
                    }

                    SettingsDivider()

                    AnimatedVisibility(
                        visible = uiState.useExoPlayer,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            SubtitleDropdownItem(
                                title = stringResource(R.string.pref_ass_render_mode_title),
                                selectedOption = uiState.assRenderMode,
                                options = AssRenderMode.entries.toList(),
                                onValueChange = viewModel::setAssRenderMode,
                                labelProvider = { stringResource(it.labelRes) },
                                icon = painterResource(id = R.drawable.ic_subtitles),
                                hint = stringResource(R.string.pref_ass_render_mode_hint),
                            )
                            SettingsDivider()
                        }
                    }

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

            if (section == PlaybackSection.VideoQuality) item {
                SettingsGroup(title = stringResource(R.string.pref_group_streaming_quality)) {
                    val qualityOptions = VideoQuality.settingsLadder()
                    val transcodingAllowed = !uiState.neverTranscode

                    VideoQualitySelectorItem(
                        icon = painterResource(id = R.drawable.ic_wifi),
                        title = stringResource(R.string.pref_quality_wifi_title),
                        selectedBitrate = uiState.videoQualityWifi,
                        options = qualityOptions,
                        enabled = transcodingAllowed,
                        onQualitySelected = viewModel::setVideoQualityWifi,
                        hint =
                            if (transcodingAllowed) null
                            else stringResource(R.string.pref_requires_transcoding),
                    )

                    SettingsDivider()

                    VideoQualitySelectorItem(
                        icon = painterResource(id = R.drawable.ic_cellular_data),
                        title = stringResource(R.string.pref_quality_cellular_title),
                        selectedBitrate = uiState.videoQualityCellular,
                        options = qualityOptions,
                        enabled = transcodingAllowed,
                        onQualitySelected = viewModel::setVideoQualityCellular,
                        hint =
                            if (transcodingAllowed) null
                            else stringResource(R.string.pref_requires_transcoding),
                    )

                    SettingsDivider()

                    val channelOptions =
                        listOf(
                            8 to stringResource(R.string.pref_transcode_channels_8),
                            6 to stringResource(R.string.pref_transcode_channels_6),
                            2 to stringResource(R.string.pref_transcode_channels_2),
                        )

                    SubtitleDropdownItem(
                        title = stringResource(R.string.pref_transcode_channels_title),
                        selectedOption = uiState.transcodeMaxAudioChannels,
                        options = channelOptions.map { it.first },
                        onValueChange = viewModel::setTranscodeMaxAudioChannels,
                        labelProvider = { channels ->
                            channelOptions.find { it.first == channels }?.second.orEmpty()
                        },
                        icon = painterResource(id = R.drawable.ic_speaker),
                        enabled = transcodingAllowed,
                        hint =
                            if (transcodingAllowed) null
                            else stringResource(R.string.pref_requires_transcoding),
                    )

                    SettingsDivider()

                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_hdr),
                        title = stringResource(R.string.pref_hdr_passthrough_title),
                        subtitle = stringResource(R.string.pref_hdr_passthrough_description),
                        checked = uiState.allowHdrPassthrough,
                        onCheckedChange = { viewModel.setAllowHdrPassthrough(it) },
                    )

                    SettingsDivider()

                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_video_off),
                        title = stringResource(R.string.pref_never_transcode_title),
                        subtitle = stringResource(R.string.pref_never_transcode_description),
                        checked = uiState.neverTranscode,
                        onCheckedChange = { viewModel.setNeverTranscode(it) },
                    )
                }
            }

            if (section == PlaybackSection.MusicQuality) item {
                SettingsGroup(title = stringResource(R.string.pref_group_streaming_quality)) {
                    val musicOptions = MusicQuality.options()
                    val musicLabels = musicOptions.associate {
                        it.maxBitrate to musicQualityLabel(it)
                    }

                    SubtitleDropdownItem(
                        title = stringResource(R.string.pref_music_quality_wifi_title),
                        selectedOption = uiState.musicQualityWifi,
                        options = musicOptions.map { it.maxBitrate },
                        onValueChange = viewModel::setMusicQualityWifi,
                        labelProvider = { bitrate -> musicLabels[bitrate].orEmpty() },
                        icon = painterResource(id = R.drawable.ic_wifi),
                        enabled = !uiState.neverTranscode,
                        hint =
                            if (uiState.neverTranscode)
                                stringResource(R.string.pref_requires_transcoding)
                            else null,
                    )

                    SettingsDivider()

                    SubtitleDropdownItem(
                        title = stringResource(R.string.pref_music_quality_cellular_title),
                        selectedOption = uiState.musicQualityCellular,
                        options = musicOptions.map { it.maxBitrate },
                        onValueChange = viewModel::setMusicQualityCellular,
                        labelProvider = { bitrate -> musicLabels[bitrate].orEmpty() },
                        icon = painterResource(id = R.drawable.ic_cellular_data),
                        enabled = !uiState.neverTranscode,
                        hint =
                            if (uiState.neverTranscode)
                                stringResource(R.string.pref_requires_transcoding)
                            else null,
                    )
                }
            }

            if (section == PlaybackSection.Chromecast) item {
                SettingsGroup(title = stringResource(R.string.pref_group_chromecast)) {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_cast),
                        title = stringResource(R.string.pref_cast_hevc_title),
                        subtitle = stringResource(R.string.pref_cast_hevc_description),
                        checked = uiState.castHevcEnabled,
                        onCheckedChange = { viewModel.setCastHevcEnabled(it) },
                    )

                    SettingsDivider()

                    val bitrateOptions =
                        listOf(
                            0 to stringResource(R.string.pref_cast_max_bitrate_auto),
                            16_000_000 to stringResource(R.string.unit_mbps_fmt, 16),
                            8_000_000 to stringResource(R.string.unit_mbps_fmt, 8),
                            4_000_000 to stringResource(R.string.unit_mbps_fmt, 4),
                            2_000_000 to stringResource(R.string.unit_mbps_fmt, 2),
                            1_000_000 to stringResource(R.string.unit_mbps_fmt, 1),
                        )

                    SubtitleDropdownItem(
                        title = stringResource(R.string.pref_cast_max_bitrate_title),
                        selectedOption = uiState.castMaxBitrate,
                        options = bitrateOptions.map { it.first },
                        onValueChange = viewModel::setCastMaxBitrate,
                        labelProvider = { bitrate ->
                            bitrateOptions.find { it.first == bitrate }?.second
                                ?: stringResource(R.string.unit_mbps_fmt, 16)
                        },
                        icon = painterResource(id = R.drawable.ic_broadcast),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipModeSelectorItem(
    icon: Painter,
    title: String,
    selectedMode: SkipMode,
    onModeSelected: (SkipMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsItem(
            icon = icon,
            title = title,
            subtitle = getSkipModeDisplayName(selectedMode),
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
            SkipMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(getSkipModeDisplayName(mode)) },
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
private fun SubtitleModeSelectorItem(
    icon: Painter,
    title: String,
    selectedMode: String,
    onModeSelected: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsItem(
        icon = icon,
        title = title,
        subtitle = getSubtitleModeDisplayName(selectedMode),
        onClick = { showDialog = true },
        trailing = {
            Icon(
                painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
    )

    if (showDialog) {
        SubtitleModePickerDialog(
            selectedMode = selectedMode,
            onSelect = { mode ->
                onModeSelected(mode)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun SubtitleModePickerDialog(
    selectedMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf("") + SubtitlePlaybackMode.entries.map { it.serialName }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pref_subtitle_mode_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { mode ->
                    val isSelected = mode == selectedMode

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
                                .clickable { onSelect(mode) }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Top,
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
                        Column {
                            Text(
                                text = getSubtitleModeDisplayName(mode),
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = getSubtitleModeDescription(mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun getSubtitleModeDisplayName(mode: String): String =
    when (SubtitlePlaybackMode.fromNameOrNull(mode)) {
        SubtitlePlaybackMode.DEFAULT -> stringResource(R.string.subtitle_mode_default)
        SubtitlePlaybackMode.ALWAYS -> stringResource(R.string.subtitle_mode_always)
        SubtitlePlaybackMode.SMART -> stringResource(R.string.subtitle_mode_smart)
        SubtitlePlaybackMode.ONLY_FORCED -> stringResource(R.string.subtitle_mode_only_forced)
        SubtitlePlaybackMode.NONE -> stringResource(R.string.subtitle_mode_none)
        null -> stringResource(R.string.subtitle_mode_follow_server)
    }

@Composable
private fun getSubtitleModeDescription(mode: String): String =
    when (SubtitlePlaybackMode.fromNameOrNull(mode)) {
        SubtitlePlaybackMode.DEFAULT -> stringResource(R.string.subtitle_mode_default_hint)
        SubtitlePlaybackMode.ALWAYS -> stringResource(R.string.subtitle_mode_always_hint)
        SubtitlePlaybackMode.SMART -> stringResource(R.string.subtitle_mode_smart_hint)
        SubtitlePlaybackMode.ONLY_FORCED -> stringResource(R.string.subtitle_mode_only_forced_hint)
        SubtitlePlaybackMode.NONE -> stringResource(R.string.subtitle_mode_none_hint)
        null -> stringResource(R.string.subtitle_mode_follow_server_hint)
    }

@Composable
private fun getSkipModeDisplayName(mode: SkipMode): String =
    when (mode) {
        SkipMode.BUTTON -> stringResource(R.string.skip_mode_button)
        SkipMode.AUTO_SKIP -> stringResource(R.string.skip_mode_auto_skip)
        SkipMode.DISABLED -> stringResource(R.string.skip_mode_disabled)
    }

@Composable
private fun BufferSizeSelectorItem(selectedSizeMb: Int, onSizeSelected: (Int) -> Unit) {
    val options =
        listOf(
            Triple(
                32,
                stringResource(R.string.unit_megabytes_fmt, 32),
                stringResource(R.string.buffer_desc_minimal),
            ),
            Triple(
                64,
                stringResource(R.string.unit_megabytes_fmt, 64),
                stringResource(R.string.buffer_desc_default),
            ),
            Triple(
                128,
                stringResource(R.string.unit_megabytes_fmt, 128),
                stringResource(R.string.buffer_desc_slow_networks),
            ),
            Triple(
                256,
                stringResource(R.string.unit_megabytes_fmt, 256),
                stringResource(R.string.buffer_desc_high),
            ),
            Triple(
                512,
                stringResource(R.string.unit_megabytes_fmt, 512),
                stringResource(R.string.buffer_desc_extreme),
            ),
        )

    val selectedIndex = options.indexOfFirst { it.first == selectedSizeMb }.coerceAtLeast(0)
    val selectedOption = options[selectedIndex]
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_speed),
            title = stringResource(R.string.pref_buffer_size_title),
            subtitle =
                stringResource(
                    R.string.pref_buffer_size_value_fmt,
                    selectedOption.second,
                    selectedOption.third,
                ),
            subtitleColor =
                if (selectedIndex >= 3) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = option.second,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (index == selectedIndex)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = option.third,
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (index >= 3) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSizeSelected(option.first)
                        expanded = false
                    },
                    leadingIcon =
                        if (index == selectedIndex) {
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
private fun PauseScreenDelaySelectorItem(seconds: Int, onSecondsChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_timer),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.pref_pause_screen_delay_title),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp),
            ) {
                IconButton(
                    onClick = { if (seconds > 0) onSecondsChange(seconds - 1) },
                    enabled = seconds > 0,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_remove),
                        contentDescription = stringResource(R.string.cd_decrease_limit),
                        tint =
                            if (seconds > 0) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text =
                        if (seconds == 0) stringResource(R.string.pause_screen_delay_instant)
                        else stringResource(R.string.pause_screen_delay_seconds_fmt, seconds),
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(72.dp).padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(36.dp),
            ) {
                IconButton(
                    onClick = { if (seconds < 5) onSecondsChange(seconds + 1) },
                    enabled = seconds < 5,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = stringResource(R.string.cd_increase_limit),
                        tint =
                            if (seconds < 5) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MpvConfigEditorDialog(fileName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(fileName) {
        text = withContext(Dispatchers.IO) { readMpvConfig(context, fileName) }
        loaded = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fileName) },
        text = {
            Column {
                if (!loaded) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        textStyle =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.mpv_conf_editor_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = loaded,
                onClick = {
                    val toSave = text
                    scope.launch {
                        withContext(Dispatchers.IO) { writeMpvConfig(context, fileName, toSave) }
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun mpvConfigDir(context: android.content.Context) = File(context.filesDir, "mpv")

private fun readMpvConfig(context: android.content.Context, fileName: String): String {
    return try {
        val file = File(mpvConfigDir(context), fileName)
        when {
            file.exists() -> file.readText()
            fileName == "mpv.conf" ->
                context.assets.open("mpv.conf").bufferedReader().use { it.readText() }
            else -> ""
        }
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Failed to read $fileName")
        ""
    }
}

private fun writeMpvConfig(context: android.content.Context, fileName: String, text: String) {
    try {
        val dir = mpvConfigDir(context)
        if (!dir.exists()) dir.mkdirs()
        File(dir, fileName).writeText(text)
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Failed to write $fileName")
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
        steps = 14,
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
                    steps = 19,
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
    steps: Int = 0,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", value),
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AfinitySlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.height(24.dp),
        )
    }
}

@Composable
private fun VideoQualitySelectorItem(
    icon: Painter,
    title: String,
    selectedBitrate: Int,
    options: List<VideoQuality>,
    enabled: Boolean,
    onQualitySelected: (Int) -> Unit,
    hint: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.maxBitrate == selectedBitrate } ?: options.first()

    Column {
        SettingsItem(
            icon = icon,
            title = title,
            subtitle = settingsQualityLabel(selected),
            onClick = { showDialog = true },
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
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }

    if (showDialog) {
        VideoQualityPickerDialog(
            title = title,
            selectedBitrate = selectedBitrate,
            options = options,
            onSelect = { bitrate ->
                onQualitySelected(bitrate)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun VideoQualityPickerDialog(
    title: String,
    selectedBitrate: Int,
    options: List<VideoQuality>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { quality ->
                    val isSelected = quality.maxBitrate == selectedBitrate

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
                                .clickable { onSelect(quality.maxBitrate) }
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
                            text = settingsQualityLabel(quality),
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun <T> SubtitleDropdownItem(
    title: String,
    selectedOption: T,
    options: List<T>,
    onValueChange: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    icon: Painter,
    hint: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Column {
            SettingsItem(
                icon = icon,
                title = title,
                subtitle = labelProvider(selectedOption),
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
                    )
                },
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (enabled) 0.7f else 0.38f
                        ),
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }

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
    val languages = stringArrayResource(id = R.array.languages)
    val languageCodes = stringArrayResource(id = R.array.language_values)

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
                        AfinityTextField(
                            value = hexInput,
                            onValueChange = { newValue ->
                                hexInput = newValue.uppercase()
                                val parsedColor = parseHexColor(newValue)
                                isValidHex = parsedColor != null
                            },
                            label = stringResource(R.string.color_picker_hex_label),
                            placeholder = stringResource(R.string.color_picker_hex_hint),
                            isError = !isValidHex,
                            supportingText =
                                if (!isValidHex) stringResource(R.string.color_picker_invalid)
                                else null,
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
                    MaterialTheme.typography.headlineSmall.fontSize *
                        1.15f *
                        subtitlePrefs.textSize,
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
