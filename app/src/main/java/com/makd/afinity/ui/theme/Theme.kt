package com.makd.afinity.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

val LocalDarkColorScheme = staticCompositionLocalOf { DarkColorScheme }

@Composable
@ReadOnlyComposable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

private val LightColorScheme =
    lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

private val AmoledColorScheme =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color(0xFF1C1C1C),
    )

@Composable
fun AFinityTheme(
    themeMode: String = ThemeMode.SYSTEM.name,
    dynamicColor: Boolean = true,
    appFont: String = "DEFAULT",
    content: @Composable () -> Unit,
) {
    val mode = ThemeMode.fromString(themeMode)
    val systemInDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val darkTheme =
        when (mode) {
            ThemeMode.SYSTEM -> systemInDarkTheme
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AMOLED -> true
        }

    val colorScheme =
        when {
            dynamicColor -> {
                val baseDynamicScheme =
                    if (darkTheme) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context)

                if (mode == ThemeMode.AMOLED) {
                    baseDynamicScheme.copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceVariant = Color(0xFF1C1C1C),
                    )
                } else {
                    baseDynamicScheme
                }
            }
            mode == ThemeMode.AMOLED -> AmoledColorScheme
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val darkScheme =
        when {
            darkTheme -> colorScheme
            dynamicColor -> dynamicDarkColorScheme(context)
            else -> DarkColorScheme
        }

    val currentTypography = getAppTypography(AppFont.fromString(appFont))

    CompositionLocalProvider(LocalDarkColorScheme provides darkScheme) {
        MaterialTheme(colorScheme = colorScheme, typography = currentTypography, content = content)
    }
}
