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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.MovieSection
import com.makd.afinity.data.models.MovieSectionType
import com.makd.afinity.data.models.PersonFromMovieSection
import com.makd.afinity.data.models.PersonSection
import com.makd.afinity.data.models.PersonSectionType
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun PersonSection(
    section: PersonSection,
    onItemClick: (AfinityMovie) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val cardWidth = widthSizeClass.portraitWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        val title = when (section.sectionType) {
            PersonSectionType.STARRING -> stringResource(R.string.home_person_starring, section.person.name)
            PersonSectionType.DIRECTED_BY -> stringResource(R.string.home_person_directed_by, section.person.name)
            PersonSectionType.WRITTEN_BY -> stringResource(R.string.home_person_written_by, section.person.name)
        }
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
                items = section.items,
                key = { movie -> "person_${section.sectionType.name}_${section.person.id}_${movie.id}" }
            ) { movie ->
                MediaItemCard(
                    item = movie,
                    onClick = { onItemClick(movie) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}

@Composable
fun MovieRecommendationSection(
    section: MovieSection,
    onItemClick: (AfinityMovie) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val cardWidth = widthSizeClass.portraitWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        val title = when (section.sectionType) {
            MovieSectionType.BECAUSE_YOU_WATCHED -> stringResource(R.string.home_because_you_watched, section.referenceMovie.name)
            MovieSectionType.STARRING_ACTOR_FROM -> stringResource(R.string.home_starring_from_watched, "", section.referenceMovie.name)
        }
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
                items = section.recommendedItems,
                key = { movie -> "recommend_${section.sectionType.name}_${section.referenceMovie.id}_${movie.id}" }
            ) { movie ->
                MediaItemCard(
                    item = movie,
                    onClick = { onItemClick(movie) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}

@Composable
fun PersonFromMovieSection(
    section: PersonFromMovieSection,
    onItemClick: (AfinityMovie) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val cardWidth = widthSizeClass.portraitWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.home_starring_from_watched, section.person.name, section.referenceMovie.name),
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
                    onClick = { onItemClick(movie) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}