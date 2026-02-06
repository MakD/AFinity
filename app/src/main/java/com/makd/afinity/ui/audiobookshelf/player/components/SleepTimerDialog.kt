package com.makd.afinity.ui.audiobookshelf.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerDialog(
    currentTimerEndTime: Long?,
    onTimerSelected: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()

    val timerOptions = listOf(5, 10, 15, 30, 45, 60, 90, 120)

    val isTimerActive =
        currentTimerEndTime != null && currentTimerEndTime > System.currentTimeMillis()
    val remainingMinutes =
        if (isTimerActive && currentTimerEndTime != null) {
            ((currentTimerEndTime - System.currentTimeMillis()) / 60000).toInt().coerceAtLeast(1)
        } else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 24.dp)) {
            Text(
                text = "SLEEP TIMER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isTimerActive && remainingMinutes != null) {
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
                            text = "$remainingMinutes min remaining",
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
                            Text("Stop Timer")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Set New Timer",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(timerOptions) { minutes ->
                    TimerTile(minutes = minutes, onClick = { onTimerSelected(minutes) })
                }
            }
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
                text = "min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
