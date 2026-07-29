package com.makd.afinity.ui.player.components

import android.text.format.DateFormat
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.logoImageUrlWithTransparency
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.models.syncplay.SyncPlayMemberInfo
import com.makd.afinity.ui.components.AfinityBadge
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.livetv.components.LiveBadge
import com.makd.afinity.ui.player.PlayerViewModel
import com.makd.afinity.ui.player.toLocalizedLanguageName
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class AudioStreamOption(
    val stream: AfinityMediaStream,
    val displayName: String,
    val isDefault: Boolean,
    val position: Int = 0,
)

data class SubtitleStreamOption(
    val stream: AfinityMediaStream?,
    val displayName: String,
    val isDefault: Boolean,
    val index: Int,
    val isNone: Boolean = false,
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerControls(
    uiState: PlayerViewModel.PlayerUiState,
    player: Player,
    onPlayerEvent: (PlayerEvent) -> Unit,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    onPipToggle: () -> Unit = {},
    playlistQueue: List<AfinityItem> = emptyList(),
    currentPlaylistIndex: Int = -1,
    playlistContentStartIndex: Int = 0,
    onJumpToEpisode: (java.util.UUID) -> Unit = {},
    onVersionToggleRequest: () -> Unit = {},
    isSyncPlay: Boolean = false,
    onSyncPlayClick: () -> Unit = {},
    syncPlayMembers: List<String> = emptyList(),
    syncPlayGroupName: String = "",
    syncPlayMemberInfo: Map<String, SyncPlayMemberInfo> = emptyMap(),
) {
    var showTrackPanel by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEpisodeSwitcher by remember { mutableStateOf(false) }
    var showMembersPopup by remember { mutableStateOf(false) }

    val currentItem = uiState.currentItem

    val unknownLang = stringResource(R.string.track_unknown)

    val audioStreamOptions =
        remember(currentItem, uiState.currentMediaSourceId, unknownLang) {
            val currentSource =
                currentItem?.sources?.firstOrNull { it.id == uiState.currentMediaSourceId }
                    ?: currentItem?.sources?.firstOrNull()

            val streams =
                currentSource
                    ?.mediaStreams
                    ?.filter { it.type == MediaStreamType.AUDIO }
                    ?.mapIndexed { index, stream ->
                        val localizedLang =
                            if (stream.language.isNotEmpty() && stream.language != "und") {
                                stream.language.toLocalizedLanguageName()
                                    ?: stream.language.uppercase()
                            } else {
                                unknownLang
                            }
                        val channelStr = formatAudioChannels(stream.channels)
                        val displayName = buildString {
                            append(localizedLang)
                            if (stream.codec.isNotBlank()) append(" • ${stream.codec.uppercase()}")
                            if (channelStr != null) append(" $channelStr")
                        }
                        AudioStreamOption(
                            stream = stream,
                            displayName = displayName,
                            isDefault = stream.isDefault,
                            position = index,
                        )
                    } ?: emptyList()
            assertAudioOptions(streams)
        }

    val noneText = stringResource(R.string.track_none)
    val trackFmt = stringResource(R.string.track_number_fmt)

    val subtitleStreamOptions =
        remember(
            currentItem,
            uiState.currentMediaSourceId,
            player.currentTracks,
            noneText,
            trackFmt,
        ) {
            val currentSource =
                currentItem?.sources?.firstOrNull { it.id == uiState.currentMediaSourceId }
                    ?: currentItem?.sources?.firstOrNull()
            val serverSubtitleStreams =
                currentSource?.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE }
                    ?: emptyList()

            val options = mutableListOf<SubtitleStreamOption>()
            options.add(
                SubtitleStreamOption(
                    stream = null,
                    displayName = noneText,
                    isDefault = false,
                    index = -1,
                    isNone = true,
                )
            )
            player.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                .forEachIndexed { index, trackGroup ->
                    val serverStream = serverSubtitleStreams.getOrNull(index)
                    val format = trackGroup.mediaTrackGroup.getFormat(0)

                    val displayName =
                        if (serverStream != null) {
                            val langCode =
                                serverStream.language.ifEmpty { format.language.orEmpty() }
                            val localizedLang =
                                if (langCode.isNotEmpty() && langCode != "und") {
                                    langCode.toLocalizedLanguageName() ?: langCode.uppercase()
                                } else {
                                    String.format(trackFmt, index + 1)
                                }
                            buildString {
                                append(localizedLang)
                                if (serverStream.isForced) append(" [Forced]")
                                if (serverStream.isHearingImpaired) append(" [SDH]")
                                if (serverStream.isExternal) append(" [External]")
                            }
                        } else {
                            val langCode = format.language.orEmpty()
                            format.label?.takeIf { it.isNotBlank() }
                                ?: if (langCode.isNotEmpty() && langCode != "und") {
                                    langCode.toLocalizedLanguageName() ?: langCode.uppercase()
                                } else {
                                    String.format(trackFmt, index + 1)
                                }
                        }

                    options.add(
                        SubtitleStreamOption(
                            stream = serverStream,
                            displayName = displayName,
                            isDefault = trackGroup.isSelected,
                            index = index,
                            isNone = false,
                        )
                    )
                }
            assertSubtitleOptions(options)
        }

    val shouldShowControls = uiState.showControls && !uiState.isInPictureInPictureMode
    val logoStartPadding by
        animateDpAsState(
            targetValue = if (shouldShowControls) 60.dp else 0.dp,
            animationSpec = tween(300),
            label = "logoStartPadding",
        )

    Box(modifier = Modifier.fillMaxSize()) {
        val pauseDetailsEligible =
            uiState.pauseScreenEnabled &&
                !uiState.isPlaying &&
                !uiState.isBuffering &&
                !uiState.isControlsLocked &&
                !uiState.isInPictureInPictureMode &&
                uiState.currentItem != null &&
                !uiState.isLiveChannel &&
                !uiState.isPlayingIntro

        var showPauseDetails by remember { mutableStateOf(false) }
        LaunchedEffect(pauseDetailsEligible, uiState.pauseScreenDelaySeconds) {
            if (pauseDetailsEligible) {
                delay(uiState.pauseScreenDelaySeconds * 1000L)
                showPauseDetails = true
            } else {
                showPauseDetails = false
            }
        }

        AnimatedVisibility(
            visible = shouldShowControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.46f)))
        }

        AnimatedVisibility(
            visible = showPauseDetails,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180)),
        ) {
            PauseDetailsOverlay(uiState = uiState, onPlayerEvent = onPlayerEvent)
        }
        AnimatedVisibility(
            visible =
                !uiState.isInPictureInPictureMode &&
                    !showPauseDetails &&
                    (!uiState.logoAutoHide || uiState.showControls),
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(
                            WindowInsets.displayCutout.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                            )
                        )
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.padding(start = logoStartPadding),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        val displayItem =
                            if (uiState.isPlayingIntro) {
                                playlistQueue.getOrNull(currentPlaylistIndex + 1)
                                    ?: uiState.currentItem
                            } else {
                                uiState.currentItem
                            }

                        if (displayItem is AfinityMovie) {
                            if (displayItem.images.logo != null) {
                                AsyncImage(
                                    imageUrl =
                                        displayItem.images.logoImageUrlWithTransparency.toString(),
                                    contentDescription = "Logo",
                                    modifier = Modifier.height(60.dp).widthIn(max = 200.dp),
                                    contentScale = ContentScale.Fit,
                                    blurHash = null,
                                    targetWidth = 200.dp,
                                    targetHeight = 60.dp,
                                )
                            } else {
                                Text(
                                    text = displayItem.name,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (displayItem is AfinityEpisode) {
                            val seasonNumber = displayItem.parentIndexNumber
                            val episodeNumber = displayItem.indexNumber
                            val episodeEnd = displayItem.indexNumberEnd
                            val episodeTitle = displayItem.name
                            val seriesName = displayItem.seriesName

                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.wrapContentWidth(),
                            ) {
                                val seriesLogoUri =
                                    displayItem.seriesLogo ?: displayItem.images.showLogo
                                if (seriesLogoUri != null) {
                                    val logoUrl =
                                        seriesLogoUri.toString().let { url ->
                                            if (url.contains("?")) "$url&format=png"
                                            else "$url?format=png"
                                        }
                                    AsyncImage(
                                        imageUrl = logoUrl,
                                        contentDescription =
                                            stringResource(R.string.cd_series_logo),
                                        modifier = Modifier.height(60.dp).widthIn(max = 200.dp),
                                        contentScale = ContentScale.Fit,
                                        targetWidth = 200.dp,
                                        targetHeight = 60.dp,
                                    )
                                } else {
                                    Text(
                                        text = seriesName,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 18.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text =
                                        stringResource(
                                            R.string.player_episode_header_fmt,
                                            seasonNumber.toString().padStart(2, '0'),
                                            if (episodeEnd != null && episodeEnd != episodeNumber)
                                                "${episodeNumber.toString().padStart(2, '0')}-${episodeEnd.toString().padStart(2, '0')}"
                                            else episodeNumber.toString().padStart(2, '0'),
                                            episodeTitle,
                                        ),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = shouldShowControls,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                TopControls(
                    modifier = Modifier.align(Alignment.TopCenter),
                    uiState = uiState,
                    onPlayerEvent = onPlayerEvent,
                    onBackClick = onBackClick,
                    onLockToggle = { onPlayerEvent(PlayerEvent.ToggleLock) },
                    onPipToggle = onPipToggle,
                    isSyncPlay = isSyncPlay,
                    onSyncPlayClick = {
                        if (isSyncPlay) showMembersPopup = !showMembersPopup else onSyncPlayClick()
                    },
                )

                if (
                    !uiState.isControlsLocked &&
                        !uiState.isInPictureInPictureMode &&
                        !showPauseDetails
                ) {
                    CenterPlayButton(
                        uiState = uiState,
                        isPlaying = uiState.isPlaying,
                        showPlayButton = uiState.showPlayButton || uiState.isBuffering,
                        isBuffering = uiState.isBuffering,
                        hasQueueNeighbors = playlistQueue.size > 1,
                        onPlayPauseClick = {
                            if (uiState.isPlaying) onPlayerEvent(PlayerEvent.Pause)
                            else onPlayerEvent(PlayerEvent.Play)
                        },
                        onSeekBackward = { onPlayerEvent(PlayerEvent.SeekRelative(-10000L)) },
                        onSeekForward = { onPlayerEvent(PlayerEvent.SeekRelative(30000L)) },
                        onNextClick = onNextClick,
                        onPreviousClick = onPreviousClick,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    BottomControls(
                        uiState = uiState,
                        onPlayerEvent = onPlayerEvent,
                        onSpeedToggle = { showSpeedDialog = !showSpeedDialog },
                        onTrackPanelToggle = { showTrackPanel = !showTrackPanel },
                        onEpisodeSwitcherToggle = { showEpisodeSwitcher = !showEpisodeSwitcher },
                        showEpisodeSwitcherButton =
                            (playlistQueue.size - playlistContentStartIndex) > 1 &&
                                !uiState.isPlayingIntro,
                        onVersionToggle = onVersionToggleRequest,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible =
                uiState.isSeeking && !uiState.showControls && !uiState.isInPictureInPictureMode,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                                )
                            )
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                ) {
                    SeekBar(uiState = uiState, onPlayerEvent = onPlayerEvent)
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
        val currentSegment = uiState.currentSegment
        AnimatedVisibility(
            visible = uiState.showSkipButton && currentSegment != null,
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                    )
                    .padding(end = 16.dp, bottom = 110.dp),
            enter =
                fadeIn(tween(300)) +
                    scaleIn(
                        initialScale = 0.8f,
                        animationSpec = tween(300),
                        transformOrigin = TransformOrigin(1f, 1f),
                    ),
            exit =
                fadeOut(tween(300)) +
                    scaleOut(
                        targetScale = 0.8f,
                        animationSpec = tween(300),
                        transformOrigin = TransformOrigin(1f, 1f),
                    ),
        ) {
            if (currentSegment != null) {
                val nextItem = playlistQueue.getOrNull(currentPlaylistIndex + 1)

                NextUpSkipOverlay(
                    nextItem = nextItem,
                    segment = currentSegment,
                    skipButtonText = uiState.skipButtonText,
                    onSkipClick = { onPlayerEvent(PlayerEvent.SkipSegment(currentSegment)) },
                )
            }
        }

        if (showTrackPanel) {
            TrackPanel(
                audioOptions = audioStreamOptions,
                subtitleOptions = subtitleStreamOptions,
                currentAudioIndex =
                    uiState.audioStreamIndex
                        ?: audioStreamOptions.find { it.isDefault }?.position
                        ?: 0,
                currentSubtitleIndex = uiState.subtitleStreamIndex ?: -1,
                onSelectTrack = { trackType, index ->
                    onPlayerEvent(PlayerEvent.SwitchToTrack(trackType, index))
                },
                onDismiss = { showTrackPanel = false },
            )
        }

        if (showSpeedDialog) {
            Box(
                modifier =
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        showSpeedDialog = false
                    },
                contentAlignment = Alignment.Center,
            ) {
                PlaybackSpeedPanel(
                    currentSpeed = uiState.playbackSpeed,
                    onSpeedChange = { speed -> onPlayerEvent(PlayerEvent.SetPlaybackSpeed(speed)) },
                    modifier =
                        Modifier.windowInsetsPadding(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                            )
                            .widthIn(min = 340.dp, max = 560.dp),
                )
            }
        }

        if (showEpisodeSwitcher && playlistQueue.isNotEmpty()) {
            val switcherQueue = playlistQueue.drop(playlistContentStartIndex)
            val switcherIndex = (currentPlaylistIndex - playlistContentStartIndex).coerceAtLeast(0)
            EpisodeSwitcher(
                episodes = switcherQueue,
                currentIndex = switcherIndex,
                isPlaying = uiState.isPlaying,
                onEpisodeClick = { episodeId ->
                    onJumpToEpisode(episodeId)
                    showEpisodeSwitcher = false
                },
                onDismiss = { showEpisodeSwitcher = false },
            )
        }

        if (showMembersPopup && isSyncPlay) {
            Box(
                modifier =
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        showMembersPopup = false
                    }
            ) {
                Box(
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .windowInsetsPadding(
                                WindowInsets.displayCutout.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                                )
                            )
                            .padding(top = 72.dp, end = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                /* consume clicks */
                            }
                ) {
                    Column(
                        modifier =
                            Modifier.background(
                                    color = Color(0xEB000000),
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .padding(16.dp)
                                .widthIn(min = 220.dp, max = 280.dp)
                                .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_users_group),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = syncPlayGroupName.ifBlank { "Watch Party" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            AfinityBadge(
                                text = stringResource(R.string.syncplay_synced_badge),
                                containerColor =
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            syncPlayMembers.forEach { member ->
                                val memberInfo = syncPlayMemberInfo[member]

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(modifier = Modifier.size(32.dp)) {
                                        if (memberInfo?.profileImageUrl != null) {
                                            AsyncImage(
                                                imageUrl = memberInfo.profileImageUrl,
                                                contentDescription = "Profile picture of $member",
                                                modifier =
                                                    Modifier.fillMaxSize()
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.1f)),
                                                contentScale = ContentScale.Crop,
                                                blurHash = null,
                                                targetWidth = 32.dp,
                                                targetHeight = 32.dp,
                                            )
                                        } else {
                                            Box(
                                                modifier =
                                                    Modifier.fillMaxSize()
                                                        .clip(CircleShape)
                                                        .background(
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.2f
                                                            )
                                                        ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = member.take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }

                                        Box(
                                            modifier =
                                                Modifier.size(10.dp)
                                                    .align(Alignment.BottomEnd)
                                                    .offset(x = 2.dp, y = 2.dp)
                                                    .background(Color(0xFF4CAF50), CircleShape)
                                                    .border(2.dp, Color(0xEB000000), CircleShape)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = member,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White.copy(alpha = 0.95f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )

                                        if (memberInfo != null) {
                                            val deviceDetails =
                                                listOfNotNull(
                                                        memberInfo.deviceName?.takeIf {
                                                            it.isNotBlank()
                                                        },
                                                        memberInfo.clientName?.takeIf {
                                                            it.isNotBlank()
                                                        },
                                                    )
                                                    .joinToString(" • ")

                                            if (deviceDetails.isNotEmpty()) {
                                                Text(
                                                    text = deviceDetails,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        TextButton(
                            onClick = {
                                showMembersPopup = false
                                onSyncPlayClick()
                            },
                            modifier = Modifier.align(Alignment.End).height(32.dp),
                            contentPadding =
                                androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                text = "Manage group",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TopControls(
    modifier: Modifier = Modifier,
    uiState: PlayerViewModel.PlayerUiState,
    onPlayerEvent: (PlayerEvent) -> Unit,
    onBackClick: () -> Unit,
    onLockToggle: () -> Unit,
    onPipToggle: () -> Unit = {},
    isSyncPlay: Boolean = false,
    onSyncPlayClick: () -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                    )
                )
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onLockToggle, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter =
                            painterResource(
                                id =
                                    if (uiState.isControlsLocked) R.drawable.ic_unlock_player
                                    else R.drawable.ic_lock_player
                            ),
                        contentDescription =
                            if (uiState.isControlsLocked) stringResource(R.string.cd_unlock)
                            else stringResource(R.string.cd_lock),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                if (!uiState.isControlsLocked && !uiState.isInPictureInPictureMode) {
                    IconButton(
                        onClick = onSyncPlayClick,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_users_group),
                            contentDescription = "Watch party",
                            tint =
                                if (isSyncPlay) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(
                        onClick = { onPlayerEvent(PlayerEvent.RequestCastDeviceSelection) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    id =
                                        if (uiState.isCasting) R.drawable.ic_cast_connected
                                        else R.drawable.ic_cast
                                ),
                            contentDescription = stringResource(R.string.cd_cast),
                            tint =
                                if (uiState.isCasting) MaterialTheme.colorScheme.primary
                                else Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(onClick = onPipToggle, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pip),
                            contentDescription = stringResource(R.string.cd_enter_pip),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(
                        onClick = { onPlayerEvent(PlayerEvent.CycleVideoZoomMode) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = uiState.videoZoomMode.getIconPainter(),
                            contentDescription = stringResource(R.string.cd_aspect_ratio),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun CenterPlayButton(
    modifier: Modifier = Modifier,
    uiState: PlayerViewModel.PlayerUiState,
    isPlaying: Boolean,
    showPlayButton: Boolean,
    isBuffering: Boolean,
    hasQueueNeighbors: Boolean = false,
    onPlayPauseClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
) {
    val hasChapters = uiState.chapters.isNotEmpty()
    val isEpisode = uiState.currentItem is AfinityEpisode
    val showSkipButtons = (isEpisode || hasChapters || hasQueueNeighbors) && !uiState.isPlayingIntro

    AnimatedVisibility(
        visible = showPlayButton,
        enter = scaleIn(animationSpec = tween(200)) + fadeIn(),
        exit = scaleOut(animationSpec = tween(200)) + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSkipButtons) {
                IconButton(onClick = onPreviousClick, modifier = Modifier.size(60.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_skip_back),
                        contentDescription = stringResource(R.string.cd_previous),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            if (!uiState.isPlayingIntro) {
                IconButton(onClick = onSeekBackward, modifier = Modifier.size(60.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rewind_backward_10),
                        contentDescription = stringResource(R.string.cd_rewind_10),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(60.dp)) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.White,
                        strokeWidth = 4.dp,
                    )
                } else {
                    Icon(
                        painter =
                            if (isPlaying) painterResource(id = R.drawable.ic_player_pause_filled)
                            else painterResource(id = R.drawable.ic_player_play_filled),
                        contentDescription =
                            if (isPlaying) stringResource(R.string.cd_pause)
                            else stringResource(R.string.cd_play),
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            if (!uiState.isPlayingIntro) {
                IconButton(onClick = onSeekForward, modifier = Modifier.size(60.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rewind_forward_30),
                        contentDescription = stringResource(R.string.cd_forward_30),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            if (showSkipButtons) {
                IconButton(onClick = onNextClick, modifier = Modifier.size(60.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_skip_forward),
                        contentDescription = stringResource(R.string.cd_next),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun BottomControls(
    modifier: Modifier = Modifier,
    uiState: PlayerViewModel.PlayerUiState,
    onPlayerEvent: (PlayerEvent) -> Unit,
    onSpeedToggle: () -> Unit,
    onTrackPanelToggle: () -> Unit,
    onEpisodeSwitcherToggle: () -> Unit = {},
    showEpisodeSwitcherButton: Boolean = false,
    onVersionToggle: () -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
    ) {
        Column {
            if (!uiState.isPlayingIntro) {
                SeekBar(uiState = uiState, onPlayerEvent = onPlayerEvent)
            } else {
                Text(
                    text = stringResource(R.string.playing_intro_text),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
            }
            if (!uiState.isPlayingIntro) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tracksActive =
                            uiState.audioStreamIndex != null || uiState.subtitleStreamIndex != null
                        LabeledControl(
                            painter = painterResource(id = R.drawable.ic_subtitles),
                            label = stringResource(R.string.player_tracks_label),
                            tint =
                                if (tracksActive) MaterialTheme.colorScheme.primary
                                else Color.White,
                            showLabel = false,
                            onClick = onTrackPanelToggle,
                        )
                        LabeledControl(
                            painter = painterResource(id = R.drawable.ic_speed),
                            label = stringResource(R.string.cd_speed),
                            showLabel = false,
                            onClick = onSpeedToggle,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (uiState.availableSources.size > 1) {
                            LabeledControl(
                                painter = painterResource(id = R.drawable.ic_versions),
                                label = stringResource(R.string.cd_version_selector),
                                showLabel = false,
                                onClick = onVersionToggle,
                            )
                        }
                        if (showEpisodeSwitcherButton) {
                            LabeledControl(
                                painter = painterResource(id = R.drawable.ic_episodes_list),
                                label = stringResource(R.string.cd_episodes),
                                showLabel = false,
                                onClick = onEpisodeSwitcherToggle,
                            )
                        }
                        LabeledControl(
                            painter = painterResource(id = R.drawable.ic_screen_rotation),
                            label = stringResource(R.string.cd_screen_rotation),
                            showLabel = false,
                            onClick = { onPlayerEvent(PlayerEvent.CycleScreenRotation) },
                        )
                        LabeledControl(
                            painter = painterResource(id = R.drawable.ic_info),
                            label = stringResource(R.string.cd_playback_info),
                            showLabel = false,
                            onClick = { onPlayerEvent(PlayerEvent.TogglePlaybackStats) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledControl(
    painter: Painter,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            painter = painter,
            contentDescription = if (showLabel) null else label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        if (showLabel) {
            Text(
                text = label,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SeekBar(
    uiState: PlayerViewModel.PlayerUiState,
    onPlayerEvent: (PlayerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggedPosition by remember { mutableStateOf<Float?>(null) }
    val duration = uiState.duration
    val position = if (uiState.isSeeking) uiState.seekPosition else uiState.currentPosition

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.isLiveChannel) {
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(6.dp)
                        .padding(start = 8.dp)
                        .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
            )
        } else {
            Slider(
                value = draggedPosition ?: position.toFloat(),
                onValueChange = { newPosition ->
                    if (!uiState.isSeeking) {
                        onPlayerEvent(PlayerEvent.OnSeekBarDragStart)
                    }
                    draggedPosition = newPosition
                    onPlayerEvent(PlayerEvent.OnSeekBarValueChange(newPosition.toLong()))
                },
                onValueChangeFinished = {
                    onPlayerEvent(
                        PlayerEvent.OnSeekBarDragFinished((draggedPosition ?: position).toLong())
                    )
                    draggedPosition = null
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(0f),
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                track = { sliderState ->
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
                    Box(
                        modifier = Modifier.fillMaxWidth().height(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(6.dp),
                            thumbTrackGapSize = 6.dp,
                            colors =
                                SliderDefaults.colors(
                                    activeTrackColor = primaryColor,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
                            drawStopIndicator = null,
                        )
                        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                            if (duration > 0) {
                                val rangeSpan =
                                    (sliderState.valueRange.endInclusive -
                                            sliderState.valueRange.start)
                                        .coerceAtLeast(1f)
                                val progress =
                                    (sliderState.value - sliderState.valueRange.start) / rangeSpan
                                val thumbTrackGapPx = 6.dp.toPx()
                                val thumbCenterX = progress * size.width
                                val bufferStartX =
                                    (thumbCenterX + thumbTrackGapPx).coerceAtMost(size.width)
                                val bufferedFraction =
                                    (uiState.bufferedPosition.toFloat() / duration.toFloat())
                                        .coerceIn(0f, 1f)
                                val bufferedEndX =
                                    (bufferedFraction * size.width).coerceAtMost(size.width)
                                val h = size.height
                                val cornerR = CornerRadius(h / 2f, h / 2f)
                                if (bufferedEndX > bufferStartX) {
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.55f),
                                        topLeft = Offset(bufferStartX, 0f),
                                        size = Size(bufferedEndX - bufferStartX, h),
                                        cornerRadius = cornerR,
                                    )
                                }
                                val markerRadius = 2.dp.toPx()
                                uiState.chapters.forEach { chapter ->
                                    val x =
                                        (chapter.startPosition.toFloat() / duration.toFloat()) *
                                            size.width
                                    val cx =
                                        x.coerceIn(markerRadius, size.width - markerRadius)
                                    val markerColor =
                                        if (cx < thumbCenterX) onPrimaryColor.copy(alpha = 0.85f)
                                        else Color.White.copy(alpha = 0.8f)
                                    drawCircle(
                                        color = markerColor,
                                        radius = markerRadius,
                                        center = Offset(cx, h / 2),
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(18.dp).padding(start = 8.dp),
            )
        }

        if (uiState.isLiveChannel) {
            LiveBadge()
        } else {
            Box(
                modifier =
                    Modifier.width(50.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onPlayerEvent(PlayerEvent.ToggleRemainingTime)
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text =
                        if (uiState.showRemainingTime) {
                            "-${formatTime((duration - position).coerceAtLeast(0L))}"
                        } else {
                            formatTime(position)
                        },
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TrackPanel(
    audioOptions: List<AudioStreamOption>,
    subtitleOptions: List<SubtitleStreamOption>,
    currentAudioIndex: Int,
    currentSubtitleIndex: Int,
    onSelectTrack: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier =
                Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .widthIn(min = 340.dp, max = 600.dp)
                    .heightIn(max = 400.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.player_tracks_label),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TrackColumn(
                        title = stringResource(R.string.player_audio_title),
                        modifier = Modifier.weight(1f),
                    ) {
                        audioOptions.forEach { option ->
                            TrackRow(
                                label = option.displayName,
                                selected = currentAudioIndex == option.position,
                                onClick = {
                                    onSelectTrack(C.TRACK_TYPE_AUDIO, option.position)
                                    onDismiss()
                                },
                            )
                        }
                    }
                    TrackColumn(
                        title = stringResource(R.string.player_subtitle_title),
                        modifier = Modifier.weight(1f),
                    ) {
                        subtitleOptions.forEach { option ->
                            TrackRow(
                                label = option.displayName,
                                selected = currentSubtitleIndex == option.index,
                                onClick = {
                                    onSelectTrack(C.TRACK_TYPE_TEXT, option.index)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackColumn(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else Color.Transparent
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PauseDetailsOverlay(
    uiState: PlayerViewModel.PlayerUiState,
    onPlayerEvent: (PlayerEvent) -> Unit,
) {
    val item = uiState.currentItem ?: return
    val context = LocalContext.current

    val source =
        item.sources.firstOrNull { it.id == uiState.currentMediaSourceId }
            ?: item.sources.firstOrNull()
    val videoStream = source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audioStream =
        source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO && it.isDefault }
            ?: source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }

    val resolution = videoStream?.let { vs ->
        val w = vs.width ?: 0
        val h = vs.height ?: 0
        when {
            w >= 3800 || h >= 2000 -> "4K"
            w >= 1900 || h >= 1000 -> "1080p"
            w >= 1200 || h >= 700 -> "720p"
            w > 0 || h > 0 -> "SD"
            else -> null
        }
    }
    val hdr =
        when {
            videoStream?.videoDoViTitle != null -> "Dolby Vision"
            else ->
                videoStream?.videoRangeType?.name?.uppercase()?.let { n ->
                    when {
                        n.contains("HDR10") && n.contains("PLUS") -> "HDR10+"
                        n.contains("HDR10") -> "HDR10"
                        n.contains("DOVI") || n.contains("DOLBY") -> "Dolby Vision"
                        n.contains("HLG") -> "HLG"
                        else -> null
                    }
                }
        }
    val audioLabel = formatAudioChannels(audioStream?.channels)

    val genres =
        when (item) {
            is AfinityMovie -> item.genres
            is AfinityShow -> item.genres
            else -> emptyList()
        }
    val year =
        when (item) {
            is AfinityMovie -> item.productionYear
            is AfinityShow -> item.productionYear
            else -> null
        }
    val officialRating =
        when (item) {
            is AfinityMovie -> item.officialRating
            is AfinityShow -> item.officialRating
            else -> null
        }
    val runtimeText = formatRuntime(item.runtimeTicks)
    val overview = item.overview.takeIf { it.isNotBlank() }
    val people =
        when (item) {
            is AfinityMovie -> item.people
            is AfinityEpisode -> item.people
            is AfinityShow -> item.people
            else -> emptyList()
        }
    val cast = people.filter { it.type == PersonKind.ACTOR }.take(3)
    val directors = people.filter { it.type == PersonKind.DIRECTOR }.map { it.name }

    val ratings = uiState.externalRatings
    val speed = uiState.playbackSpeed.coerceAtLeast(0.1f)
    val remainingMs =
        ((uiState.duration - uiState.currentPosition).coerceAtLeast(0L) / speed).toLong()
    val endsAt =
        remember(remainingMs / 60000L) {
            DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + remainingMs))
        }

    val logoUrl =
        when (item) {
            is AfinityEpisode ->
                (item.seriesLogo ?: item.images.showLogo)?.toString()?.let { url ->
                    if (url.contains("?")) "$url&format=png" else "$url?format=png"
                }
            is AfinityMovie ->
                if (item.images.logo != null) item.images.logoImageUrlWithTransparency.toString()
                else null
            is AfinityShow ->
                if (item.images.logo != null) item.images.logoImageUrlWithTransparency.toString()
                else null
            else -> null
        }
    val primaryTitle = if (item is AfinityEpisode) item.seriesName else item.name
    val episodeSubtitle =
        (item as? AfinityEpisode)?.let { ep ->
            buildString {
                val s = ep.parentIndexNumber
                val e = ep.indexNumber
                if (s != null && e != null) append("S$s:E$e")
                if (ep.name.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(ep.name)
                }
            }
        }
    val communityRating =
        when (item) {
            is AfinityMovie -> item.communityRating
            is AfinityEpisode -> item.communityRating
            is AfinityShow -> item.communityRating
            else -> null
        }
    val criticRating = (item as? AfinityMovie)?.criticRating

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                    )
                    .padding(start = 32.dp, end = 32.dp, top = 56.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (logoUrl != null) {
                        AsyncImage(
                            imageUrl = logoUrl,
                            contentDescription = primaryTitle,
                            modifier = Modifier.heightIn(max = 52.dp).widthIn(max = 300.dp),
                            contentScale = ContentScale.Fit,
                            blurHash = null,
                            targetWidth = 300.dp,
                            targetHeight = 52.dp,
                        )
                    } else {
                        Text(
                            text = primaryTitle,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!episodeSubtitle.isNullOrBlank()) {
                        Text(
                            text = episodeSubtitle,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    val metaColor = Color.White.copy(alpha = 0.8f)
                    val metadataItems = mutableListOf<@Composable () -> Unit>()
                    genres
                        .take(2)
                        .joinToString(" · ")
                        .takeIf { it.isNotBlank() && item !is AfinityEpisode }
                        ?.let { g -> metadataItems.add { PauseMetaText(g, metaColor) } }
                    year?.let { metadataItems.add { PauseMetaText(it.toString(), metaColor) } }
                    officialRating
                        ?.takeIf { it.isNotBlank() }
                        ?.let { metadataItems.add { PauseMetaText(it, metaColor) } }
                    runtimeText?.let { metadataItems.add { PauseMetaText(it, metaColor) } }
                    resolution?.let { r -> metadataItems.add { PauseMetaText(r, metaColor) } }
                    hdr?.let { h -> metadataItems.add { PauseMetaText(h, metaColor) } }
                    audioLabel?.let { a -> metadataItems.add { PauseMetaText(a, metaColor) } }
                    communityRating?.let { r ->
                        metadataItems.add {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_imdb_logo),
                                    contentDescription = stringResource(R.string.cd_imdb),
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(18.dp),
                                )
                                PauseMetaText(String.format(Locale.US, "%.1f", r), metaColor)
                            }
                        }
                    }
                    criticRating?.let { rt ->
                        metadataItems.add {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            id =
                                                if (rt > 60) R.drawable.ic_rotten_tomato_fresh
                                                else R.drawable.ic_rotten_tomato_rotten
                                        ),
                                    contentDescription =
                                        stringResource(R.string.cd_rotten_tomatoes),
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(13.dp),
                                )
                                PauseMetaText("${rt.toInt()}%", metaColor)
                            }
                        }
                    }
                    ratings.tmdb?.let { t ->
                        metadataItems.add {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                PauseMetaText("TMDB", Color.White.copy(alpha = 0.5f))
                                PauseMetaText(t, metaColor)
                            }
                        }
                    }
                    if (metadataItems.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            metadataItems.forEachIndexed { index, mItem ->
                                mItem()
                                if (index < metadataItems.size - 1) {
                                    PauseMetaText("•", Color.White.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                    if (directors.isNotEmpty()) {
                        Text(
                            text =
                                stringResource(
                                    R.string.home_person_directed_by,
                                    directors.joinToString(", "),
                                ),
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    overview?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 14.dp).widthIn(max = 540.dp),
                        )
                    }
                }

                if (cast.isNotEmpty()) {
                    Column(
                        modifier = Modifier.widthIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.player_cast_heading).uppercase(),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                        )
                        cast.forEach { person -> CastRow(person) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onPlayerEvent(PlayerEvent.Play) },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_play_filled),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_resume),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    onClick = {
                        onPlayerEvent(PlayerEvent.Seek(0))
                        onPlayerEvent(PlayerEvent.Play)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_skip_back),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.player_restart))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text =
                        stringResource(R.string.player_time_left, formatTime(remainingMs)) +
                            "  ·  " +
                            stringResource(R.string.player_ends_at, endsAt),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PauseMetaText(
    text: String,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(text = text, color = color, fontSize = 13.sp, fontWeight = fontWeight, maxLines = 1)
}

@Composable
private fun CastRow(person: AfinityPerson) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier.size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val uri = person.image.uri?.toString()
            if (uri != null) {
                AsyncImage(
                    imageUrl = uri,
                    contentDescription = person.name,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    blurHash = person.image.blurHash,
                    targetWidth = 54.dp,
                    targetHeight = 54.dp,
                )
            } else {
                Text(
                    text = person.name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
        Column {
            Text(
                text = person.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (person.role.isNotBlank()) {
                Text(
                    text = person.role,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatRuntime(ticks: Long): String? {
    if (ticks <= 0L) return null
    val totalMinutes = (ticks / 10_000_000L / 60L).toInt()
    if (totalMinutes <= 0) return null
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = abs(timeMs / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

private fun formatAudioChannels(channels: Int?): String? =
    when (channels) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> channels?.let { "${it}ch" }
    }

private val parentheticalRegex = Regex("""\(([^)]+)\)""")

private fun extractRegionalHint(displayTitle: String?): String? = displayTitle?.let {
    parentheticalRegex.find(it)?.groupValues?.get(1)?.trim()
}

private fun assertAudioOptions(options: List<AudioStreamOption>): List<AudioStreamOption> {
    val duplicates = options.groupBy { it.displayName }.filter { it.value.size > 1 }.keys
    return options.map { opt ->
        if (opt.displayName !in duplicates) return@map opt
        val hint = extractRegionalHint(opt.stream.displayTitle) ?: return@map opt
        opt.copy(displayName = "${opt.displayName} ($hint)")
    }
}

private fun assertSubtitleOptions(options: List<SubtitleStreamOption>): List<SubtitleStreamOption> {
    val duplicates =
        options.filter { !it.isNone }.groupBy { it.displayName }.filter { it.value.size > 1 }.keys
    return options.map { opt ->
        if (opt.isNone || opt.displayName !in duplicates) return@map opt
        val hint = extractRegionalHint(opt.stream?.displayTitle) ?: return@map opt
        opt.copy(displayName = "${opt.displayName} ($hint)")
    }
}
