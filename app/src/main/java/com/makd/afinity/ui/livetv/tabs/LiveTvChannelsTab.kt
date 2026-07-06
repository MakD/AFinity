package com.makd.afinity.ui.livetv.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AlphabetScroller
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.livetv.LiveTvUiState
import com.makd.afinity.ui.livetv.components.ChannelCard

@Composable
fun LiveTvChannelsTab(
    uiState: LiveTvUiState,
    onChannelClick: (AfinityChannel) -> Unit,
    onFavoriteClick: (AfinityChannel) -> Unit,
    selectedLetter: String? = null,
    onLetterSelected: (String) -> Unit = {},
    onClearFilter: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        FullScreenLoading(modifier = modifier)
        return
    }

    val layoutDirection = LocalLayoutDirection.current
    val playerOffset = LocalPlayerOffset.current
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val safeStart = safeDrawing.calculateStartPadding(layoutDirection)
    safeDrawing.calculateEndPadding(layoutDirection)
    val safeBottom = safeDrawing.calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f)) {
                if (uiState.channels.isEmpty() && selectedLetter != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.livetv_empty_letter_fmt,
                                        selectedLetter ?: "",
                                    ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = onClearFilter,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(stringResource(R.string.action_show_all_channels))
                            }
                        }
                    }
                } else if (uiState.channels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.livetv_empty_generic),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                start = 16.dp + safeStart,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = max(safeBottom, playerOffset) + 16.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                Text(
                                    text =
                                        if (selectedLetter != null)
                                            stringResource(
                                                R.string.livetv_header_letter_fmt,
                                                selectedLetter,
                                            )
                                        else stringResource(R.string.livetv_header_all),
                                    style =
                                        MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text =
                                        stringResource(
                                            R.string.livetv_count_fmt,
                                            uiState.channels.size,
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        items(items = uiState.channels, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                onClick = { onChannelClick(channel) },
                                onFavoriteClick = { onFavoriteClick(channel) },
                                showProgramOverlays = false,
                            )
                        }
                    }
                }
            }

            Box(
                modifier =
                    Modifier.fillMaxHeight()
                        .padding(top = 16.dp, bottom = safeBottom)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)),
                contentAlignment = Alignment.Center,
            ) {
                AlphabetScroller(
                    selectedLetter = selectedLetter,
                    onLetterSelected = onLetterSelected,
                    modifier =
                        Modifier.background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = MaterialTheme.shapes.small,
                        ),
                )
            }
        }
    }
}
