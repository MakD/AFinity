package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.media.AfinityChapter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
fun TrickplayPreview(
    isVisible: Boolean,
    previewImage: ImageBitmap?,
    positionMs: Long,
    durationMs: Long,
    chapters: List<AfinityChapter>,
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
            Box(modifier = Modifier.fillMaxSize()) {
                val seekBarY = configuration.screenHeightDp.dp - 80.dp

                val startPadding = 24.dp
                val endPadding = 68.dp

                val actualSliderWidth = screenWidth - startPadding - endPadding

                val progress = if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                val targetX = startPadding + (actualSliderWidth * progress)

                val constrainedX = when {
                    targetX - previewWidth / 2 < 8.dp -> 8.dp
                    targetX + previewWidth / 2 > screenWidth - 8.dp -> screenWidth - previewWidth - 8.dp
                    else -> targetX - previewWidth / 2
                }

                Column(
                    modifier = Modifier
                        .offset(x = constrainedX, y = seekBarY - previewHeight - 40.dp)
                        .width(previewWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier.size(previewWidth, previewHeight),
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Image(
                            bitmap = previewImage,
                            contentDescription = "Video preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    val currentChapter = chapters.lastOrNull { it.startPosition <= positionMs }
                    if (currentChapter != null && !currentChapter.name.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentChapter.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}