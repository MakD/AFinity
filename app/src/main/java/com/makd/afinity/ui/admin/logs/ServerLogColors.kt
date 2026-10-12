package com.makd.afinity.ui.admin.logs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.makd.afinity.ui.settings.logs.LogLevelColors
import com.makd.afinity.util.logging.LogLevel

private const val LIGHT_SURFACE_LUMINANCE = 0.5f
private const val ERROR_CONTAINER_ALPHA = 0.08f
private const val WARN_CONTAINER_ALPHA = 0.1f

private val WarnOnLight = Color(0xFF8A5A00)

internal object ServerLogColors {

    @Composable
    @ReadOnlyComposable
    fun content(level: LogLevel): Color =
        when {
            !isLightSurface() -> LogLevelColors.content(level)
            level == LogLevel.ERROR -> MaterialTheme.colorScheme.error
            level == LogLevel.WARN -> WarnOnLight
            else -> LogLevelColors.content(level)
        }

    @Composable
    @ReadOnlyComposable
    fun container(level: LogLevel): Color =
        when {
            !isLightSurface() -> LogLevelColors.container(level)
            level == LogLevel.ERROR ->
                MaterialTheme.colorScheme.error.copy(alpha = ERROR_CONTAINER_ALPHA)
            level == LogLevel.WARN -> WarnOnLight.copy(alpha = WARN_CONTAINER_ALPHA)
            else -> LogLevelColors.container(level)
        }

    @Composable
    @ReadOnlyComposable
    private fun isLightSurface(): Boolean =
        MaterialTheme.colorScheme.surface.luminance() > LIGHT_SURFACE_LUMINANCE
}
