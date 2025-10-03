package com.makd.afinity.ui.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.makd.afinity.ui.theme.AFinityTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.UUID

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            AFinityTheme {
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

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }

    private fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}