package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
fun TrickplayPreview(
    isVisible: Boolean,
    previewImage: ImageBitmap?,
    position: Float,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val previewWidth = 220.dp
    val previewHeight = 124.dp

    AnimatedVisibility(
        visible = isVisible && previewImage != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (previewImage != null) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val seekBarY = configuration.screenHeightDp.dp - 80.dp

                val seekBarPadding = 16.dp
                val seekBarWidth = screenWidth - (seekBarPadding * 2)
                val seekBarStartX = seekBarPadding

                val targetX = seekBarStartX + (seekBarWidth * position)

                val constrainedX = when {
                    targetX - previewWidth / 2 < 0.dp -> previewWidth / 2
                    targetX + previewWidth / 2 > screenWidth -> screenWidth - previewWidth / 2
                    else -> targetX
                } - previewWidth / 2

                Card(
                    modifier = Modifier
                        .offset(
                            x = constrainedX,
                            y = seekBarY - previewHeight - 16.dp
                        )
                        .size(previewWidth, previewHeight),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp
                    )
                ) {
                    Image(
                        bitmap = previewImage,
                        contentDescription = "Video preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}