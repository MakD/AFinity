package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.item.components.shared.LandscapeDetailTheme

sealed interface LandscapeDetailArrangement {
    data object Single : LandscapeDetailArrangement

    data object Split : LandscapeDetailArrangement

    data class Sidebar(val width: Dp) : LandscapeDetailArrangement
}

@Composable
fun LandscapeDetailLayout(
    backdropStyle: BackdropStyle,
    backdrop: List<Pair<String, String?>>,
    listState: LazyListState,
    arrangement: LandscapeDetailArrangement,
    items: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
    backdropDescription: String? = null,
    listVerticalArrangement: Arrangement.Vertical = Arrangement.Top,
    pane: @Composable ColumnScope.() -> Unit = {},
) {
    if (backdropStyle == BackdropStyle.BlurredCover) {
        LandscapeDetailBody(
            backdropStyle,
            backdrop,
            listState,
            arrangement,
            items,
            modifier,
            backdropDescription,
            listVerticalArrangement,
            pane,
        )
    } else {
        LandscapeDetailTheme {
            LandscapeDetailBody(
                backdropStyle,
                backdrop,
                listState,
                arrangement,
                items,
                modifier,
                backdropDescription,
                listVerticalArrangement,
                pane,
            )
        }
    }
}

@Composable
private fun LandscapeDetailBody(
    backdropStyle: BackdropStyle,
    backdrop: List<Pair<String, String?>>,
    listState: LazyListState,
    arrangement: LandscapeDetailArrangement,
    items: LazyListScope.() -> Unit,
    modifier: Modifier,
    backdropDescription: String?,
    listVerticalArrangement: Arrangement.Vertical,
    pane: @Composable ColumnScope.() -> Unit,
) {
    val bottom = detailListBottomPadding()
    val top = afinityTopAppBarHeight()
    val viewport =
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()

    Box(modifier = modifier.fillMaxSize()) {
        DetailBackdrop(backdropStyle, backdrop, backdropDescription)

        when (arrangement) {
            LandscapeDetailArrangement.Single ->
                LazyColumn(
                    state = listState,
                    modifier = viewport,
                    contentPadding =
                        PaddingValues(
                            start = DetailGutter,
                            top = top,
                            end = DetailGutter,
                            bottom = bottom,
                        ),
                    verticalArrangement = listVerticalArrangement,
                    content = items,
                )

            LandscapeDetailArrangement.Split ->
                Row(modifier = viewport) {
                    Column(
                        modifier =
                            Modifier.weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    start = DetailGutter,
                                    top = top,
                                    end = DetailGutter,
                                    bottom = bottom,
                                ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = pane,
                    )
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier.weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(top = top, bottom = bottom),
                        verticalArrangement = listVerticalArrangement,
                        content = items,
                    )
                }

            is LandscapeDetailArrangement.Sidebar ->
                Row(
                    modifier = viewport.padding(horizontal = DetailGutter),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(
                        modifier =
                            Modifier.width(arrangement.width)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(top = top, bottom = bottom),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = pane,
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(top = top, bottom = bottom),
                        verticalArrangement = listVerticalArrangement,
                        content = items,
                    )
                }
        }
    }
}
