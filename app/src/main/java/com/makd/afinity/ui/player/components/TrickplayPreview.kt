package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.data.models.media.AfinityChapter

private val SEEK_BAR_START_PADDING = 24.dp
private val SEEK_BAR_END_PADDING = 78.dp
private val SEEK_BAR_TOP_GAP = 8.dp
private val SEEK_BAR_BOTTOM_CLEARANCE = 80.dp
private val PREVIEW_MARGIN = 8.dp

@Composable
fun TrickplayPreview(
    isVisible: Boolean,
    previewImage: ImageBitmap?,
    positionMs: Long,
    durationMs: Long,
    chapters: List<AfinityChapter>,
    modifier: Modifier = Modifier,
) {
    val (previewWidth, previewHeight) =
        remember(previewImage) {
            if (previewImage != null) {
                val width = previewImage.width.toFloat()
                val height = previewImage.height.toFloat()
                val aspectRatio = width / height
                if (width > height) {
                    220.dp to 124.dp
                } else {
                    (220.dp * aspectRatio) to 220.dp
                }
            } else {
                220.dp to 124.dp
            }
        }

    AnimatedVisibility(
        visible = isVisible && previewImage != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        if (previewImage != null) {
            BoxWithConstraints(
                modifier =
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                            )
                        )
            ) {
                val sliderWidth = maxWidth - SEEK_BAR_START_PADDING - SEEK_BAR_END_PADDING
                val progress =
                    if (durationMs > 0)
                        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    else 0f
                val targetX = SEEK_BAR_START_PADDING + (sliderWidth * progress)

                val minX = PREVIEW_MARGIN
                val maxX = maxWidth - previewWidth - PREVIEW_MARGIN
                val constrainedX =
                    when {
                        targetX - previewWidth / 2 < minX -> minX
                        targetX + previewWidth / 2 > maxX -> maxX
                        else -> targetX - previewWidth / 2
                    }

                Card(
                    modifier =
                        Modifier.align(Alignment.BottomStart)
                            .offset(
                                x = constrainedX,
                                y = -(SEEK_BAR_BOTTOM_CLEARANCE + previewHeight + SEEK_BAR_TOP_GAP),
                            )
                            .size(previewWidth, previewHeight),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = previewImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )

                        val currentChapter = chapters.lastOrNull { it.startPosition <= positionMs }

                        AnimatedContent(
                            targetState = currentChapter?.name,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(200)) togetherWith
                                    fadeOut(animationSpec = tween(200)) using
                                    SizeTransform(clip = false)
                            },
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                            label = "ChapterNameAnimation",
                        ) { chapterName ->
                            if (!chapterName.isNullOrEmpty()) {
                                Box(
                                    modifier =
                                        Modifier.background(
                                                color = Color.Black.copy(alpha = 0.75f),
                                                shape = RoundedCornerShape(4.dp),
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = chapterName,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
