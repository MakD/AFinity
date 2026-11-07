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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.data.models.media.AfinityChapter
import kotlin.math.roundToInt

const val TRICKPLAY_PREVIEW_FRACTION = 0.3f
val TrickplayPreviewMinSize = 160.dp
val TrickplayPreviewMaxSize = 300.dp
private val PREVIEW_GAP = 12.dp
private val PREVIEW_MARGIN = 8.dp

@Stable
class SeekBarAnchor {
    private var owner: Any? = null

    var bounds by mutableStateOf<Rect?>(null)
        private set

    fun report(owner: Any, windowBounds: Rect) {
        this.owner = owner
        if (bounds != windowBounds) bounds = windowBounds
    }

    fun release(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        bounds = null
    }
}

@Composable
fun TrickplayPreview(
    isVisible: Boolean,
    previewImage: ImageBitmap?,
    positionMs: Long,
    durationMs: Long,
    chapters: List<AfinityChapter>,
    seekBarAnchor: SeekBarAnchor,
    modifier: Modifier = Modifier,
) {
    val sliderBounds = seekBarAnchor.bounds
    var containerOrigin by remember { mutableStateOf<Offset?>(null) }

    AnimatedVisibility(
        visible = isVisible && previewImage != null && sliderBounds != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier.fillMaxSize().onGloballyPositioned {
                    containerOrigin = it.positionInWindow()
                }
        ) {
            val image = previewImage ?: return@BoxWithConstraints
            val windowSlider = sliderBounds ?: return@BoxWithConstraints
            val origin = containerOrigin ?: return@BoxWithConstraints
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            val safeDrawing = WindowInsets.safeDrawing
            val safeLeft = safeDrawing.getLeft(density, layoutDirection)
            val safeRight = safeDrawing.getRight(density, layoutDirection)
            val safeTop = safeDrawing.getTop(density)
            val containerWidth = constraints.maxWidth.toFloat()

            val slider = windowSlider.translate(-origin)
            val gap = with(density) { PREVIEW_GAP.toPx() }
            val margin = with(density) { PREVIEW_MARGIN.toPx() }
            val longEdge =
                with(density) {
                    (slider.width * TRICKPLAY_PREVIEW_FRACTION).coerceIn(
                        TrickplayPreviewMinSize.toPx(),
                        TrickplayPreviewMaxSize.toPx(),
                    )
                }
            val aspectRatio = image.width.toFloat() / image.height.toFloat()
            var previewWidth = if (aspectRatio >= 1f) longEdge else longEdge * aspectRatio
            var previewHeight = if (aspectRatio >= 1f) longEdge / aspectRatio else longEdge
            val availableHeight = slider.top - gap - safeTop - margin
            if (availableHeight > 0f && previewHeight > availableHeight) {
                previewWidth *= availableHeight / previewHeight
                previewHeight = availableHeight
            }

            val progress =
                if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                else 0f
            val thumbX =
                if (layoutDirection == LayoutDirection.Rtl) slider.right - slider.width * progress
                else slider.left + slider.width * progress
            val minX = safeLeft + margin
            val maxX = (containerWidth - safeRight - margin - previewWidth).coerceAtLeast(minX)
            val x = (thumbX - previewWidth / 2f).coerceIn(minX, maxX)
            val y = slider.top - gap - previewHeight

            Card(
                modifier =
                    Modifier.absoluteOffset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .size(
                            with(density) { previewWidth.toDp() },
                            with(density) { previewHeight.toDp() },
                        ),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = image,
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
