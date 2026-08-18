package com.makd.afinity.ui.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.makd.afinity.R
import com.makd.afinity.data.models.syncplay.SyncPlayState
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.player.SyncPlayUiState
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupStateType
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Composable
fun SyncPlayGroupSheet(
    syncPlayState: SyncPlayState,
    uiState: SyncPlayUiState,
    onCreateGroup: (name: String) -> Unit,
    onJoinGroup: (groupId: UUID) -> Unit,
    onLeaveGroup: () -> Unit,
    onRefreshGroups: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!uiState.isJoining) onDismiss() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(0.9f).padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            if (syncPlayState.isInGroup) {
                InGroupContent(
                    syncPlayState = syncPlayState,
                    onLeaveGroup = {
                        onLeaveGroup()
                        onDismiss()
                    },
                )
            } else {
                NotInGroupContent(
                    uiState = uiState,
                    onCreateGroup = onCreateGroup,
                    onJoinGroup = onJoinGroup,
                    onRefreshGroups = onRefreshGroups,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun InGroupContent(
    syncPlayState: SyncPlayState,
    onLeaveGroup: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.syncplay_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = syncPlayState.groupName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            GroupStateChip(state = syncPlayState.groupState)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text =
                pluralStringResource(
                    R.plurals.syncplay_member_count,
                    syncPlayState.members.size,
                    syncPlayState.members.size,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        if (syncPlayState.members.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(syncPlayState.members) { member ->
                    Text(
                        text = member,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onLeaveGroup,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.syncplay_leave_group))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun NotInGroupContent(
    uiState: SyncPlayUiState,
    onCreateGroup: (name: String) -> Unit,
    onJoinGroup: (groupId: UUID) -> Unit,
    onRefreshGroups: () -> Unit,
    onDismiss: () -> Unit,
) {
    var groupName by remember { mutableStateOf("") }
    val isJoining = uiState.isJoining

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.syncplay_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        AfinityTextField(
            value = groupName,
            onValueChange = { groupName = it },
            label = stringResource(R.string.syncplay_group_name),
            enabled = !isJoining,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { if (groupName.isNotBlank()) onCreateGroup(groupName.trim()) },
            enabled = groupName.isNotBlank() && !isJoining,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isJoining) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.syncplay_joining))
            } else {
                Text(text = stringResource(R.string.syncplay_create_group))
            }
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.syncplay_active_groups),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            TextButton(onClick = onRefreshGroups, enabled = !isJoining) {
                Text(text = stringResource(R.string.action_refresh))
            }
        }

        if (uiState.isLoadingGroups) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (uiState.availableGroups.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.syncplay_no_active_groups),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(uiState.availableGroups, key = { it.groupId }) { group ->
                    GroupRow(
                        group = group,
                        isJoining = isJoining,
                        onJoinGroup = onJoinGroup,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onDismiss,
            enabled = !isJoining,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(text = stringResource(R.string.action_cancel))
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun GroupRow(
    group: GroupInfoDto,
    isJoining: Boolean,
    onJoinGroup: (UUID) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.groupName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.syncplay_member_count,
                        group.participants.size,
                        group.participants.size,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = relativeTime(group.lastUpdatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        GroupStateChip(state = group.state)
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
            onClick = { onJoinGroup(group.groupId) },
            enabled = !isJoining,
        ) {
            if (isJoining) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(text = stringResource(R.string.action_join))
            }
        }
    }
}

@Composable
private fun relativeTime(dateTime: LocalDateTime): String {
    val now = LocalDateTime.now()
    val minutes = ChronoUnit.MINUTES.between(dateTime, now).coerceAtLeast(0)
    val hours = minutes / 60
    return when {
        minutes < 1 -> stringResource(R.string.syncplay_active_just_now)
        minutes < 60 -> stringResource(R.string.syncplay_active_minutes_fmt, minutes.toInt())
        hours < 24 -> stringResource(R.string.syncplay_active_hours_fmt, hours.toInt())
        else -> stringResource(R.string.syncplay_active_days_fmt, (hours / 24).toInt())
    }
}

@Composable
private fun GroupStateChip(state: GroupStateType) {
    val (label, color) =
        when (state) {
            GroupStateType.IDLE ->
                stringResource(R.string.syncplay_state_ready) to MaterialTheme.colorScheme.secondary
            GroupStateType.WAITING ->
                stringResource(R.string.syncplay_state_waiting) to
                    MaterialTheme.colorScheme.tertiary
            GroupStateType.PAUSED ->
                stringResource(R.string.syncplay_state_paused) to MaterialTheme.colorScheme.outline
            GroupStateType.PLAYING ->
                stringResource(R.string.syncplay_state_playing) to MaterialTheme.colorScheme.primary
            else ->
                stringResource(R.string.syncplay_state_unknown) to MaterialTheme.colorScheme.outline
        }
    SuggestionChip(
        onClick = {},
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        colors =
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = color.copy(alpha = 0.15f),
                labelColor = color,
            ),
        border =
            SuggestionChipDefaults.suggestionChipBorder(
                enabled = true,
                borderColor = color.copy(alpha = 0.4f),
            ),
    )
}
