package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color

enum class AfinityScreenMode {
    Standard,
    Hero,
    Immersive,
}

@Composable
fun AfinityScreen(
    topBar: @Composable (backgroundOpacity: () -> Float) -> Unit,
    modifier: Modifier = Modifier,
    mode: AfinityScreenMode = AfinityScreenMode.Standard,
    scrollOpacity: () -> Float = NoFill,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val isLandscape = isLandscapeWindow()
    val landscapeHero = mode == AfinityScreenMode.Hero && isLandscape
    val fillsOnScroll = mode != AfinityScreenMode.Standard && !landscapeHero
    val contentModifier =
        if (mode == AfinityScreenMode.Standard) {
            Modifier.statusBarsPadding().padding(top = afinityTopAppBarHeight()).clipToBounds()
        } else {
            Modifier
        }

    RequestDarkStatusBar(enabled = landscapeHero)

    Scaffold(
        modifier = modifier,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        contentWindowInsets =
            ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            ),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().then(contentModifier)) { content(innerPadding) }
            topBar(if (fillsOnScroll) scrollOpacity else NoFill)
        }
    }
}

private val NoFill: () -> Float = { 0f }
