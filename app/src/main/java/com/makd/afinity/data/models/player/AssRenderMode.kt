package com.makd.afinity.data.models.player

import androidx.annotation.StringRes
import com.makd.afinity.R

enum class AssRenderMode(val value: String, @param:StringRes val labelRes: Int) {
    OFF("off", R.string.pref_ass_render_off),
    CUES("cues", R.string.pref_ass_render_cues),
    OVERLAY("overlay", R.string.pref_ass_render_overlay);

    companion object {
        val default = CUES

        fun fromValue(value: String): AssRenderMode {
            return entries.find { it.value == value } ?: default
        }
    }
}