package com.makd.afinity.ui.audiobookshelf.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.ui.player.components.formatSleepCountdown
import com.makd.afinity.ui.player.components.rememberSleepTimerRemainingMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerDialog(
    currentTimerEndTime: Long?,
    onTimerSelected: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    chapterCount: Int = 0,
    currentChapterIndex: Int = -1,
    isPodcast: Boolean = false,
    chapterTargetRemainingSeconds: (Int) -> Double? = { null },
    onChapterTimerSelected: (Int) -> Unit = {},
    activeChapterTarget: Int? = null,
    activeTargetRemainingSeconds: Double? = null,
) {
    val sheetState = rememberModalBottomSheetState()

    val timerOptions = listOf(5, 10, 15, 30, 45, 60, 90, 120)

    val durationRemainingMs = rememberSleepTimerRemainingMs(currentTimerEndTime)
    val isDurationTimerActive = currentTimerEndTime != null && durationRemainingMs > 0L
    val isChapterTimerActive = activeTargetRemainingSeconds != null
    val isTimerActive = isDurationTimerActive || isChapterTimerActive
    val remainingMs =
        if (isChapterTimerActive) (activeTargetRemainingSeconds * 1000).toLong()
        else durationRemainingMs

    var extraChapters by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.abs_sleep_timer_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isTimerActive) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_timer),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.player_time_left,
                                    formatSleepCountdown(remainingMs),
                                ),
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onCancelTimer,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(painterResource(id = R.drawable.ic_timer_off), null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.abs_sleep_timer_stop))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(R.string.abs_sleep_timer_set_new),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val chapterRemaining = chapterTargetRemainingSeconds(extraChapters)
            if (chapterRemaining != null) {
                val maxExtra =
                    if (chapterCount > 0 && currentChapterIndex >= 0)
                        chapterCount - 1 - currentChapterIndex
                    else 0

                ChapterTimerRow(
                    label =
                        stringResource(
                            if (isPodcast) R.string.abs_sleep_timer_end_of_episode
                            else R.string.abs_sleep_timer_end_of_chapter
                        ),
                    extraChapters = extraChapters,
                    canDecrease = extraChapters > 0,
                    canIncrease = extraChapters < maxExtra,
                    remainingSeconds = chapterRemaining,
                    selected = activeChapterTarget != null,
                    onDecrease = { extraChapters = (extraChapters - 1).coerceAtLeast(0) },
                    onIncrease = { extraChapters = (extraChapters + 1).coerceAtMost(maxExtra) },
                    onClick = { onChapterTimerSelected(extraChapters) },
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(timerOptions, key = { it }) { minutes ->
                    TimerTile(minutes = minutes, onClick = { onTimerSelected(minutes) })
                }
            }
        }
    }
}

@Composable
private fun ChapterTimerRow(
    label: String,
    extraChapters: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    remainingSeconds: Double,
    selected: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (extraChapters > 0) "$label +$extraChapters" else label,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    stringResource(
                        R.string.player_time_left,
                        formatSleepCountdown((remainingSeconds * 1000).toLong()),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onDecrease, enabled = canDecrease) {
            Icon(
                painter = painterResource(id = R.drawable.ic_minus),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onIncrease, enabled = canIncrease) {
            Icon(
                painter = painterResource(id = R.drawable.ic_plus),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TimerTile(minutes: Int, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.aspectRatio(1.2f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = minutes.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.unit_minutes_short),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
