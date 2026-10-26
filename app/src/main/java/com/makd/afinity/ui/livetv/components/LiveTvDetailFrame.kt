package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.ImageHeroKind
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.isLandscapeWindow

@Composable
fun LiveTvDetailFrame(
    heroChain: List<Pair<String, String?>>,
    backdropDescription: String,
    lazyListState: LazyListState,
    header: @Composable ColumnScope.(isLandscape: Boolean) -> Unit,
    items: LazyListScope.(horizontalPadding: Dp) -> Unit,
) {
    if (isLandscapeWindow()) {
        LandscapeDetailLayout(
            backdropStyle = BackdropStyle.Cinematic,
            backdrop = heroChain,
            listState = lazyListState,
            arrangement = LandscapeDetailArrangement.Single,
            backdropDescription = backdropDescription,
            items = {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { header(true) }
                }
                items(0.dp)
            },
        )
    } else {
        PortraitDetailLayout(
            hero = PortraitHero.Image(kind = ImageHeroKind.Backdrop, backdrop = heroChain),
            listState = lazyListState,
            header = { header(false) },
            items = { items(DetailGutter) },
        )
    }
}
