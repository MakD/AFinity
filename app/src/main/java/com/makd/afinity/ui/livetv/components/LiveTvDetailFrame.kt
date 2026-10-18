package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.item.components.shared.DetailBackdrop
import com.makd.afinity.ui.item.components.shared.HeroSection
import com.makd.afinity.ui.item.components.shared.rememberLandscapeDetailColorScheme
import com.makd.afinity.ui.utils.verticalLayoutOffset

@Composable
fun LiveTvDetailFrame(
    heroChain: List<Pair<String, String?>>,
    backdropDescription: String,
    lazyListState: LazyListState,
    header: @Composable ColumnScope.(isLandscape: Boolean) -> Unit,
    items: LazyListScope.(horizontalPadding: Dp) -> Unit,
) {
    val playerOffset = LocalPlayerOffset.current

    if (isLandscapeWindow()) {
        val density = LocalDensity.current
        val statusBarHeight = WindowInsets.statusBars.getTop(density)
        val displayCutoutLeft = WindowInsets.displayCutout.getLeft(density, LayoutDirection.Ltr)

        MaterialTheme(colorScheme = rememberLandscapeDetailColorScheme()) {
            Box(modifier = Modifier.fillMaxSize()) {
                DetailBackdrop(fallbackChain = heroChain, contentDescription = backdropDescription)

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            top = with(density) { statusBarHeight.toDp() } + 64.dp,
                            start = with(density) { displayCutoutLeft.toDp() + 16.dp },
                            end = 16.dp,
                            bottom = 16.dp + playerOffset,
                        ),
                ) {
                    item(key = "header") {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { header(true) }
                    }
                    items(0.dp)
                }
            }
        }
    } else {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = max(bottomPadding, playerOffset) + 16.dp),
        ) {
            item(key = "hero") { HeroSection(fallbackChain = heroChain) }

            item(key = "header") {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .verticalLayoutOffset((-110).dp)
                            .padding(start = 16.dp, end = 16.dp, top = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    header(false)
                }
            }

            items(16.dp)
        }
    }
}
