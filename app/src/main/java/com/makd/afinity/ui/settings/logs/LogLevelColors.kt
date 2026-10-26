package com.makd.afinity.ui.settings.logs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.makd.afinity.util.logging.LogLevel

object LogLevelColors {

    private const val LIGHT_SURFACE_LUMINANCE = 0.5f
    private const val ERROR_CONTAINER_ALPHA_ON_LIGHT = 0.08f
    private const val WARN_CONTAINER_ALPHA_ON_LIGHT = 0.1f

    private val Error = Color(0xFFFF8A80)
    private val ErrorContainer = Color(0xFF2A1618)
    private val Warn = Color(0xFFE8B33D)
    private val WarnContainer = Color(0xFF2A2314)
    private val WarnOnLight = Color(0xFF8A5A00)
    private val ConsoleSurface = Color(0xFF0E0D12)

    @Composable
    @ReadOnlyComposable
    private fun isLightSurface(): Boolean =
        MaterialTheme.colorScheme.surface.luminance() > LIGHT_SURFACE_LUMINANCE

    @Composable
    @ReadOnlyComposable
    private fun error(): Color = if (isLightSurface()) MaterialTheme.colorScheme.error else Error

    @Composable
    @ReadOnlyComposable
    private fun warn(): Color = if (isLightSurface()) WarnOnLight else Warn

    val consoleSurface: Color
        @Composable
        @ReadOnlyComposable
        get() =
            if (isLightSurface()) MaterialTheme.colorScheme.surfaceContainerLow else ConsoleSurface

    @Composable
    @ReadOnlyComposable
    fun content(level: LogLevel): Color =
        when (level) {
            LogLevel.ERROR -> error()
            LogLevel.WARN -> warn()
            LogLevel.INFO -> MaterialTheme.colorScheme.primary
            LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    @Composable
    @ReadOnlyComposable
    fun container(level: LogLevel): Color =
        when {
            level == LogLevel.ERROR && isLightSurface() ->
                MaterialTheme.colorScheme.error.copy(alpha = ERROR_CONTAINER_ALPHA_ON_LIGHT)
            level == LogLevel.WARN && isLightSurface() ->
                WarnOnLight.copy(alpha = WARN_CONTAINER_ALPHA_ON_LIGHT)
            level == LogLevel.ERROR -> ErrorContainer
            level == LogLevel.WARN -> WarnContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }

    @Composable
    @ReadOnlyComposable
    fun ribbon(level: LogLevel): Color =
        when (level) {
            LogLevel.ERROR -> error()
            LogLevel.WARN -> warn()
            LogLevel.INFO -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            LogLevel.DEBUG -> MaterialTheme.colorScheme.outlineVariant
        }

    @Composable
    @ReadOnlyComposable
    fun expandedTint(level: LogLevel): Color =
        when (level) {
            LogLevel.ERROR -> error().copy(alpha = 0.13f)
            LogLevel.WARN -> warn().copy(alpha = 0.11f)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        }

    @Composable
    @ReadOnlyComposable
    fun rowTint(level: LogLevel): Color =
        when (level) {
            LogLevel.ERROR -> error().copy(alpha = 0.09f)
            LogLevel.WARN -> warn().copy(alpha = 0.07f)
            else -> Color.Transparent
        }
}
