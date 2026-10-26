package com.makd.afinity.ui.audiobookshelf.item.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.ui.audiobookshelf.item.components.SeriesCoverGrid
import com.makd.afinity.ui.audiobookshelf.withAbsWidth
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.DetailCoverSize
import com.makd.afinity.ui.components.DetailCoverTitle
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.afinitySectionTitleStyle
import com.makd.afinity.ui.components.backdropChainOf
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.utils.rememberTopBarOpacity

@Composable
fun AudiobookshelfSeriesScreen(
    onNavigateToPlayer: (String, String?, Double?) -> Unit,
    onNavigateHome: () -> Unit = {},
    viewModel: AudiobookshelfSeriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val serverUrl = config?.serverUrl

    val isLandscape = isLandscapeWindow()

    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = {},
                onHomeClick = onNavigateHome,
                backgroundOpacity = backgroundOpacity,
            )
        },
        mode = AfinityScreenMode.Hero,
        scrollOpacity = { topBarOpacity },
    ) {
        when {
            uiState.isLoading -> {
                FullScreenLoading()
            }

            uiState.books.isNotEmpty() -> {
                val coverUrls =
                    if (serverUrl != null) {
                        uiState.books.take(4).map { "$serverUrl/api/items/${it.id}/cover" }
                    } else {
                        emptyList()
                    }

                if (isLandscape) {
                    val firstCoverUrl = coverUrls.firstOrNull()

                    LandscapeDetailLayout(
                        backdropStyle = BackdropStyle.BlurredCover,
                        backdrop = backdropChainOf(firstCoverUrl?.withAbsWidth(600)),
                        listState = lazyListState,
                        arrangement = LandscapeDetailArrangement.Split,
                        listVerticalArrangement = Arrangement.spacedBy(12.dp),
                        pane = {
                            SeriesCoverGrid(
                                coverUrls = coverUrls,
                                modifier = Modifier.width(DetailCoverSize),
                            )
                            DetailCoverTitle(
                                title = viewModel.seriesName,
                                subtitle =
                                    pluralStringResource(
                                        R.plurals.abs_series_books_fmt,
                                        uiState.totalBooks,
                                        uiState.totalBooks,
                                    ),
                            )
                        },
                        items = {
                            item { BooksSectionTitle() }

                            itemsIndexed(uiState.books, key = { _, book -> book.id }) { index, book
                                ->
                                SeriesBookItem(
                                    book = book,
                                    bookNumber = index + 1,
                                    serverUrl = serverUrl,
                                    onPlay = { onNavigateToPlayer(book.id, null, null) },
                                )
                            }
                        },
                    )
                } else {
                    PortraitDetailLayout(
                        hero =
                            PortraitHero.Cover(
                                backdropChainOf(coverUrls.firstOrNull()?.withAbsWidth(600))
                            ),
                        listState = lazyListState,
                        listVerticalArrangement = Arrangement.spacedBy(12.dp),
                        header = {
                            SeriesCoverGrid(
                                coverUrls = coverUrls,
                                modifier = Modifier.width(DetailCoverSize),
                            )
                            DetailCoverTitle(
                                title = viewModel.seriesName,
                                subtitle =
                                    pluralStringResource(
                                        R.plurals.abs_series_books_fmt,
                                        uiState.totalBooks,
                                        uiState.totalBooks,
                                    ),
                            )
                        },
                    ) {
                        item { BooksSectionTitle() }

                        itemsIndexed(uiState.books, key = { _, book -> book.id }) { index, book ->
                            SeriesBookItem(
                                book = book,
                                bookNumber = index + 1,
                                serverUrl = serverUrl,
                                onPlay = { onNavigateToPlayer(book.id, null, null) },
                            )
                        }
                    }
                }
            }

            uiState.error != null -> {
                FullScreenError(message = stringResource(R.string.abs_error_load_series))
            }
        }
    }
}

@Composable
private fun BooksSectionTitle() {
    Text(
        text = stringResource(R.string.abs_books),
        style = afinitySectionTitleStyle(),
        modifier = Modifier.padding(start = DetailGutter, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SeriesBookItem(
    book: LibraryItem,
    bookNumber: Int,
    serverUrl: String?,
    onPlay: () -> Unit,
) {
    val coverUrl =
        if (serverUrl != null) "$serverUrl/api/items/${book.id}/cover?width=216" else null

    val sequence = book.media.metadata.series?.firstOrNull()?.sequence
    val bookLabel = if (sequence != null) "Book $sequence" else "Book $bookNumber"

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier =
                    Modifier.size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = book.media.metadata.title ?: "Unknown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                book.media.metadata.publishedYear?.let { year ->
                    Text(
                        text = year,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                book.media.duration?.let { duration ->
                    val prefix = if (book.media.metadata.publishedYear != null) " • " else ""
                    Text(
                        text = "$prefix${formatDuration(duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_player_play_filled),
                contentDescription = stringResource(R.string.cd_play),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
