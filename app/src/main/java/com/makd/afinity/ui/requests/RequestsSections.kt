package com.makd.afinity.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
    modifier: Modifier = Modifier
) {
    val cardWidth = rememberPortraitCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = title,
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