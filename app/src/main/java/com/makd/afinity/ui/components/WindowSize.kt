package com.makd.afinity.ui.components

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

@Composable
@ReadOnlyComposable
fun isLandscapeWindow(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width > size.height
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun listDetailPaneDirective(): PaneScaffoldDirective {
    val defaultDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val windowSize = LocalWindowInfo.current.containerSize
    val isLandscape = windowSize.width > windowSize.height
    val screenWidthDp = with(LocalDensity.current) { windowSize.width.toDp() }
    val canSplit = isLandscape && screenWidthDp >= 600.dp
    val preferredListWidth =
        when {
            screenWidthDp >= 1200.dp -> 380.dp
            screenWidthDp >= 840.dp -> 340.dp
            screenWidthDp >= 600.dp -> (screenWidthDp * 0.38f).coerceIn(260.dp, 320.dp)
            else -> screenWidthDp
        }
    return PaneScaffoldDirective(
        maxHorizontalPartitions = if (canSplit) 2 else 1,
        horizontalPartitionSpacerSize = 0.dp,
        maxVerticalPartitions = defaultDirective.maxVerticalPartitions,
        verticalPartitionSpacerSize = defaultDirective.verticalPartitionSpacerSize,
        defaultPanePreferredWidth = preferredListWidth,
        excludedBounds = defaultDirective.excludedBounds,
    )
}
