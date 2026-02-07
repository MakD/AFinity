package com.makd.afinity.ui.audiobookshelf.item.series

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.ui.audiobookshelf.item.components.ItemHeroBackground
import com.makd.afinity.ui.audiobookshelf.item.components.SeriesCoverGrid

@Composable
fun AudiobookshelfSeriesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String, String?, Double?) -> Unit,
    viewModel: AudiobookshelfSeriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val serverUrl = config?.serverUrl

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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

                    ItemHeroBackground(coverUrl = firstCoverUrl)

                    Row(
                        modifier =
                            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Spacer(modifier = Modifier.statusBarsPadding())

                            SeriesCoverGrid(
                                coverUrls = coverUrls,
                                modifier = Modifier.width(200.dp),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = viewModel.seriesName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "${uiState.totalBooks} books",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        LazyColumn(
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    ),
                            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                Spacer(modifier = Modifier.statusBarsPadding())
                                Text(
                                    text = "Books",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier =
                                        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                                )
                            }

                            itemsIndexed(uiState.books) { index, book ->
                                SeriesBookItem(
                                    book = book,
                                    bookNumber = index + 1,
                                    serverUrl = serverUrl,
                                    onPlay = { onNavigateToPlayer(book.id, null, null) },
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            SeriesHeader(
                                seriesName = viewModel.seriesName,
                                totalBooks = uiState.totalBooks,
                                coverUrls = coverUrls,
                                firstCoverUrl = coverUrls.firstOrNull(),
                            )
                        }

                        item {
                            Text(
                                text = "Books",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier =
                                    Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                            )
                        }

                        itemsIndexed(uiState.books) { index, book ->
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
                Text(
                    text = "Failed to load series",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                ),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_left),
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun SeriesHeader(
    seriesName: String,
    totalBooks: Int,
    coverUrls: List<String>,
    firstCoverUrl: String?,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
            ItemHeroBackground(coverUrl = firstCoverUrl)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(60.dp))

            SeriesCoverGrid(coverUrls = coverUrls, modifier = Modifier.width(200.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = seriesName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$totalBooks books",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeriesBookItem(
    book: LibraryItem,
    bookNumber: Int,
    serverUrl: String?,
    onPlay: () -> Unit,
) {
    val coverUrl = if (serverUrl != null) "$serverUrl/api/items/${book.id}/cover" else null

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
                contentDescription = "Play",
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
