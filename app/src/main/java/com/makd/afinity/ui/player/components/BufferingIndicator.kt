package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R

@Composable
fun ErrorIndicator(
    isVisible: Boolean,
    errorMessage: String?,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(animationSpec = tween(200)) + fadeIn(),
        exit = scaleOut(animationSpec = tween(200)) + fadeOut(),
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.9f)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.player_error_title),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }

                Button(
                    onClick = onRetryClick,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.action_retry),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
