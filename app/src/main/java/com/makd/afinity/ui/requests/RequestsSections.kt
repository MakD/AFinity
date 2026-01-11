package com.makd.afinity.ui.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R.drawable
import com.makd.afinity.data.models.jellyseerr.GenreSliderItem
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.Network
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.Studio
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import com.makd.afinity.util.BackdropTracker

@Composable
fun MyRequestsSection(
    requests: List<JellyseerrRequest>,
    isAdmin: Boolean,
    onRequestClick: (JellyseerrRequest) -> Unit,
    onApprove: (Int) -> Unit,
    onDecline: (Int) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
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
                    onDecline = { onDecline(request.id) },
                    cardWidth = cardWidth
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
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    onViewAllClick: (() -> Unit)? = null
) {
    val cardWidth = widthSizeClass.portraitWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp
    val headerBottomPadding = if (onViewAllClick != null) 4.dp else 16.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = headerBottomPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (onViewAllClick != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onViewAllClick
                        ),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Icon(
                        painter = painterResource(id = drawable.ic_chevron_right),
                        contentDescription = "View All",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
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
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}

@Composable
fun StudiosSection(
    studios: List<Studio>,
    onStudioClick: (Studio) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
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
                    onClick = { onStudioClick(studio) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}

@Composable
fun NetworksSection(
    networks: List<Network>,
    onNetworkClick: (Network) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)

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
                    onClick = { onNetworkClick(network) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}

@Composable
fun MovieGenresSection(
    genres: List<GenreSliderItem>,
    onGenreClick: (GenreSliderItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    backdropTracker: BackdropTracker? = null
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
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
                    isMovie = true,
                    cardWidth = cardWidth
                )
            }
        }
    }
}

@Composable
fun TvGenresSection(
    genres: List<GenreSliderItem>,
    onGenreClick: (GenreSliderItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    backdropTracker: BackdropTracker? = null
) {
    val cardWidth = widthSizeClass.landscapeWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
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
                    isMovie = false,
                    cardWidth = cardWidth
                )
            }
        }
    }
}