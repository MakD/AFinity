package com.makd.afinity.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun GenreSection(
    genre: String,
    movies: List<AfinityMovie>,
    loaded: Boolean,
    onVisible: () -> Unit,
    onHidden: () -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onVisible()
        onDispose { onHidden() }
    }

    if (loaded && movies.isEmpty()) return

    val cardWidth = widthSizeClass.portraitWidth
    val fixedRowHeight = CardDimensions.rowHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)

    Column(modifier = modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = stringResource(R.string.home_genre_movies_title, genre))

        if (!loaded) {
            MediaRowSkeleton(cardWidth = cardWidth, height = fixedRowHeight)
        } else {
            CardRow(modifier = Modifier.height(fixedRowHeight)) {
                items(items = movies, key = { movie -> "genre_${genre}_${movie.id}" }) { movie ->
                    MediaItemCard(
                        item = movie,
                        onClick = { onItemClick(movie) },
                        cardWidth = cardWidth,
                    )
                }
            }
        }
    }
}
