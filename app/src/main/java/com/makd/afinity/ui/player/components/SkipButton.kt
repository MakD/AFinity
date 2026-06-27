package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinitySegment

@Composable
fun SkipButton(
    segment: AfinitySegment,
    skipButtonText: String,
    onClick: (AfinitySegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timerProgress = remember { Animatable(1f) }

    LaunchedEffect(segment) {
        timerProgress.snapTo(1f)
        timerProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 8000, easing = LinearEasing),
        )
    }

    AnimatedVisibility(
        visible = true,
        enter =
            slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) +
                fadeIn(animationSpec = tween(300)),
        exit =
            slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) +
                fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        ElevatedButton(
            onClick = { onClick(segment) },
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
                    progress = { timerProgress.value },
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_next),
                    contentDescription = stringResource(R.string.cd_skip_button_icon),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = skipButtonText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
