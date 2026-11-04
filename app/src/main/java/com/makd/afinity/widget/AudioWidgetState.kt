package com.makd.afinity.widget

enum class AudioWidgetStatus {
    PLAYING,
    PAUSED,
    STOPPED,
}

enum class AudioWidgetEngine {
    MUSIC,
    ABS,
}

data class AudioWidgetState(
    val status: AudioWidgetStatus,
    val engine: AudioWidgetEngine,
    val title: String?,
    val subtitle: String?,
    val isSignedIn: Boolean,
    val isLoaded: Boolean,
    val absItemId: String? = null,
    val absEpisodeId: String? = null,
    val coverUrl: String? = null,
    val continueItems: List<ContinueItem> = emptyList(),
    val isCasting: Boolean = false,
)

data class ContinueItem(
    val engine: AudioWidgetEngine,
    val title: String,
    val subtitle: String?,
    val absItemId: String? = null,
    val absEpisodeId: String? = null,
)
