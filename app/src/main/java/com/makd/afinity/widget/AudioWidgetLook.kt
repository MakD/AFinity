package com.makd.afinity.widget

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import timber.log.Timber

object AudioWidgetLook {
    private const val DARK_TEXT = 0xFF111111.toInt()
    private const val SECONDARY_LIGHT_TEXT = 0xBFFFFFFF.toInt()
    private const val SECONDARY_DARK_TEXT = 0xBF111111.toInt()
    private const val OPAQUE_BACKGROUND_THRESHOLD = 50

    fun isLightText(context: Context, settings: AudioWidgetSettings): Boolean =
        when (settings.textColour) {
            WidgetTextColour.LIGHT -> true
            WidgetTextColour.DARK -> false
            WidgetTextColour.AUTO ->
                if (settings.backgroundOpacity >= OPAQUE_BACKGROUND_THRESHOLD) isNight(context)
                else !wallpaperPrefersDarkText(context)
        }

    fun textColor(lightText: Boolean): Int = if (lightText) Color.WHITE else DARK_TEXT

    fun secondaryTextColor(lightText: Boolean): Int =
        if (lightText) SECONDARY_LIGHT_TEXT else SECONDARY_DARK_TEXT

    fun backgroundColor(lightText: Boolean, opacityPercent: Int): Int {
        val alpha = (opacityPercent.coerceIn(0, 100) * 255) / 100
        return if (lightText) Color.argb(alpha, 18, 18, 20) else Color.argb(alpha, 247, 247, 247)
    }

    private fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun wallpaperPrefersDarkText(context: Context): Boolean =
        try {
            val colors =
                WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        } catch (e: SecurityException) {
            Timber.w(e, "Widget: wallpaper colours unavailable")
            false
        }
}
