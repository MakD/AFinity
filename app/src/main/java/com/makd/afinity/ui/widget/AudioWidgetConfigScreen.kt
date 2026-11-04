package com.makd.afinity.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinitySegmentedTabs
import com.makd.afinity.ui.components.AfinitySlider
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.SegmentedTab
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.widget.AudioWidgetEngine
import com.makd.afinity.widget.AudioWidgetLook
import com.makd.afinity.widget.AudioWidgetSettings
import com.makd.afinity.widget.AudioWidgetState
import com.makd.afinity.widget.AudioWidgetStatus
import com.makd.afinity.widget.WidgetAlignment
import com.makd.afinity.widget.WidgetBadgeColours
import com.makd.afinity.widget.WidgetTextColour
import kotlin.math.roundToInt

private val WidgetFont =
    FontFamily(
        Font(R.font.google_sans_flex_regular, FontWeight.Normal),
        Font(R.font.google_sans_flex_medium, FontWeight.Medium),
        Font(R.font.google_sans_flex_bold, FontWeight.Bold),
    )

@Composable
fun AudioWidgetConfigScreen(
    viewModel: AudioWidgetConfigViewModel,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopBar(
                title = { AfinityTopAppBarTitle(stringResource(R.string.widget_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cd_close),
                        )
                    }
                },
                backgroundOpacity = backgroundOpacity,
            )
        }
    ) { innerPadding ->
        val current = settings ?: return@AfinityScreen
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    )
        ) {
            PreviewSection(current, viewModel.previewState)

            Spacer(modifier = Modifier.height(24.dp))
            SettingsGroup(title = stringResource(R.string.widget_settings_layout)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.widget_setting_cover),
                    subtitle = stringResource(R.string.widget_setting_cover_desc),
                    checked = current.showCover,
                    onCheckedChange = { checked ->
                        viewModel.update { it.copy(showCover = checked) }
                    },
                )
                SettingsDivider(startPadding = 16.dp)
                SettingsSwitchItem(
                    title = stringResource(R.string.widget_setting_status),
                    subtitle = stringResource(R.string.widget_setting_status_desc),
                    checked = current.showStatus,
                    onCheckedChange = { checked ->
                        viewModel.update { it.copy(showStatus = checked) }
                    },
                )
                SettingsDivider(startPadding = 16.dp)
                SegmentedSetting(
                    title = stringResource(R.string.widget_setting_alignment),
                    subtitle = null,
                    options =
                        listOf(
                            stringResource(R.string.widget_align_left),
                            stringResource(R.string.widget_align_right),
                        ),
                    selectedIndex = current.alignment.ordinal,
                    onSelect = { index ->
                        viewModel.update { it.copy(alignment = WidgetAlignment.entries[index]) }
                    },
                )
                SettingsDivider(startPadding = 16.dp)
                SettingsSwitchItem(
                    title = stringResource(R.string.widget_setting_continue),
                    subtitle = stringResource(R.string.widget_setting_continue_desc),
                    checked = current.showContinue,
                    onCheckedChange = { checked ->
                        viewModel.update { it.copy(showContinue = checked) }
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SettingsGroup(title = stringResource(R.string.widget_settings_appearance)) {
                OpacitySetting(
                    value = current.backgroundOpacity,
                    onValueChange = { value ->
                        viewModel.update { it.copy(backgroundOpacity = value) }
                    },
                )
                SettingsDivider(startPadding = 16.dp)
                SettingsSwitchItem(
                    title = stringResource(R.string.widget_setting_shadow),
                    subtitle = stringResource(R.string.widget_setting_shadow_desc),
                    checked = current.textShadow,
                    onCheckedChange = { checked ->
                        viewModel.update { it.copy(textShadow = checked) }
                    },
                )
                SettingsDivider(startPadding = 16.dp)
                SegmentedSetting(
                    title = stringResource(R.string.widget_setting_text_colour),
                    subtitle = stringResource(R.string.widget_setting_text_colour_desc),
                    options =
                        listOf(
                            stringResource(R.string.widget_text_auto),
                            stringResource(R.string.widget_text_light),
                            stringResource(R.string.widget_text_dark),
                        ),
                    selectedIndex = current.textColour.ordinal,
                    onSelect = { index ->
                        viewModel.update { it.copy(textColour = WidgetTextColour.entries[index]) }
                    },
                )
                SettingsDivider(startPadding = 16.dp)
                SegmentedSetting(
                    title = stringResource(R.string.widget_setting_badge),
                    subtitle = null,
                    options =
                        listOf(
                            stringResource(R.string.widget_badge_status),
                            stringResource(R.string.widget_badge_you),
                        ),
                    selectedIndex = current.badgeColours.ordinal,
                    onSelect = { index ->
                        viewModel.update {
                            it.copy(badgeColours = WidgetBadgeColours.entries[index])
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { viewModel.save(onSaved) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
            ) {
                Text(stringResource(R.string.btn_save_changes))
            }
        }
    }
}

@Composable
private fun PreviewSection(settings: AudioWidgetSettings, state: AudioWidgetState?) {
    var darkWallpaper by rememberSaveable { mutableStateOf(true) }
    val systemDark = isSystemInDarkTheme()
    val lightText =
        when (settings.textColour) {
            WidgetTextColour.LIGHT -> true
            WidgetTextColour.DARK -> false
            WidgetTextColour.AUTO ->
                if (settings.backgroundOpacity >= 50) systemDark else darkWallpaper
        }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(232.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (darkWallpaper) Color(0xFF1B2029) else Color(0xFFDCD6CC))
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(0.4f)
                        .align(Alignment.BottomCenter)
                        .background(if (darkWallpaper) Color(0xFF0E1014) else Color(0xFFEDE8DF))
            )
            WidgetPreview(
                settings = settings,
                state = state,
                lightText = lightText,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        AfinitySegmentedTabs(
            tabs =
                listOf(
                    SegmentedTab(stringResource(R.string.widget_preview_dark)),
                    SegmentedTab(stringResource(R.string.widget_preview_light)),
                ),
            selectedIndex = if (darkWallpaper) 0 else 1,
            onSelect = { darkWallpaper = it == 0 },
            horizontalPadding = 0.dp,
        )
    }
}

@Composable
private fun WidgetPreview(
    settings: AudioWidgetSettings,
    state: AudioWidgetState?,
    lightText: Boolean,
    modifier: Modifier = Modifier,
) {
    val textColor = Color(AudioWidgetLook.textColor(lightText))
    val shadow = if (settings.textShadow) Shadow(Color(0xB3000000), Offset(0f, 2f), 8f) else null
    val status = state?.status ?: AudioWidgetStatus.STOPPED
    val isAbs = state?.engine == AudioWidgetEngine.ABS
    val materialYou = settings.badgeColours == WidgetBadgeColours.MATERIAL_YOU
    val direction =
        if (settings.alignment == WidgetAlignment.RIGHT) LayoutDirection.Ltr
        else LayoutDirection.Rtl

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
            WidgetPreviewCard(
                settings = settings,
                state = state,
                lightText = lightText,
                textColor = textColor,
                shadow = shadow,
                status = status,
                isAbs = isAbs,
                materialYou = materialYou,
            )
        }
    }
}

@Composable
private fun WidgetPreviewCard(
    settings: AudioWidgetSettings,
    state: AudioWidgetState?,
    lightText: Boolean,
    textColor: Color,
    shadow: Shadow?,
    status: AudioWidgetStatus,
    isAbs: Boolean,
    materialYou: Boolean,
) {
    Row(
        modifier =
            Modifier.clip(RoundedCornerShape(20.dp))
                .background(
                    Color(AudioWidgetLook.backgroundColor(lightText, settings.backgroundOpacity))
                )
                .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (settings.showCover) {
            AsyncImage(
                imageUrl = state?.coverUrl,
                contentDescription = null,
                modifier =
                    Modifier.padding(bottom = 8.dp)
                        .size(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = Alignment.End,
        ) {
            Column(
                modifier = Modifier.padding(end = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (settings.showStatus) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(statusText(status)),
                            style =
                                TextStyle(
                                    fontFamily = WidgetFont,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.3.em,
                                    color = textColor,
                                    shadow = shadow,
                                ),
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Image(
                            painter = painterResource(badgeIcon(status, materialYou)),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                state?.title?.let { title ->
                    Text(
                        text = title,
                        style =
                            TextStyle(
                                fontFamily = WidgetFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = textColor,
                                shadow = shadow,
                                textAlign = TextAlign.End,
                            ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                state?.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style =
                            TextStyle(
                                fontFamily = WidgetFont,
                                fontSize = 13.sp,
                                color = textColor,
                                shadow = shadow,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PreviewIcon(
                        if (isAbs) R.drawable.ic_rewind_backward_30
                        else R.drawable.ic_player_skip_back,
                        textColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PreviewIcon(
                        if (isAbs) R.drawable.ic_rewind_forward_30
                        else R.drawable.ic_player_skip_forward,
                        textColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Image(
                            painter =
                                painterResource(
                                    playIcon(status == AudioWidgetStatus.PLAYING, lightText)
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewIcon(icon: Int, tint: Color) {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SegmentedSetting(
    title: String,
    subtitle: String?,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
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
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        AfinitySegmentedTabs(
            tabs = options.map { SegmentedTab(it) },
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            horizontalPadding = 0.dp,
        )
    }
}

@Composable
private fun OpacitySetting(value: Int, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = stringResource(R.string.widget_setting_opacity),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.widget_setting_opacity_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = stringResource(R.string.widget_opacity_value_fmt, value),
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
            value = value.toFloat(),
            onValueChange = { onValueChange((it / 5f).roundToInt() * 5) },
            valueRange = 0f..100f,
            steps = 19,
            modifier = Modifier.height(24.dp),
        )
    }
}

private fun statusText(status: AudioWidgetStatus): Int =
    when (status) {
        AudioWidgetStatus.PLAYING -> R.string.widget_status_playing
        AudioWidgetStatus.PAUSED -> R.string.widget_status_paused
        AudioWidgetStatus.STOPPED -> R.string.widget_status_stopped
    }

private fun badgeIcon(status: AudioWidgetStatus, materialYou: Boolean): Int =
    when (status) {
        AudioWidgetStatus.PLAYING ->
            if (materialYou) R.drawable.widget_badge_you_playing
            else R.drawable.widget_badge_playing
        AudioWidgetStatus.PAUSED ->
            if (materialYou) R.drawable.widget_badge_you_paused else R.drawable.widget_badge_paused
        AudioWidgetStatus.STOPPED ->
            if (materialYou) R.drawable.widget_badge_you_stopped
            else R.drawable.widget_badge_stopped
    }

private fun playIcon(isPlaying: Boolean, lightText: Boolean): Int =
    when {
        isPlaying && lightText -> R.drawable.widget_pause_circle
        isPlaying -> R.drawable.widget_pause_circle_dark
        lightText -> R.drawable.widget_play_circle
        else -> R.drawable.widget_play_circle_dark
    }
