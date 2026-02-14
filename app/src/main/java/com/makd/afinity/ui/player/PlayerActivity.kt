package com.makd.afinity.ui.player

import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Process
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.ui.theme.AFinityTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    @Inject lateinit var preferencesRepository: PreferencesRepository

    private var wasPip: Boolean = false
    private var isResumed: Boolean = false

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
        val isLiveChannel = intent.getBooleanExtra("isLiveChannel", false)
        val channelName = intent.getStringExtra("channelName")
        val liveStreamUrl = intent.getStringExtra("liveStreamUrl")

        Timber.d(
            "PlayerActivity: Starting playback for item $itemId, seasonId=$seasonId, shuffle=$shuffle, isLive=$isLiveChannel"
        )

        setContent {
            val themeMode by
                preferencesRepository.getThemeModeFlow().collectAsState(initial = "SYSTEM")
            val dynamicColors by
                preferencesRepository.getDynamicColorsFlow().collectAsState(initial = true)

            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.enterPictureInPicture = { enterPictureInPicture() }
                viewModel.updatePipParams = {
                    if (isPipSupported) {
                        setPictureInPictureParams(buildPipParams())
                    }
                }
            }

            LaunchedEffect(uiState.resolvedOrientation) {
                requestedOrientation = uiState.resolvedOrientation
            }

            AFinityTheme(themeMode = themeMode, dynamicColor = dynamicColors) {
                PlayerScreenWrapper(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    startPositionMs = startPositionMs,
                    seasonId = seasonId,
                    shuffle = shuffle,
                    isLiveChannel = isLiveChannel,
                    channelName = channelName,
                    liveStreamUrl = liveStreamUrl,
                    onBackPressed = { finish() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
        val playerView = viewModel.getPlayerView()
        val viewWidth = playerView?.width ?: resources.displayMetrics.widthPixels
        val viewHeight = playerView?.height ?: resources.displayMetrics.heightPixels

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

        if (!isInPictureInPictureMode) {
            window.decorView.postDelayed(
                {
                    if (!isResumed) {
                        viewModel.stopPlayback()
                        finish()
                    }
                },
                100,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        hideSystemUI()

        if (wasPip) {
            wasPip = false
        } else {
            viewModel.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        isResumed = false

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
