package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.external.ExternalTitles
import com.makd.afinity.data.models.external.ExternalTitlesSource
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.ui.requests.DiscoverMediaCard
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun ExternalTitlesSection(
    title: String,
    titles: ExternalTitles,
    onSeerrItemClick: (SearchResultItem) -> Unit,
    cardWidth: Dp,
    onViewAllClick: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailSectionTitle(text = title)
            if (onViewAllClick != null) {
                Box(
                    modifier =
                        Modifier.size(40.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onViewAllClick,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = stringResource(R.string.cd_view_all),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(titles.items, key = { "${it.mediaType}_${it.id}" }) { item ->
                DiscoverMediaCard(
                    item = item,
                    onClick = {
                        when (titles.source) {
                            ExternalTitlesSource.SEERR -> onSeerrItemClick(item)
                            ExternalTitlesSource.TMDB ->
                                uriHandler.openUri(
                                    "https://www.themoviedb.org/${item.mediaType}/${item.id}"
                                )
                        }
                    },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}
