package com.makd.afinity.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.calculateCardHeight
import com.makd.afinity.ui.theme.rememberLandscapeCardWidth
import com.makd.afinity.ui.theme.rememberPortraitCardWidth

@Composable
fun MyRequestsSection(
    requests: List<JellyseerrRequest>,
    isAdmin: Boolean,
    onRequestClick: (JellyseerrRequest) -> Unit,
    onApprove: (Int) -> Unit,
    onDecline: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = rememberLandscapeCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = "Recent Requests",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = requests,
                key = { request -> "request_${request.id}" }
            ) { request ->
                RequestCard(
                    request = request,
                    isAdmin = isAdmin,
                    onClick = { onRequestClick(request) },
                    onApprove = { onApprove(request.id) },
                    onDecline = { onDecline(request.id) }
                )
            }
        }
    }
}

@Composable
fun DiscoverSection(
    title: String,
    items: List<SearchResultItem>,
    onItemClick: (SearchResultItem) -> Unit,
    modifier: Modifier = Modifier,
    onViewAllClick: (() -> Unit)? = null
) {
    val cardWidth = rememberPortraitCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (onViewAllClick != null) {
                androidx.compose.material3.TextButton(
                    onClick = onViewAllClick
                ) {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = items,
                key = { item -> "${title.replace(" ", "_")}_${item.id}" }
            ) { item ->
                DiscoverMediaCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun StudiosSection(
    studios: List<com.makd.afinity.data.models.jellyseerr.Studio>,
    onStudioClick: (com.makd.afinity.data.models.jellyseerr.Studio) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = rememberLandscapeCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 10.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = "Studios",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = studios,
                key = { studio -> "studio_${studio.id}" }
            ) { studio ->
                StudioCard(
                    studio = studio,
                    onClick = { onStudioClick(studio) }
                )
            }
        }
    }
}

@Composable
fun NetworksSection(
    networks: List<com.makd.afinity.data.models.jellyseerr.Network>,
    onNetworkClick: (com.makd.afinity.data.models.jellyseerr.Network) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = rememberLandscapeCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = "Networks",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            modifier = Modifier.height(cardHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = networks,
                key = { network -> "network_${network.id}" }
            ) { network ->
                NetworkCard(
                    network = network,
                    onClick = { onNetworkClick(network) }
                )
            }
        }
    }
}

@Composable
fun MovieGenresSection(
    genres: List<com.makd.afinity.data.models.jellyseerr.GenreSliderItem>,
    onGenreClick: (com.makd.afinity.data.models.jellyseerr.GenreSliderItem) -> Unit,
    modifier: Modifier = Modifier,
    backdropTracker: com.makd.afinity.util.BackdropTracker? = null
) {
    val cardWidth = rememberLandscapeCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 10.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = "Movie Genres",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = genres,
                key = { genre -> "movie_genre_${genre.id}" }
            ) { genre ->
                GenreCard(
                    genre = genre,
                    onClick = { onGenreClick(genre) },
                    backdropTracker = backdropTracker,
                    isMovie = true
                )
            }
        }
    }
}

@Composable
fun TvGenresSection(
    genres: List<com.makd.afinity.data.models.jellyseerr.GenreSliderItem>,
    onGenreClick: (com.makd.afinity.data.models.jellyseerr.GenreSliderItem) -> Unit,
    modifier: Modifier = Modifier,
    backdropTracker: com.makd.afinity.util.BackdropTracker? = null
) {
    val cardWidth = rememberLandscapeCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 10.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = "TV Genres",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = genres,
                key = { genre -> "tv_genre_${genre.id}" }
            ) { genre ->
                GenreCard(
                    genre = genre,
                    onClick = { onGenreClick(genre) },
                    backdropTracker = backdropTracker,
                    isMovie = false
                )
            }
        }
    }
}