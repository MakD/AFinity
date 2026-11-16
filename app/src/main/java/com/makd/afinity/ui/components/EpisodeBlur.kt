package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.LocalBlurUnwatchedEpisodes

private val EpisodeBlurRadius = 12.dp

fun Modifier.episodeBlur(enabled: Boolean): Modifier =
    if (enabled) blur(EpisodeBlurRadius) else this

@Composable
@ReadOnlyComposable
fun shouldBlurEpisode(
    item: AfinityItem,
    imageUrl: String?,
    watched: Boolean = item.played,
    started: Boolean = item.playbackPositionTicks > 0L,
): Boolean =
    LocalBlurUnwatchedEpisodes.current &&
        item is AfinityEpisode &&
        !watched &&
        !started &&
        imageUrl != null &&
        (imageUrl == item.images.primaryImageUrl ||
            imageUrl == item.images.thumbImageUrl ||
            imageUrl == item.images.backdropImageUrl)
