package com.makd.afinity.player.mpv

import android.os.Parcelable
import androidx.media3.common.C
import kotlinx.parcelize.Parcelize

@Parcelize
enum class MPVTrackType(val type: String, val propertyName: String) : Parcelable {
    VIDEO("video", "vid"),
    AUDIO("audio", "aid"),
    SUBTITLE("sub", "sid");

    companion object {
        fun fromMedia3TrackType(trackType: Int): MPVTrackType {
            return when (trackType) {
                C.TRACK_TYPE_VIDEO -> VIDEO
                C.TRACK_TYPE_AUDIO -> AUDIO
                C.TRACK_TYPE_TEXT -> SUBTITLE
                else -> SUBTITLE
            }
        }
    }
}
