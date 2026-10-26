package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.makd.afinity.navigation.LocalPlayerOffset

val DetailGutter = 16.dp

val DetailCoverSize = 200.dp

@Composable
fun detailListBottomPadding(): Dp {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return max(navBarBottom, LocalPlayerOffset.current) + 16.dp
}
