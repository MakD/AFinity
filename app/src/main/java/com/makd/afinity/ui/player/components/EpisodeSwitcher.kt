package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.ui.components.AsyncImage
import java.util.Locale
import java.util.UUID

@Composable
fun EpisodeSwitcher(
    episodes: List<AfinityItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    onEpisodeClick: (UUID) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayEpisodes = episodes

    val activeEpisodeIndex = currentIndex.coerceAtLeast(0)

    val partInfoMap =
        remember(displayEpisodes) {
            buildMap<Int, Pair<AfinityItem, Int>> {
                var i = 0
                while (i < displayEpisodes.size) {
                    val current = displayEpisodes[i]
                    val isNamed = current is AfinityEpisode || current is AfinityMovie
                    if (isNamed) {
                        var j = i + 1
                        while (
                            j < displayEpisodes.size &&
                                displayEpisodes[j] !is AfinityEpisode &&
                                displayEpisodes[j] !is AfinityMovie
                        ) j++
                        if (j > i + 1) {
                            for (k in i + 1 until j) {
                                put(k, Pair(current, k - i + 1))
                            }
                        }
                        i = j
                    } else {
                        i++
                    }
                }
            }
        }

    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = (activeEpisodeIndex - 1).coerceAtLeast(0)
        )

    LaunchedEffect(activeEpisodeIndex) {
        if (activeEpisodeIndex >= 0) {
            listState.animateScrollToItem((activeEpisodeIndex - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier =
            modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally { it } + fadeIn(),
            modifier = Modifier.fillMaxHeight(),
        ) {
            Surface(
                modifier =
                    Modifier.fillMaxHeight()
                        .widthIn(min = 380.dp, max = 450.dp)
                        .padding(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            /* Consume clicks */
                        },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(top = 24.dp, start = 24.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.player_up_next),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            val currentSeason =
                                (displayEpisodes.getOrNull(activeEpisodeIndex) as? AfinityEpisode)
                                    ?.parentIndexNumber
                            if (currentSeason != null) {
                                Text(
                                    text =
                                        stringResource(R.string.player_season_fmt, currentSeason),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier =
                                Modifier.background(
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                            alpha = 0.5f
                                        ),
                                        CircleShape,
                                    )
                                    .size(32.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = stringResource(R.string.cd_close),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                    LazyColumn(
                        state = listState,
                        contentPadding =
                            PaddingValues(top = 16.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(items = displayEpisodes, key = { _, item -> item.id }) {
                            index,
                            item ->
                            EpisodeSwitcherCard(
                                episode = item,
                                parentItem = partInfoMap[index]?.first,
                                partNumber = partInfoMap[index]?.second,
                                isCurrentlyPlaying = index == activeEpisodeIndex,
                                isPlaying = isPlaying,
                                onClick = { onEpisodeClick(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeSwitcherCard(
    episode: AfinityItem,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    parentItem: AfinityItem? = null,
    partNumber: Int? = null,
) {
    val displayItem: AfinityItem = parentItem ?: episode
    val backgroundColor =
        if (isCurrentlyPlaying) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            Color.Transparent
        }

    val borderColor =
        if (isCurrentlyPlaying) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        } else {
            Color.Transparent
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.width(140.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            val cardImageUrl =
                if (episode is AfinityMovie) {
                    episode.images?.thumbImageUrl ?: episode.images?.primaryImageUrl
                } else {
                    episode.images?.primaryImageUrl
                }
            val cardBlurHash =
                if (episode is AfinityMovie) {
                    episode.images?.thumbBlurHash ?: episode.images?.primaryBlurHash
                } else {
                    episode.images?.primaryBlurHash
                }
            AsyncImage(
                imageUrl = cardImageUrl.toString(),
                contentDescription = displayItem.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                blurHash = cardBlurHash,
            )

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                startY = 50f,
                            )
                        )
            )

            if (episode.played) {
                Box(
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = stringResource(R.string.cd_watched_status),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            if (episode.playbackPositionTicks > 0 && episode.runtimeTicks > 0) {
                val progress =
                    episode.playbackPositionTicks.toFloat() / episode.runtimeTicks.toFloat()
                if (progress > 0f && progress < 0.95f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier =
                            Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                }
            }

            if (episode.runtimeTicks > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                ) {
                    val minutesFmt = stringResource(R.string.time_minutes_short)
                    val secondsFmt = stringResource(R.string.time_seconds_short)
                    val runtimeText =
                        remember(episode.runtimeTicks) {
                            val totalSeconds = episode.runtimeTicks / 10000000
                            val minutes = totalSeconds / 60
                            val seconds = totalSeconds % 60
                            if (minutes > 0) String.format(minutesFmt, minutes)
                            else String.format(secondsFmt, seconds)
                        }

                    Text(
                        text = runtimeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            if (isCurrentlyPlaying) {
                val composition by
                    rememberLottieComposition(LottieCompositionSpec.Asset("anim_equalizer.lottie"))

                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val dynamicProperties =
                        rememberLottieDynamicProperties(
                            rememberLottieDynamicProperty(
                                property = LottieProperty.COLOR,
                                value = MaterialTheme.colorScheme.primary.toArgb(),
                                keyPath = arrayOf("**"),
                            ),
                            rememberLottieDynamicProperty(
                                property = LottieProperty.STROKE_COLOR,
                                value = MaterialTheme.colorScheme.primary.toArgb(),
                                keyPath = arrayOf("**"),
                            ),
                        )

                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        isPlaying = isPlaying,
                        dynamicProperties = dynamicProperties,
                        modifier = Modifier.size(120.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            val episodeMeta = (parentItem as? AfinityEpisode) ?: (episode as? AfinityEpisode)
            val rating =
                episodeMeta?.communityRating
                    ?: (parentItem as? AfinityMovie)?.communityRating
                    ?: (episode as? AfinityMovie)?.communityRating
            val totalParts =
                episodeMeta?.partCount
                    ?: (parentItem as? AfinityMovie)?.partCount
                    ?: (episode as? AfinityMovie)?.partCount

            val showRatings = LocalShowRatings.current
            val hasTopRow =
                episodeMeta != null || (showRatings && rating != null) || partNumber != null
            if (hasTopRow) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (episodeMeta != null) {
                        Text(
                            text =
                                stringResource(
                                    R.string.player_season_episode_fmt,
                                    episodeMeta.parentIndexNumber ?: 1,
                                    if (
                                        episodeMeta.indexNumberEnd != null &&
                                            episodeMeta.indexNumberEnd != episodeMeta.indexNumber
                                    )
                                        "${episodeMeta.indexNumber ?: 0}-${episodeMeta.indexNumberEnd}"
                                    else "${episodeMeta.indexNumber ?: 0}",
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }

                    if (showRatings && rating != null && rating > 0) {
                        if (episodeMeta != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_imdb_logo),
                                contentDescription = stringResource(R.string.cd_imdb),
                                tint = Color.Unspecified,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    if (partNumber != null) {
                        if (episodeMeta != null || (showRatings && rating != null && rating > 0)) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        Text(
                            text =
                                stringResource(
                                    R.string.meta_part_of_fmt,
                                    partNumber,
                                    totalParts ?: partNumber,
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = displayItem.name,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp,
                fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.SemiBold,
                lineHeight = 20.sp,
            )

            val overviewText = displayItem.overview
            if (overviewText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = overviewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
