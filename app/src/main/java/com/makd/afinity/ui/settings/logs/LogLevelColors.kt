package com.makd.afinity.ui.settings.logs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.makd.afinity.util.logging.LogLevel

object LogLevelColors {

    private val Error = Color(0xFFFF8A80)
    private val ErrorContainer = Color(0xFF2A1618)
    private val Warn = Color(0xFFE8B33D)
    private val WarnContainer = Color(0xFF2A2314)

    val consoleSurface = Color(0xFF0E0D12)

    @Composable
    @ReadOnlyComposable
    fun content(level: LogLevel): Color =
        when (level) {
            LogLevel.ERROR -> Error
            LogLevel.WARN -> Warn
            LogLevel.INFO -> MaterialTheme.colorScheme.primary
            LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    @Composable
    @ReadOnlyComposable
    fun container(level: LogLevel): Color =
        when (level) {
            LogLevel.ERROR -> ErrorContainer
            LogLevel.WARN -> WarnContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }

    @Composable
    @ReadOnlyComposable
    fun ribbon(level: LogLevel): Color =
        when (level) {
            LogLevel.ERROR -> Error
            LogLevel.WARN -> Warn
            LogLevel.INFO -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            LogLevel.DEBUG -> MaterialTheme.colorScheme.outlineVariant
        }

    @Composable
    @ReadOnlyComposable
    fun rowTint(level: LogLevel): Color =
        when (level) {
            LogLevel.ERROR -> Error.copy(alpha = 0.09f)
            LogLevel.WARN -> Warn.copy(alpha = 0.07f)
            else -> Color.Transparent
        }
}
