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
import android.os.Build
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
import com.makd.afinity.R
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.ui.theme.AFinityTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject


@androidx.media3.common.util.UnstableApi
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private var pipCallback: (() -> Unit)? = null

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private var wasPip: Boolean = false
    private var wasZoom: Boolean = false

    companion object {
        private const val ACTION_PLAY_PAUSE = "com.makd.afinity.action.PLAY_PAUSE"
        private const val REQUEST_PLAY_PAUSE = 1001
    }

    private val pipReceiver = object : BroadcastReceiver() {
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = IntentFilter().apply {
                addAction(ACTION_PLAY_PAUSE)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(pipReceiver, filter, RECEIVER_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    registerReceiver(pipReceiver, filter)
                }
                Timber.d("PIP receiver registered successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to register PIP receiver")
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        val itemId = intent.getStringExtra("itemId")?.let { UUID.fromString(it) } ?: run {
            Timber.e("PlayerActivity: No itemId provided")
            finish()
            return
        }
        val mediaSourceId = intent.getStringExtra("mediaSourceId") ?: ""
        val audioStreamIndex = intent.getIntExtra("audioStreamIndex", -1).takeIf { it != -1 }
        val subtitleStreamIndex = intent.getIntExtra("subtitleStreamIndex", -1).takeIf { it != -1 }
        val startPositionMs = intent.getLongExtra("startPositionMs", 0L)

        Timber.d("PlayerActivity: Starting playback for item $itemId")

        setContent {
            val themeMode by preferencesRepository.getThemeModeFlow()
                .collectAsState(initial = "SYSTEM")
            val dynamicColors by preferencesRepository.getDynamicColorsFlow()
                .collectAsState(initial = true)

            LaunchedEffect(Unit) {
                viewModel.enterPictureInPicture = {
                    enterPictureInPicture()
                }
            }

            AFinityTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColors
            ) {
                PlayerScreenWrapper(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    startPositionMs = startPositionMs,
                    onBackPressed = { finish() },
                    modifier = Modifier.fillMaxSize()
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val pipGestureEnabled = runBlocking { preferencesRepository.getPipGestureEnabled() }

        if (pipGestureEnabled &&
            viewModel.player.isPlaying &&
            !viewModel.uiState.value.isControlsLocked
        ) {
            enterPictureInPicture()
        }
    }

    private fun enterPictureInPicture() {
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
        val displayAspectRatio = Rational(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels
        )

        val videoSize = viewModel.player.videoSize
        val aspectRatio = if (videoSize.width > 0 && videoSize.height > 0) {
            Rational(
                videoSize.width.coerceAtMost((videoSize.height * 2.39f).toInt()),
                videoSize.height.coerceAtMost((videoSize.width * 2.39f).toInt())
            )
        } else {
            Rational(16, 9)
        }

        val sourceRectHint = if (displayAspectRatio < aspectRatio) {
            val space = ((resources.displayMetrics.heightPixels -
                    (resources.displayMetrics.widthPixels.toFloat() / aspectRatio.toFloat())) / 2).toInt()
            android.graphics.Rect(
                0,
                space,
                resources.displayMetrics.widthPixels,
                (resources.displayMetrics.widthPixels.toFloat() / aspectRatio.toFloat()).toInt() + space
            )
        } else {
            val space = ((resources.displayMetrics.widthPixels -
                    (resources.displayMetrics.heightPixels.toFloat() * aspectRatio.toFloat())) / 2).toInt()
            android.graphics.Rect(
                space,
                0,
                (resources.displayMetrics.heightPixels.toFloat() * aspectRatio.toFloat()).toInt() + space,
                resources.displayMetrics.heightPixels
            )
        }

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .setSourceRectHint(sourceRectHint)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val actions = buildPipActions()
            if (actions.isNotEmpty()) {
                builder.setActions(actions)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(viewModel.player.isPlaying)
        }

        return builder.build()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        viewModel.onPipModeChanged(isInPictureInPictureMode)

        if (isInPictureInPictureMode) {
            Timber.d("Entered PIP mode - keeping playback active")
        } else {
            Timber.d("Exited PIP mode - restoring full screen")
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
        } else {
            viewModel.onPause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (wasPip) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                unregisterReceiver(pipReceiver)
            } catch (e: Exception) {
                Timber.e(e, "Failed to unregister PIP receiver")
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipActions(): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()

        val playPauseIntent = Intent(ACTION_PLAY_PAUSE).apply {
            setPackage(packageName)
        }
        val playPausePendingIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_PLAY_PAUSE,
            playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val playPauseIcon = if (viewModel.player.isPlaying) {
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_pause)
        } else {
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_play)
        }
        val playPauseAction = RemoteAction(
            playPauseIcon,
            if (viewModel.player.isPlaying) "Pause" else "Play",
            if (viewModel.player.isPlaying) "Pause" else "Play",
            playPausePendingIntent
        )
        actions.add(playPauseAction)

        return actions
    }
}