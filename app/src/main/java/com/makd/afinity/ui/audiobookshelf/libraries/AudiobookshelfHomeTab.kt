package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfSeries
import com.makd.afinity.data.models.audiobookshelf.Library
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.PersonalizedSection
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.audiobookshelf.libraries.components.AudiobookCard
import com.makd.afinity.ui.audiobookshelf.libraries.components.SeriesShelfCard
import com.makd.afinity.ui.components.CardRowContentPadding
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LibraryShortcutCard
import com.makd.afinity.ui.components.LibraryShortcutsRow
import com.makd.afinity.ui.components.afinitySectionTitleStyle
import com.makd.afinity.ui.components.cardRowBleed
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun AudiobookshelfHomeTab(
    sections: List<PersonalizedSection>,
    libraries: List<Library>,
    serverUrl: String?,
    onItemClick: (LibraryItem) -> Unit,
    onSeriesClick: (AudiobookshelfSeries) -> Unit,
    onBrowseSeries: () -> Unit,
    onBrowseLibrary: (Library) -> Unit,
    isLoading: Boolean,
    widthSizeClass: WindowWidthSizeClass,
) {
    when {
        isLoading -> {
            FullScreenLoading()
        }

        sections.isEmpty() -> {
            val playerOffset = LocalPlayerOffset.current
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = playerOffset),
            ) {
                item(key = "library_shortcuts") {
                    LibraryShortcutsRow(
                        libraries = libraries,
                        onBrowseSeries = onBrowseSeries,
                        onBrowseLibrary = onBrowseLibrary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                item(key = "no_personalized_content") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.abs_no_personalized_content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        else -> {
            val cardWidth = widthSizeClass.portraitWidth
            val fixedRowHeight =
                CardDimensions.rowHeight(
                    cardWidth,
                    CardDimensions.ASPECT_RATIO_SQUARE,
                    metadataHeight = CardDimensions.subtitleLine,
                )
            val playerOffset = LocalPlayerOffset.current
            val columnState = rememberLazyListState()

            LazyColumn(
                state = columnState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = playerOffset),
            ) {
                item(key = "library_shortcuts") {
                    LibraryShortcutsRow(
                        libraries = libraries,
                        onBrowseSeries = onBrowseSeries,
                        onBrowseLibrary = onBrowseLibrary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                items(
                    items = sections,
                    key = { it.id },
                    contentType = { section ->
                        if (section.series.isNotEmpty()) "series_section" else "item_section"
                    },
                ) { section ->
                    val rowListState = rememberLazyListState()
                    val currentFirstItemId =
                        section.items.firstOrNull()?.id ?: section.series.firstOrNull()?.id
                    var prevFirstItemId by remember { mutableStateOf(currentFirstItemId) }

                    if (prevFirstItemId != currentFirstItemId) {
                        if (prevFirstItemId != null && currentFirstItemId != null) {
                            rowListState.requestScrollToItem(0)
                        }
                        prevFirstItemId = currentFirstItemId
                    }

                    Column {
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(modifier = Modifier.padding(horizontal = CardDimensions.RowGutter)) {
                            Text(
                                text =
                                    if (section.id == "recent-series") {
                                        stringResource(R.string.abs_recently_added_series)
                                    } else {
                                        section.label
                                    },
                                style = afinitySectionTitleStyle(),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )

                            LazyRow(
                                state = rowListState,
                                modifier = Modifier.height(fixedRowHeight).cardRowBleed(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = CardRowContentPadding,
                            ) {
                                if (section.series.isNotEmpty()) {
                                    items(
                                        items = section.series,
                                        key = { series -> "${section.id}_${series.id}" },
                                    ) { series ->
                                        SeriesShelfCard(
                                            series = series,
                                            serverUrl = serverUrl,
                                            onClick = { onSeriesClick(series) },
                                            modifier = Modifier.width(cardWidth * 1.5f),
                                        )
                                    }
                                } else {
                                    items(
                                        items = section.items,
                                        key = { item ->
                                            val episodeId = item.recentEpisode?.id
                                            if (episodeId != null) {
                                                "${section.id}_${item.id}_$episodeId"
                                            } else {
                                                "${section.id}_${item.id}"
                                            }
                                        },
                                    ) { item ->
                                        AudiobookCard(
                                            item = item,
                                            serverUrl = serverUrl,
                                            onClick = { onItemClick(item) },
                                            modifier = Modifier.width(cardWidth),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class ShortcutTarget {
    data object Series : ShortcutTarget()

    data class ForLibrary(val library: Library) : ShortcutTarget()
}

private data class LibraryShortcut(
    val key: String,
    val target: ShortcutTarget,
    val label: String,
    val iconRes: Int,
    val gradientStart: Color,
    val gradientEnd: Color,
)

private val LIBRARY_SHORTCUT_GRADIENTS =
    listOf(
        Color(0xFFB1AADA) to Color(0xFF5A5482),
        Color(0xFFE2AE95) to Color(0xFF965243),
        Color(0xFFA4C4D6) to Color(0xFF50787A),
        Color(0xFFAED1AE) to Color(0xFF4F7E70),
        Color(0xFFD4A5C9) to Color(0xFF7B4F8C),
    )

@Composable
private fun LibraryShortcutsRow(
    libraries: List<Library>,
    onBrowseSeries: () -> Unit,
    onBrowseLibrary: (Library) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardWidth = CardDimensions.landscapeCardWidth

    val seriesLabel = stringResource(R.string.abs_tab_series)
    val shortcuts =
        remember(libraries, seriesLabel) {
            val targets: List<ShortcutTarget> = buildList {
                add(ShortcutTarget.Series)
                libraries.forEach { library -> add(ShortcutTarget.ForLibrary(library)) }
            }
            targets.mapIndexed { index, target ->
                val (start, end) =
                    LIBRARY_SHORTCUT_GRADIENTS[index % LIBRARY_SHORTCUT_GRADIENTS.size]
                when (target) {
                    is ShortcutTarget.Series ->
                        LibraryShortcut(
                            key = "series",
                            target = target,
                            label = seriesLabel,
                            iconRes = R.drawable.ic_collection,
                            gradientStart = start,
                            gradientEnd = end,
                        )
                    is ShortcutTarget.ForLibrary -> {
                        val iconRes =
                            when (target.library.mediaType.lowercase()) {
                                "podcast" -> R.drawable.ic_apple_podcast
                                "book" -> R.drawable.ic_book_audio
                                else -> R.drawable.ic_book
                            }
                        LibraryShortcut(
                            key = "library:${target.library.id}",
                            target = target,
                            label = target.library.name,
                            iconRes = iconRes,
                            gradientStart = start,
                            gradientEnd = end,
                        )
                    }
                }
            }
        }

    LibraryShortcutsRow(title = stringResource(R.string.abs_action_browse), modifier = modifier) {
        items(shortcuts, key = { it.key }) { shortcut ->
            LibraryShortcutCard(
                label = shortcut.label,
                iconRes = shortcut.iconRes,
                gradientStart = shortcut.gradientStart,
                gradientEnd = shortcut.gradientEnd,
                cardWidth = cardWidth,
                onClick = {
                    when (val target = shortcut.target) {
                        is ShortcutTarget.Series -> onBrowseSeries()
                        is ShortcutTarget.ForLibrary -> onBrowseLibrary(target.library)
                    }
                },
            )
        }
    }
}
