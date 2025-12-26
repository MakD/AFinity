package com.makd.afinity.ui.player.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.jdtech.mpv.MPVLib
import timber.log.Timber

@Composable
fun MpvSurface(
    modifier: Modifier = Modifier,
    onSurfaceCreated: () -> Unit = {},
    onSurfaceDestroyed: () -> Unit = {}
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        MPVLib.attachSurface(holder.surface)
                        MPVLib.setOptionString("force-window", "yes")
                        MPVLib.setOptionString("vo", "gpu")
                        MPVLib.setOptionString("vid", "auto")
                        onSurfaceCreated()
                        Timber.d("MPV surface created and attached")
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
                        Timber.d("MPV surface changed: ${width}x${height}")
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        MPVLib.setOptionString("vid", "no")
                        MPVLib.setOptionString("vo", "null")
                        MPVLib.setOptionString("force-window", "no")
                        MPVLib.detachSurface()
                        onSurfaceDestroyed()
                        Timber.d("MPV surface destroyed and detached")
                    }
                })
            }
        }
    )
}