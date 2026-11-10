package com.makd.afinity.ui.item

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.makd.afinity.data.models.media.ThemeSong
import com.makd.afinity.player.theme.ThemeMusicPlayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val HERO_WAIT_MS = 1_000L

@Composable
fun ThemeMusicEffect(
    player: ThemeMusicPlayer,
    resolved: Boolean,
    song: ThemeSong?,
    heroLoaded: Boolean,
) {
    val entryLifecycle = LocalLifecycleOwner.current.lifecycle
    val activityLifecycle = (LocalActivity.current as? LifecycleOwner)?.lifecycle
    val owner = remember { Any() }
    val heroLoadedState = rememberUpdatedState(heroLoaded)
    var startCount by remember { mutableIntStateOf(0) }

    DisposableEffect(entryLifecycle, activityLifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    player.hold(owner)
                    startCount++
                }
                Lifecycle.Event.ON_STOP -> {
                    val appHidden =
                        activityLifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == false
                    if (appHidden) player.stop() else player.release(owner)
                }
                else -> Unit
            }
        }
        entryLifecycle.addObserver(observer)
        onDispose {
            entryLifecycle.removeObserver(observer)
            player.release(owner)
        }
    }

    LaunchedEffect(player, startCount, resolved, song) {
        if (startCount == 0 || !resolved) return@LaunchedEffect
        if (song != null) {
            withTimeoutOrNull(HERO_WAIT_MS) { snapshotFlow { heroLoadedState.value }.first { it } }
        }
        player.play(owner, song)
    }
}
