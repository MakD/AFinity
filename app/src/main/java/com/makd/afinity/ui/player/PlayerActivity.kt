package com.makd.afinity.ui.player

import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Bundle
import android.os.Process
import android.util.Rational
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.navigation.LocalShowAwards
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.ui.theme.AFinityTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import timber.log.Timber

@UnstableApi
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    // Same activity-scoped instance PlayerScreen gets from hiltViewModel().
    private val syncPlayViewModel: SyncPlayViewModel by viewModels()

    @Inject lateinit var preferencesRepository: PreferencesRepository

    private var wasPip: Boolean = false

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    private var volumeStepBeforeMute = 1

    private var playerUiHasFocus = false

    private val arrowKeys =
        setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )

    private val repeatableVolumeKeys = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)

    private val repeatableSeekKeys =
        setOf(
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )

    companion object {
        private const val ACTION_PLAY_PAUSE = "com.makd.afinity.action.PLAY_PAUSE"
        private const val REQUEST_PLAY_PAUSE = 1001
    }

    private val pipReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Timber.d("PIP Receiver - Action received: ${intent?.action}")

                when (intent?.action) {
                    ACTION_PLAY_PAUSE -> {
                        Timber.d("PIP Control: Play/Pause clicked")
                        if (viewModel.player.isPlaying) {
                            viewModel.handlePlayerEvent(PlayerEvent.Pause)
                        } else {
                            viewModel.handlePlayerEvent(PlayerEvent.Play)
                        }
                        if (isInPictureInPictureMode) {
                            setPictureInPictureParams(buildPipParams())
                        }
                    }

                    else -> {
                        Timber.w("PIP Receiver - Unknown action: ${intent?.action}")
                    }
                }
            }
        }

    private val isPipSupported by lazy {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return@lazy false
        }

        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager?
        appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter().apply { addAction(ACTION_PLAY_PAUSE) }

        try {
            registerReceiver(pipReceiver, filter, RECEIVER_EXPORTED)
            Timber.d("PIP receiver registered successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register PIP receiver")
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        window.colorMode = ActivityInfo.COLOR_MODE_HDR

        val itemId =
            intent.getStringExtra("itemId")?.let { UUID.fromString(it) }
                ?: run {
                    Timber.e("PlayerActivity: No itemId provided")
                    finish()
                    return
                }
        val mediaSourceId = intent.getStringExtra("mediaSourceId") ?: ""
        val audioStreamIndex = intent.getIntExtra("audioStreamIndex", -1).takeIf { it != -1 }
        val subtitleStreamIndex = intent.getIntExtra("subtitleStreamIndex", -1).takeIf { it != -1 }
        val startPositionMs = intent.getLongExtra("startPositionMs", 0L)
        val seasonId = intent.getStringExtra("seasonId")?.let { UUID.fromString(it) }
        val shuffle = intent.getBooleanExtra("shuffle", false)
        val playlistId = intent.getStringExtra("playlistId")?.let { UUID.fromString(it) }
        val isLiveChannel = intent.getBooleanExtra("isLiveChannel", false)
        val channelName = intent.getStringExtra("channelName")
        val liveStreamUrl = intent.getStringExtra("liveStreamUrl")

        Timber.d(
            "PlayerActivity: Starting playback for item $itemId, seasonId=$seasonId, shuffle=$shuffle, isLive=$isLiveChannel"
        )

        setContent {
            val themeModeFlow = remember { preferencesRepository.getThemeModeFlow() }
            val dynamicColorsFlow = remember { preferencesRepository.getDynamicColorsFlow() }
            val showRatingsFlow = remember { preferencesRepository.getShowRatingsFlow() }
            val showAwardsFlow = remember { preferencesRepository.getShowAwardsFlow() }
            val themeMode by themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val dynamicColors by dynamicColorsFlow.collectAsStateWithLifecycle(initialValue = true)
            val showRatings by showRatingsFlow.collectAsStateWithLifecycle(initialValue = true)
            val showAwards by showAwardsFlow.collectAsStateWithLifecycle(initialValue = true)

            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.enterPictureInPicture = { enterPictureInPicture() }
                viewModel.updatePipParams = {
                    if (isPipSupported) {
                        setPictureInPictureParams(buildPipParams())
                    }
                }
            }

            LaunchedEffect(uiState.resolvedOrientation, uiState.isCasting) {
                requestedOrientation =
                    if (uiState.isCasting) {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        uiState.resolvedOrientation
                    }
            }

            AFinityTheme(themeMode = themeMode, dynamicColor = dynamicColors) {
                CompositionLocalProvider(
                    LocalShowRatings provides showRatings,
                    LocalShowAwards provides showAwards,
                ) {
                    PlayerScreenWrapper(
                        itemId = itemId,
                        mediaSourceId = mediaSourceId,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        startPositionMs = startPositionMs,
                        seasonId = seasonId,
                        shuffle = shuffle,
                        playlistId = playlistId,
                        isLiveChannel = isLiveChannel,
                        channelName = channelName,
                        liveStreamUrl = liveStreamUrl,
                        onBackPressed = { finish() },
                        modifier =
                            Modifier.fillMaxSize()
                                .onFocusChanged { playerUiHasFocus = it.hasFocus }
                                .focusGroup(),
                    )
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val state = viewModel.uiState.value
        // Only full keyboards get shortcuts: remotes, gamepads and D-pads keep plain focus
        // navigation. viewModel.player is unset until isPlayerReady; while casting, the cast
        // controller owns playback, the controls lock must block keyboard input like it blocks
        // gestures, and an open in-window panel needs the keys for its own focus navigation.
        if (
            event.device?.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC ||
                !state.isPlayerReady ||
                state.currentItem == null ||
                viewModel.isOverlayPanelOpen ||
                state.isCasting ||
                viewModel.castManager.castState.value.isConnected ||
                state.isControlsLocked ||
                event.isCtrlPressed ||
                event.isAltPressed ||
                event.isMetaPressed
        ) {
            return super.dispatchKeyEvent(event)
        }
        val shortcut = keyboardShortcut(event, state) ?: return super.dispatchKeyEvent(event)
        // A focused control (panel list, slider, button) keeps its own key handling; shortcuts
        // only take the keys it leaves unused. Arrows stay with focus navigation even at its edge.
        if (playerUiHasFocus) {
            if (super.dispatchKeyEvent(event)) return true
            if (event.keyCode in arrowKeys) return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && isShortcutRepeatAllowed(event)) {
            shortcut()
        }
        return true
    }

    // Mirrors the YouTube web player's keyboard shortcuts.
    private fun keyboardShortcut(
        event: KeyEvent,
        state: PlayerViewModel.PlayerUiState,
    ): (() -> Unit)? {
        val player = viewModel.player
        val shift = event.isShiftPressed
        val canSeek = !state.isLiveChannel && !state.isPlayingIntro
        // Seek keys are swallowed rather than passed on when seeking is off, so they don't
        // silently turn into focus navigation on live TV or intros.
        val noOp: () -> Unit = {}
        val playlist = viewModel.playlistState.value
        return when (event.keyCode) {
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_K -> {
                {
                    viewModel.handlePlayerEvent(
                        if (
                            state.isPlaying ||
                                (state.playWhenReady && player.playbackState != Player.STATE_ENDED)
                        ) {
                            PlayerEvent.Pause
                        } else {
                            PlayerEvent.Play
                        }
                    )
                }
            }
            KeyEvent.KEYCODE_J -> if (canSeek) seekBy(-10_000) else noOp
            KeyEvent.KEYCODE_L -> if (canSeek) seekBy(10_000) else noOp
            KeyEvent.KEYCODE_DPAD_LEFT -> if (canSeek) seekBy(-5_000) else noOp
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (canSeek) seekBy(5_000) else noOp
            KeyEvent.KEYCODE_DPAD_UP -> {
                { setVolumeStep(currentVolumeStep() + 1) }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                { setVolumeStep(currentVolumeStep() - 1) }
            }
            KeyEvent.KEYCODE_M -> {
                {
                    val step = currentVolumeStep()
                    if (step > 0) {
                        volumeStepBeforeMute = step
                        setVolumeStep(0)
                    } else {
                        setVolumeStep(volumeStepBeforeMute)
                    }
                }
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
                if (shift || (event.keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && !event.isNumLockOn)) {
                    null
                } else if (!canSeek) {
                    noOp
                } else {
                    {
                        val digit =
                            if (event.keyCode <= KeyEvent.KEYCODE_9) {
                                event.keyCode - KeyEvent.KEYCODE_0
                            } else {
                                event.keyCode - KeyEvent.KEYCODE_NUMPAD_0
                            }
                        if (player.duration > 0) seekTo(player.duration * digit / 10)
                    }
                }
            KeyEvent.KEYCODE_MOVE_HOME ->
                if (canSeek) {
                    { seekTo(0) }
                } else noOp
            KeyEvent.KEYCODE_MOVE_END ->
                if (canSeek) {
                    { if (player.duration > 0) seekTo(player.duration) }
                } else noOp
            KeyEvent.KEYCODE_N ->
                if (shift && canSeek && playlist.hasNext) {
                    { viewModel.onNextEpisode() }
                } else null
            KeyEvent.KEYCODE_P ->
                if (shift && canSeek && playlist.hasPrevious) {
                    { viewModel.onPreviousEpisode() }
                } else null
            KeyEvent.KEYCODE_I -> {
                { viewModel.handlePlayerEvent(PlayerEvent.EnterPictureInPicture) }
            }
            // '<' and '>' sit on different physical keys across layouts (e.g. QWERTZ).
            else ->
                when (if (state.isSpeedingUp) null else event.unicodeChar.toChar()) {
                    '>' -> {
                        { changePlaybackSpeed(0.25f) }
                    }
                    '<' -> {
                        { changePlaybackSpeed(-0.25f) }
                    }
                    else -> null
                }
        }
    }

    // Every repeated seek in a SyncPlay group is sent to the server and re-syncs all members.
    private fun isShortcutRepeatAllowed(event: KeyEvent): Boolean =
        event.repeatCount == 0 ||
            event.keyCode in repeatableVolumeKeys ||
            (event.keyCode in repeatableSeekKeys &&
                !syncPlayViewModel.syncPlayState.value.isInGroup)

    private fun seekBy(deltaMs: Long): () -> Unit = {
        viewModel.handlePlayerEvent(PlayerEvent.SeekRelative(deltaMs))
    }

    private fun seekTo(positionMs: Long) {
        viewModel.handlePlayerEvent(PlayerEvent.Seek(positionMs))
        viewModel.showControls()
    }

    private fun changePlaybackSpeed(delta: Float) {
        val speed = (viewModel.uiState.value.playbackSpeed + delta).coerceIn(0.25f, 2f)
        viewModel.handlePlayerEvent(PlayerEvent.SetPlaybackSpeed(speed))
        viewModel.showControls()
    }

    private fun currentVolumeStep(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private fun setVolumeStep(step: Int) {
        val maxStep = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = step.coerceIn(0, maxStep)
        // SetVolume takes a percentage that VolumeManager truncates back to a stream step; pick
        // the percentage that lands on the target under that same conversion. With more than
        // 100 steps not every step is reachable, so move to the nearest one in that direction.
        val stepAt = { percent: Int -> ((percent.toFloat() / 100f) * maxStep).toInt() }
        val percent =
            if (target < currentVolumeStep()) {
                (100 downTo 0).firstOrNull { stepAt(it) <= target } ?: 0
            } else {
                (0..100).firstOrNull { stepAt(it) >= target } ?: 100
            }
        viewModel.handlePlayerEvent(PlayerEvent.SetVolume(percent))
    }

    private fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    fun enterPictureInPicture() {
        if (!isPipSupported) {
            return
        }

        try {
            enterPictureInPictureMode(buildPipParams())
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to enter PiP mode")
        }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val rootView = window.decorView
        val viewWidth = rootView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val viewHeight = rootView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val displayAspectRatio = Rational(viewWidth, viewHeight)

        val videoSize = viewModel.player.videoSize
        val aspectRatio =
            if (videoSize.width > 0 && videoSize.height > 0) {
                Rational(
                    videoSize.width.coerceAtMost((videoSize.height * 2.39f).toInt()),
                    videoSize.height.coerceAtMost((videoSize.width * 2.39f).toInt()),
                )
            } else {
                Rational(16, 9)
            }

        val sourceRectHint =
            if (displayAspectRatio < aspectRatio) {
                val space =
                    ((viewHeight - (viewWidth.toFloat() / aspectRatio.toFloat())) / 2).toInt()
                android.graphics.Rect(
                    0,
                    space,
                    viewWidth,
                    (viewWidth.toFloat() / aspectRatio.toFloat()).toInt() + space,
                )
            } else {
                val space =
                    ((viewWidth - (viewHeight.toFloat() * aspectRatio.toFloat())) / 2).toInt()
                android.graphics.Rect(
                    space,
                    0,
                    (viewHeight.toFloat() * aspectRatio.toFloat()).toInt() + space,
                    viewHeight,
                )
            }

        val actions = buildPipActions()

        return PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .setSourceRectHint(sourceRectHint)
            .setActions(actions)
            .setAutoEnterEnabled(viewModel.player.isPlaying)
            .build()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        viewModel.onPipModeChanged(isInPictureInPictureMode)

        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            Timber.d("PiP dismissed, stopping playback")
            viewModel.stopPlayback()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()

        if (wasPip) {
            wasPip = false
        } else {
            viewModel.onResume()
        }
    }

    override fun onPause() {
        super.onPause()

        if (isInPictureInPictureMode) {
            wasPip = true
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (isPipSupported) {
            setPictureInPictureParams(buildPipParams())
        }
    }

    override fun onStop() {
        super.onStop()

        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        val isScreenOn = powerManager.isInteractive

        val allowBackground = viewModel.uiState.value.pipBackgroundPlay

        if (isInPictureInPictureMode) {
            wasPip = true
            if (!isScreenOn && !allowBackground) {
                viewModel.onPause()
            }
        } else if (wasPip) {
            viewModel.stopPlayback()
            finish()
        } else {
            viewModel.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (!isInPictureInPictureMode) {
            viewModel.stopPlayback()
        }

        try {
            unregisterReceiver(pipReceiver)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unregister PIP receiver")
        }
    }

    private fun buildPipActions(): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()

        val playPauseIntent = Intent(ACTION_PLAY_PAUSE).apply { setPackage(packageName) }
        val playPausePendingIntent =
            PendingIntent.getBroadcast(
                this,
                REQUEST_PLAY_PAUSE,
                playPauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val playPauseIcon =
            if (viewModel.player.isPlaying) {
                Icon.createWithResource(this, R.drawable.ic_player_pause_filled)
            } else {
                Icon.createWithResource(this, R.drawable.ic_player_play_filled)
            }
        val title =
            if (viewModel.player.isPlaying) getString(R.string.cd_pause)
            else getString(R.string.cd_play)
        val playPauseAction = RemoteAction(playPauseIcon, title, title, playPausePendingIntent)
        actions.add(playPauseAction)

        return actions
    }
}
