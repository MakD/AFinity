package com.makd.afinity.ui.player.components

import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import dev.jdtech.mpv.MPVLib
import timber.log.Timber

@UnstableApi
@Composable
fun MpvSurface(
    modifier: Modifier = Modifier,
    mpv: MPVLib,
    videoOutput: String = "gpu",
    aspectRatio: Float = 0f,
    fitToVideo: Boolean = true,
    onSurfaceCreated: () -> Unit = {},
    onSurfaceDestroyed: () -> Unit = {},
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                val aspectFrame =
                    AspectRatioFrameLayout(context).apply {
                        layoutParams =
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                )
                                .apply { gravity = Gravity.CENTER }
                        addView(
                            SurfaceView(context).apply {
                                layoutParams =
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                    )
                                holder.addCallback(
                                    object : SurfaceHolder.Callback {
                                        override fun surfaceCreated(holder: SurfaceHolder) {
                                            mpv.attachSurface(holder.surface)
                                            mpv.setOptionString("force-window", "yes")
                                            mpv.setOptionString("vo", videoOutput)
                                            mpv.setOptionString("vid", "auto")
                                            onSurfaceCreated()
                                            Timber.d("MPV surface created and attached")
                                        }

                                        override fun surfaceChanged(
                                            holder: SurfaceHolder,
                                            format: Int,
                                            width: Int,
                                            height: Int,
                                        ) {
                                            mpv.setPropertyString(
                                                "android-surface-size",
                                                "${width}x$height",
                                            )
                                            Timber.d("MPV surface changed: ${width}x${height}")
                                        }

                                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                                            mpv.setOptionString("vid", "no")
                                            mpv.setOptionString("vo", "null")
                                            mpv.setOptionString("force-window", "no")
                                            mpv.detachSurface()
                                            onSurfaceDestroyed()
                                            Timber.d("MPV surface destroyed and detached")
                                        }
                                    }
                                )
                            }
                        )
                    }
                addView(aspectFrame)
            }
        },
        update = { root ->
            val aspectFrame = root.getChildAt(0) as AspectRatioFrameLayout
            if (fitToVideo && aspectRatio > 0f) {
                aspectFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                aspectFrame.setAspectRatio(aspectRatio)
            } else {
                aspectFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        },
    )
}
