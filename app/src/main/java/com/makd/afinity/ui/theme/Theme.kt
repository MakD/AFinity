package com.makd.afinity.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

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
    content: @Composable () -> Unit,
) {
    val mode = ThemeMode.fromString(themeMode)
    val systemInDarkTheme = isSystemInDarkTheme()

    val darkTheme =
        when (mode) {
            ThemeMode.SYSTEM -> systemInDarkTheme
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AMOLED -> true
        }

    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
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

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
