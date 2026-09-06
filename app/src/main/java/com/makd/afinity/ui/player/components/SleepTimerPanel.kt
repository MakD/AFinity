package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.player.SLEEP_TIMER_EXTEND_MINUTES
import com.makd.afinity.data.models.player.SLEEP_TIMER_PROMPT_THRESHOLD_MS
import com.makd.afinity.data.models.player.SleepTimerMode
import com.makd.afinity.ui.player.PlayerViewModel

private val SLEEP_TIMER_DURATION_OPTIONS = listOf(5, 15, 30, 45, 60, 90)

@Composable
fun SleepTimerPanel(
    uiState: PlayerViewModel.PlayerUiState,
    endOfItemRemainingMs: Long,
    onSelectMode: (SleepTimerMode) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val armed = uiState.isSleepTimerArmed
    val selectedMinutes = (uiState.sleepTimerMode as? SleepTimerMode.Duration)?.minutes
    val endOfItemSelected = uiState.sleepTimerMode is SleepTimerMode.EndOfItem

    Box(
        modifier =
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier =
                Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .widthIn(min = 340.dp, max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = stringResource(R.string.player_sleep_timer_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (armed) {
                        Text(
                            text =
                                stringResource(
                                    R.string.player_time_left,
                                    formatSleepCountdown(uiState.sleepTimerRemainingMs),
                                ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SLEEP_TIMER_DURATION_OPTIONS.forEach { minutes ->
                        DurationTile(
                            minutes = minutes,
                            selected = selectedMinutes == minutes,
                            onClick = {
                                onSelectMode(SleepTimerMode.Duration(minutes))
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (endOfItemRemainingMs > 0L) {
                    Spacer(modifier = Modifier.height(12.dp))
                    EndOfItemRow(
                        isMovie = uiState.currentItem is AfinityMovie,
                        remainingMs = endOfItemRemainingMs,
                        selected = endOfItemSelected,
                        onClick = {
                            onSelectMode(SleepTimerMode.EndOfItem)
                            onDismiss()
                        },
                    )
                }

                if (armed) {
                    Spacer(modifier = Modifier.height(12.dp))
                    StopRow(
                        onClick = {
                            onCancel()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationTile(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = minutes.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.unit_minutes_short),
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EndOfItemRow(
    isMovie: Boolean,
    remainingMs: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painter =
                painterResource(
                    id = if (isMovie) R.drawable.ic_movie else R.drawable.ic_episodes_list
                ),
            contentDescription = null,
            tint =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp),
        )
        Text(
            text =
                stringResource(
                    if (isMovie) R.string.player_sleep_timer_end_of_movie
                    else R.string.player_sleep_timer_end_of_episode
                ),
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.player_time_left, formatSleepCountdown(remainingMs)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StopRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_timer_off),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.player_sleep_timer_stop),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
fun SleepTimerExtendPrompt(
    remainingMs: Long,
    onExtend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress =
        (remainingMs.toFloat() / SLEEP_TIMER_PROMPT_THRESHOLD_MS.toFloat()).coerceIn(0f, 1f)

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = stringResource(R.string.player_sleep_timer_ends_in, formatSleepCountdown(remainingMs)),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.78f),
        )

        Spacer(modifier = Modifier.height(8.dp))

        ElevatedButton(
            onClick = onExtend,
            shape = CircleShape,
            colors =
                ButtonDefaults.elevatedButtonColors(
                    containerColor =
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            elevation =
                ButtonDefaults.elevatedButtonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp,
                ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_moon_filled),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text =
                    pluralStringResource(
                        R.plurals.player_sleep_timer_extend,
                        SLEEP_TIMER_EXTEND_MINUTES,
                        SLEEP_TIMER_EXTEND_MINUTES,
                    ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SleepTimerEndedOverlay(
    uiState: PlayerViewModel.PlayerUiState,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        dim.animateTo(
            targetValue = 0.93f,
            animationSpec = tween(durationMillis = 5000, easing = LinearEasing),
        )
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onResume,
                ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = !uiState.isPlaying,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(200)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_moon_filled),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.size(32.dp),
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.player_sleep_timer_ended),
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.92f),
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text =
                        stringResource(
                            R.string.player_sleep_timer_paused_fmt,
                            formatSleepCountdown(uiState.currentPosition),
                            pluralStringResource(
                                R.plurals.player_sleep_timer_closing_in,
                                uiState.sleepTimerCloseInSeconds,
                                uiState.sleepTimerCloseInSeconds,
                            ),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.alpha(if (uiState.sleepTimerCloseInSeconds > 0) 1f else 0f),
                )

                Spacer(modifier = Modifier.height(18.dp))

                ElevatedButton(
                    onClick = onResume,
                    shape = CircleShape,
                    colors =
                        ButtonDefaults.elevatedButtonColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    elevation =
                        ButtonDefaults.elevatedButtonElevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 2.dp,
                        ),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_play_filled),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.player_resume),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
