package com.makd.afinity.ui.components

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
    return PaneScaffoldDirective(
        maxHorizontalPartitions =
            if (isLandscapeWindow()) 2 else defaultDirective.maxHorizontalPartitions,
        horizontalPartitionSpacerSize = 0.dp,
        maxVerticalPartitions = defaultDirective.maxVerticalPartitions,
        verticalPartitionSpacerSize = defaultDirective.verticalPartitionSpacerSize,
        defaultPanePreferredWidth = 420.dp,
        excludedBounds = defaultDirective.excludedBounds,
    )
}
