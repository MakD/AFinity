package com.makd.afinity.ui.home.components

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
import com.makd.afinity.data.models.MovieSection
import com.makd.afinity.data.models.PersonFromMovieSection
import com.makd.afinity.data.models.PersonSection
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.calculateCardHeight
import com.makd.afinity.ui.theme.rememberPortraitCardWidth

@Composable
fun PersonSection(
    section: PersonSection,
    onItemClick: (AfinityMovie) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = rememberPortraitCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = section.sectionType.toDisplayTitle(section.person.name),
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
                items = section.items,
                key = { movie -> "person_${section.sectionType.name}_${section.person.id}_${movie.id}" }
            ) { movie ->
                MediaItemCard(
                    item = movie,
                    onClick = { onItemClick(movie) }
                )
            }
        }
    }
}

@Composable
fun MovieRecommendationSection(
    section: MovieSection,
    onItemClick: (AfinityMovie) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = rememberPortraitCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = section.sectionType.toDisplayTitle(section.referenceMovie.name),
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
                items = section.recommendedItems,
                key = { movie -> "recommend_${section.sectionType.name}_${section.referenceMovie.id}_${movie.id}" }
            ) { movie ->
                MediaItemCard(
                    item = movie,
                    onClick = { onItemClick(movie) }
                )
            }
        }
    }
}

@Composable
fun PersonFromMovieSection(
    section: PersonFromMovieSection,
    onItemClick: (AfinityMovie) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = rememberPortraitCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = section.toDisplayTitle(),
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
                items = section.items,
                key = { movie -> "actor_recent_${section.person.id}_${section.referenceMovie.id}_${movie.id}" }
            ) { movie ->
                MediaItemCard(
                    item = movie,
                    onClick = { onItemClick(movie) }
                )
            }
        }
    }
}