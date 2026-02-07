package com.makd.afinity.data.models.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.core.graphics.toColorInt
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat

data class SubtitlePreferences(
    val textColor: Int = Color.WHITE,
    val textSize: Float = 1.0f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val outlineStyle: SubtitleOutlineStyle = SubtitleOutlineStyle.NONE,
    val outlineColor: Int = Color.BLACK,
    val outlineSize: Float = 0f,
    val backgroundColor: Int = Color.TRANSPARENT,
    val windowColor: Int = Color.TRANSPARENT,
    val verticalPosition: SubtitleVerticalPosition = SubtitleVerticalPosition.BOTTOM,
    val horizontalAlignment: SubtitleHorizontalAlignment = SubtitleHorizontalAlignment.CENTER,
) {
    companion object {
        val DEFAULT = SubtitlePreferences()

        fun colorToMpvHex(color: Int): String {
            val alpha = Color.alpha(color)
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            return String.format("#%02X%02X%02X%02X", alpha, red, green, blue)
        }

        fun hexToColor(hex: String): Int {
            return try {
                hex.toColorInt()
            } catch (e: IllegalArgumentException) {
                Color.WHITE
            }
        }
    }

    fun toMpvFontSize(): Int {
        return (55 * textSize).toInt()
    }

    fun toExoPlayerFractionalSize(): Float {
        return 0.0625f * textSize
    }
}

enum class SubtitleOutlineStyle(val displayName: String) {
    NONE("None"),
    OUTLINE("Outline"),
    DROP_SHADOW("Drop Shadow"),
    BACKGROUND_BOX("Background Box"),
    RAISED("Raised"),
    DEPRESSED("Depressed");

    companion object {
        fun fromString(value: String): SubtitleOutlineStyle {
            return entries.find { it.name == value } ?: NONE
        }
    }

    @OptIn(UnstableApi::class)
    fun toExoPlayerEdgeType(): Int {
        return when (this) {
            NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
            OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            BACKGROUND_BOX -> CaptionStyleCompat.EDGE_TYPE_NONE
            RAISED -> CaptionStyleCompat.EDGE_TYPE_RAISED
            DEPRESSED -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
        }
    }
}

enum class SubtitleVerticalPosition(val displayName: String, val mpvValue: String) {
    TOP("Top", "top"),
    CENTER("Center", "center"),
    BOTTOM("Bottom", "bottom");

    companion object {
        fun fromString(value: String): SubtitleVerticalPosition {
            return entries.find { it.name == value } ?: BOTTOM
        }
    }
}

enum class SubtitleHorizontalAlignment(val displayName: String, val mpvValue: String) {
    LEFT("Left", "left"),
    CENTER("Center", "center"),
    RIGHT("Right", "right");

    companion object {
        fun fromString(value: String): SubtitleHorizontalAlignment {
            return entries.find { it.name == value } ?: CENTER
        }
    }
}
