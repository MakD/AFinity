package com.makd.afinity.data.models.player

import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.TranscodeReason

sealed interface StreamDecision {

    val url: String
    val playMethod: PlayMethod

    val protocol: MediaStreamProtocol
        get() = MediaStreamProtocol.HTTP

    val transcodeReasons: List<TranscodeReason>
        get() = emptyList()

    val burnedInSubtitleIndex: Int?
        get() = null

    val isServerSideSubtitle: Boolean
        get() = burnedInSubtitleIndex != null

    data class DirectPlay(override val url: String) : StreamDecision {
        override val playMethod = PlayMethod.DIRECT_PLAY
    }

    data class DirectStream(
        override val url: String,
        override val protocol: MediaStreamProtocol,
        override val transcodeReasons: List<TranscodeReason>,
    ) : StreamDecision {
        override val playMethod = PlayMethod.DIRECT_STREAM
    }

    data class Transcode(
        override val url: String,
        override val protocol: MediaStreamProtocol,
        override val transcodeReasons: List<TranscodeReason>,
        override val burnedInSubtitleIndex: Int?,
    ) : StreamDecision {
        override val playMethod = PlayMethod.TRANSCODE
    }
}
