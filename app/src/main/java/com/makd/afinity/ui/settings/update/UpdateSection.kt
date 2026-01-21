package com.makd.afinity.ui.settings.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.updater.models.GitHubRelease
import com.makd.afinity.data.updater.models.UpdateCheckFrequency
import com.makd.afinity.data.updater.models.UpdateState

@Composable
fun UpdateSection(
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    var showUpdateDialog by remember { mutableStateOf(false) }
    var pendingRelease by remember { mutableStateOf<GitHubRelease?>(null) }

    if (updateState is UpdateState.Available && !showUpdateDialog && pendingRelease == null) {
        pendingRelease = (updateState as UpdateState.Available).release
        showUpdateDialog = true
    }

    if (updateState is UpdateState.Downloaded && !showUpdateDialog && pendingRelease == null) {
        pendingRelease = (updateState as UpdateState.Downloaded).release
        showUpdateDialog = true
    }

    if (showUpdateDialog && pendingRelease != null) {
        UpdateAvailableDialog(
            release = pendingRelease!!,
            downloadedFile = if (updateState is UpdateState.Downloaded) (updateState as UpdateState.Downloaded).file else null,
            isDownloading = updateState is UpdateState.Downloading,
            onDownload = { viewModel.downloadUpdate(pendingRelease!!) },
            onInstall = { file ->
                viewModel.installUpdate(file)
                showUpdateDialog = false
                viewModel.dismissUpdate()
                pendingRelease = null
            },
            onDismiss = {
                showUpdateDialog = false
                viewModel.dismissUpdate()
                pendingRelease = null
            }
        )
    }

    SettingsGroup(title = "Updates", modifier = modifier) {
        CheckForUpdatesItem(
            updateState = updateState,
            lastCheckTime = uiState.lastCheckTime,
            onCheckClick = { viewModel.checkForUpdates() }
        )

        SettingsDivider()

        UpdateFrequencySelector(
            currentFrequency = uiState.checkFrequency,
            onFrequencySelected = { viewModel.setCheckFrequency(it) }
        )
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
private fun CheckForUpdatesItem(
    updateState: UpdateState,
    lastCheckTime: String,
    onCheckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isChecking = updateState is UpdateState.Checking
    val isDownloading = updateState is UpdateState.Downloading

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isChecking && !isDownloading) { onCheckClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_system_update),
            contentDescription = null,
            tint = if (isChecking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Check for Updates",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )

            AnimatedVisibility(
                visible = updateState is UpdateState.Downloading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    val progress = (updateState as? UpdateState.Downloading)?.progress ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Downloading...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$progress%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(
                visible = updateState !is UpdateState.Downloading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = when (updateState) {
                        is UpdateState.Checking -> "Checking..."
                        is UpdateState.UpToDate -> "Up to date • $lastCheckTime"
                        is UpdateState.Error -> "Check failed • $lastCheckTime"
                        else -> "Last check: $lastCheckTime"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (updateState) {
                        is UpdateState.Checking -> MaterialTheme.colorScheme.primary
                        is UpdateState.UpToDate -> MaterialTheme.colorScheme.tertiary
                        is UpdateState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun UpdateFrequencySelector(
    currentFrequency: UpdateCheckFrequency,
    onFrequencySelected: (UpdateCheckFrequency) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_schedule),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Check Frequency",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = currentFrequency.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            UpdateCheckFrequency.entries.forEach { frequency ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = frequency.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (frequency == currentFrequency) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_circle_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        onFrequencySelected(frequency)
                        expanded = false
                    }
                )
            }
        }
    }
}