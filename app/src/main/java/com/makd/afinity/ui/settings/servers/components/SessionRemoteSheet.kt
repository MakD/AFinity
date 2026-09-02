package com.makd.afinity.ui.settings.servers.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.settings.servers.utils.formatTicks
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.SessionInfoDto

private const val TICKS_PER_SECOND = 10_000_000L
private const val MESSAGE_MAX_LENGTH = 200

@Composable
private fun SessionMessageDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_message_outgoing),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.dialog_session_message_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            AfinityTextField(
                value = text,
                onValueChange = { if (it.length <= MESSAGE_MAX_LENGTH) text = it },
                placeholder = stringResource(R.string.dialog_session_message_placeholder),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onSend(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
fun SessionRemoteSheet(
    session: SessionInfoDto,
    baseUrl: String,
    isOwnSession: Boolean,
    pendingPause: Boolean?,
    onDismiss: () -> Unit,
    onTogglePause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlaystate: (PlaystateCommand) -> Unit,
    onSetVolume: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onSendMessage: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val item = session.nowPlayingItem

    val supported = session.capabilities?.supportedCommands.orEmpty()
    val canSetVolume = GeneralCommandType.SET_VOLUME in supported
    val canMute =
        GeneralCommandType.TOGGLE_MUTE in supported ||
            (GeneralCommandType.MUTE in supported && GeneralCommandType.UNMUTE in supported)
    val canMessage = GeneralCommandType.DISPLAY_MESSAGE in supported

    var showMessageDialog by remember { mutableStateOf(false) }

    if (showMessageDialog) {
        SessionMessageDialog(
            onDismiss = { showMessageDialog = false },
            onSend = {
                onSendMessage(it)
                showMessageDialog = false
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isOwnSession) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_info),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.session_remote_other_user_warning,
                                    session.userName
                                        ?: stringResource(R.string.unknown_user),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            val artworkUrl =
                remember(baseUrl, item?.id, item?.backdropImageTags, item?.imageTags) {
                    val id = item?.id ?: return@remember null
                    if (baseUrl.isEmpty()) return@remember null
                    val backdropTag = item.backdropImageTags?.firstOrNull()
                    if (backdropTag != null) {
                        "$baseUrl/Items/$id/Images/Backdrop?tag=$backdropTag&maxWidth=600"
                    } else {
                        item.imageTags?.get(ImageType.PRIMARY)?.let {
                            "$baseUrl/Items/$id/Images/Primary?tag=$it&maxWidth=600"
                        }
                    }
                }

            if (artworkUrl != null) {
                AsyncImage(
                    imageUrl = artworkUrl,
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            Text(
                text = item?.name ?: stringResource(R.string.unknown_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text =
                    buildString {
                        append(session.deviceName ?: stringResource(R.string.unknown_device))
                        session.client?.let { append(" · $it") }
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val basePositionTicks = session.playState?.positionTicks ?: 0L
            val serverPaused = session.playState?.isPaused ?: true
            val isPaused = pendingPause ?: serverPaused
            val runtimeTicks = item?.runTimeTicks

            var localPositionTicks by
                remember(basePositionTicks) { mutableStateOf(basePositionTicks) }
            var scrubbing by remember { mutableStateOf(false) }
            var scrubValue by remember { mutableFloatStateOf(0f) }

            LaunchedEffect(basePositionTicks, serverPaused) {
                if (!serverPaused) {
                    while (true) {
                        delay(1000)
                        localPositionTicks += TICKS_PER_SECOND
                    }
                }
            }

            val canSeek = session.playState?.canSeek ?: false

            if (runtimeTicks != null && runtimeTicks > 0) {
                val fraction =
                    if (scrubbing) scrubValue
                    else
                        (localPositionTicks.toDouble() / runtimeTicks.toDouble())
                            .toFloat()
                            .coerceIn(0f, 1f)

                Slider(
                    value = fraction,
                    onValueChange = {
                        scrubbing = true
                        scrubValue = it
                    },
                    onValueChangeFinished = {
                        scrubbing = false
                        onSeekTo((scrubValue * runtimeTicks).toLong())
                    },
                    enabled = canSeek,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTicks((fraction * runtimeTicks).toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatTicks(runtimeTicks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onPlaystate(PlaystateCommand.PREVIOUS_TRACK) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_skip_back),
                        contentDescription = stringResource(R.string.cd_session_previous),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (canSeek) {
                    IconButton(onClick = { onSeekBy(-10L) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rewind_backward_10),
                            contentDescription = stringResource(R.string.cd_session_rewind),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                FilledIconButton(onClick = onTogglePause, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painter =
                            painterResource(
                                id =
                                    if (isPaused) R.drawable.ic_player_play_filled
                                    else R.drawable.ic_player_pause_filled
                            ),
                        contentDescription =
                            stringResource(
                                if (isPaused) R.string.cd_session_play
                                else R.string.cd_session_pause
                            ),
                        modifier = Modifier.size(28.dp),
                    )
                }
                if (canSeek) {
                    IconButton(onClick = { onSeekBy(30L) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rewind_forward_30),
                            contentDescription = stringResource(R.string.cd_session_forward),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                IconButton(onClick = { onPlaystate(PlaystateCommand.NEXT_TRACK) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_skip_forward),
                        contentDescription = stringResource(R.string.cd_session_next),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            if (canMute || canSetVolume) {
                val isMuted = session.playState?.isMuted == true
                val volumeLevel = session.playState?.volumeLevel
                var localVolume by
                    remember(volumeLevel) { mutableFloatStateOf((volumeLevel ?: 100).toFloat()) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (canMute) {
                        IconButton(onClick = onToggleMute) {
                            Icon(
                                painter =
                                    painterResource(
                                        id =
                                            if (isMuted) R.drawable.ic_volume_off
                                            else R.drawable.ic_volume_up
                                    ),
                                contentDescription = stringResource(R.string.cd_session_mute),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    if (canSetVolume) {
                        Slider(
                            value = localVolume,
                            onValueChange = { localVolume = it },
                            onValueChangeFinished = { onSetVolume(localVolume.toInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canMessage) {
                    FilledTonalButton(
                        onClick = { showMessageDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_message_outgoing),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Box(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(R.string.action_session_message))
                    }
                }
                FilledTonalButton(
                    onClick = {
                        onPlaystate(PlaystateCommand.STOP)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(modifier = Modifier.size(8.dp))
                    Text(text = stringResource(R.string.action_session_stop))
                }
            }
        }
    }
}