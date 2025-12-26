package com.makd.afinity.ui.settings.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                            painter = painterResource(id = R.drawable.ic_arrow_left),
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
                    onAutoPlayToggle = viewModel::toggleAutoPlay,
                    onSkipIntroToggle = viewModel::toggleSkipIntro,
                    onSkipOutroToggle = viewModel::toggleSkipOutro,
                    onUseExoPlayerToggle = viewModel::toggleUseExoPlayer,
                    onPipGestureToggle = viewModel::togglePipGesture
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
    onAutoPlayToggle: (Boolean) -> Unit,
    onSkipIntroToggle: (Boolean) -> Unit,
    onSkipOutroToggle: (Boolean) -> Unit,
    onUseExoPlayerToggle: (Boolean) -> Unit,
    onPipGestureToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerSettingsSection(
        title = "Playback",
        icon = painterResource(id = R.drawable.ic_play_circle),
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
            subtitle = "Use home button or gesture to enter picture-in-picture while video is playing",
            checked = pipGestureEnabled,
            onCheckedChange = onPipGestureToggle
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
private fun PlayerSettingsSection(
    title: String,
    icon: Painter,
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