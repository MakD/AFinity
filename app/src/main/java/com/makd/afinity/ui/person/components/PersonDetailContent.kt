package com.makd.afinity.ui.person.components

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import com.makd.afinity.ui.utils.htmlToAnnotatedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailContent(
    person: AfinityPersonDetail,
    movies: List<AfinityMovie>,
    shows: List<AfinityShow>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val cardWidth = widthSizeClass.portraitWidth

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { PersonHeroSection(person = person) }

        item {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .offset(y = (-110).dp)
                        .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = person.name,
                    style =
                        MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                        ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                PersonMetadataSection(person = person)

                if (person.overview.isNotBlank()) {
                    PersonOverviewSection(overview = person.overview)
                }

                if (movies.isNotEmpty()) {
                    PersonFilmographySection(
                        title = stringResource(R.string.person_movies_fmt, movies.size),
                        items = movies,
                        onItemClick = onItemClick,
                        cardWidth = cardWidth,
                    )
                }

                if (shows.isNotEmpty()) {
                    PersonFilmographySection(
                        title = stringResource(R.string.person_shows_fmt, shows.size),
                        items = shows,
                        onItemClick = onItemClick,
                        cardWidth = cardWidth,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonHeroSection(person: AfinityPersonDetail) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    Box(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.6f)) {
        AsyncImage(
            imageUrl = person.images.primaryImageUrl,
            contentDescription = stringResource(R.string.cd_person_image_fmt, person.name),
            blurHash = person.images.primaryBlurHash,
            targetWidth = LocalConfiguration.current.screenWidthDp.dp,
            targetHeight = screenHeight * 0.6f,
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithCache {
                        val gradient =
                            Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height * 0.75f,
                                endY = size.height,
                            )
                        onDrawWithContent {
                            drawContent()
                            drawRect(gradient, blendMode = BlendMode.DstIn)
                        }
                    },
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
    }
}

@Composable
private fun PersonOverviewSection(overview: String, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }
    var isEllipsized by remember { mutableStateOf(false) }

    val containsHtml =
        remember(overview) {
            overview.contains("<a ", ignoreCase = true) ||
                overview.contains("</a>", ignoreCase = true) ||
                overview.contains("<br", ignoreCase = true)
        }

    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedText =
        remember(overview, linkColor) {
            if (containsHtml) htmlToAnnotatedString(overview, linkColor) else null
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.person_biography),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (containsHtml && annotatedText != null) {
            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                modifier = Modifier.animateContentSize(),
                overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                onTextLayout = { result ->
                    if (!isExpanded) {
                        isEllipsized = result.hasVisualOverflow
                    }
                },
            )
        } else {
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                modifier = Modifier.animateContentSize(),
                overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                onTextLayout = { result ->
                    if (!isExpanded) {
                        isEllipsized = result.hasVisualOverflow
                    }
                },
            )
        }

        if (isEllipsized || isExpanded) {
            Text(
                text =
                    if (isExpanded) stringResource(R.string.action_show_less)
                    else stringResource(R.string.action_show_more),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        isExpanded = !isExpanded
                    },
            )
        }
    }
}

@Composable
private fun PersonFilmographySection(
    title: String,
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(items = items, key = { item -> item.id }) { item ->
                FilmographyItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
private fun FilmographyItemCard(
    item: AfinityItem,
    onClick: () -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    MediaItemCard(item = item, onClick = onClick, modifier = modifier, cardWidth = cardWidth)
}

@Composable
private fun PersonMetadataSection(person: AfinityPersonDetail, modifier: Modifier = Modifier) {
    val hasBirthday = person.premiereDate != null
    val hasBirthplace = person.productionLocations.isNotEmpty()
    val hasExternalLinks = !person.externalUrls.isNullOrEmpty()

    if (hasBirthday || hasBirthplace || hasExternalLinks) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            person.premiereDate?.let { birthday ->
                val datePattern = stringResource(R.string.person_date_fmt)
                val formatter =
                    remember(datePattern) {
                        java.time.format.DateTimeFormatter.ofPattern(datePattern)
                    }
                val now = java.time.LocalDateTime.now()
                val age = java.time.Period.between(birthday.toLocalDate(), now.toLocalDate()).years
                Text(
                    text =
                        stringResource(R.string.person_born_fmt, birthday.format(formatter), age),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (person.productionLocations.isNotEmpty()) {
                Text(
                    text =
                        stringResource(
                            R.string.person_birthplace_fmt,
                            person.productionLocations.first(),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (hasExternalLinks) {
                PersonExternalLinksSection(person = person)
            }
        }
    }
}

@Composable
private fun PersonExternalLinksSection(person: AfinityPersonDetail, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val externalUrls = person.externalUrls ?: return

    Row(
        modifier = modifier.offset(x = (-8).dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        externalUrls.forEach { externalUrl ->
            val url = externalUrl.url ?: return@forEach
            val lowerUrl = url.lowercase()

            val iconRes =
                when {
                    "imdb" in lowerUrl -> R.drawable.ic_imdb_logo
                    "themoviedb.org" in lowerUrl -> R.drawable.ic_tmdb
                    "tvdb" in lowerUrl -> R.drawable.ic_tvdb
                    "trakt" in lowerUrl -> R.drawable.ic_trakt
                    "tvmaze" in lowerUrl -> R.drawable.ic_tvmaze
                    else -> R.drawable.ic_link
                }

            Box(
                modifier =
                    Modifier.clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                context.startActivity(intent)
                            },
                        )
                        .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription =
                        externalUrl.name ?: stringResource(R.string.cd_external_link),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(16.dp),
                )
            }
        }
    }
}
