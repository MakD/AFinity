package com.makd.afinity.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.makd.afinity.R
import kotlin.math.absoluteValue

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { /* Consume tap - prevent dismissal */ })
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Current Speed ${String.format("%.2f", sliderSpeed)}x",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                sliderSpeed = (sliderSpeed - 0.25f).coerceIn(0.25f, 2.0f)
                                onSpeedChange(sliderSpeed)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_remove),
                                contentDescription = "Decrease speed",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Slider(
                            value = sliderSpeed,
                            onValueChange = { newSpeed ->
                                sliderSpeed = newSpeed
                            },
                            onValueChangeFinished = {
                                onSpeedChange(sliderSpeed)
                            },
                            valueRange = 0.25f..2.0f,
                            steps = 6,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        IconButton(
                            onClick = {
                                sliderSpeed = (sliderSpeed + 0.25f).coerceIn(0.25f, 2.0f)
                                onSpeedChange(sliderSpeed)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_add),
                                contentDescription = "Increase speed",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SpeedChip(
                            speed = 0.25f,
                            isSelected = (sliderSpeed - 0.25f).absoluteValue < 0.01f,
                            onClick = {
                                sliderSpeed = 0.25f
                                onSpeedChange(0.25f)
                            }
                        )
                        SpeedChip(
                            speed = 0.50f,
                            isSelected = (sliderSpeed - 0.50f).absoluteValue < 0.01f,
                            onClick = {
                                sliderSpeed = 0.50f
                                onSpeedChange(0.50f)
                            }
                        )
                        SpeedChip(
                            speed = 1.00f,
                            isSelected = (sliderSpeed - 1.00f).absoluteValue < 0.01f,
                            onClick = {
                                sliderSpeed = 1.00f
                                onSpeedChange(1.00f)
                            }
                        )
                        SpeedChip(
                            speed = 1.50f,
                            isSelected = (sliderSpeed - 1.50f).absoluteValue < 0.01f,
                            onClick = {
                                sliderSpeed = 1.50f
                                onSpeedChange(1.50f)
                            }
                        )
                        SpeedChip(
                            speed = 2.00f,
                            isSelected = (sliderSpeed - 2.00f).absoluteValue < 0.01f,
                            onClick = {
                                sliderSpeed = 2.00f
                                onSpeedChange(2.00f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = String.format("%.2f", speed),
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}