package com.makd.afinity.ui.item.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import org.jellyfin.sdk.model.api.PersonKind

@Composable
internal fun OverviewSection(item: AfinityItem) {
    var isExpanded by remember { mutableStateOf(false) }
    var isEllipsized by remember { mutableStateOf(false) }

    if (item.overview.isNotEmpty()) {
        Column {
            Text(
                text = item.overview,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize(),
                onTextLayout = { result ->
                    if (!isExpanded) {
                        isEllipsized = result.hasVisualOverflow
                    }
                },
            )

            if (isEllipsized || isExpanded) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                isExpanded = !isExpanded
                            }
                            .padding(vertical = 4.dp),
                ) {
                    Text(
                        text =
                            if (isExpanded) stringResource(R.string.action_see_less)
                            else stringResource(R.string.action_see_more),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Icon(
                        painter =
                            if (isExpanded) painterResource(id = R.drawable.ic_keyboard_arrow_up)
                            else painterResource(id = R.drawable.ic_keyboard_arrow_down),
                        contentDescription =
                            if (isExpanded) stringResource(R.string.cd_collapse)
                            else stringResource(R.string.cd_expand),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TaglineSection(item: AfinityItem) {
    val tagline =
        when (item) {
            is AfinityMovie -> item.tagline
            is AfinityShow -> item.tagline
            else -> null
        }

    tagline
        ?.takeIf { it.isNotBlank() }
        ?.let { taglineText ->
            Text(
                text = taglineText,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
}

@Composable
internal fun DirectorSection(item: AfinityItem) {
    val directors =
        when (item) {
            is AfinityMovie -> item.people.filter { it.type == PersonKind.DIRECTOR }
            is AfinityShow -> item.people.filter { it.type == PersonKind.DIRECTOR }
            else -> emptyList()
        }

    if (directors.isNotEmpty()) {
        Text(
            text =
                stringResource(R.string.director_prefix, directors.joinToString(", ") { it.name }),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun WriterSection(item: AfinityItem) {
    val writers =
        when (item) {
            is AfinityMovie -> item.people.filter { it.type == PersonKind.WRITER }
            is AfinityShow -> item.people.filter { it.type == PersonKind.WRITER }
            else -> emptyList()
        }

    if (writers.isNotEmpty()) {
        Text(
            text = stringResource(R.string.writers_prefix, writers.joinToString(", ") { it.name }),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SeasonsSection(
    seasons: List<AfinitySeason>,
    navController: NavController,
    widthSizeClass: WindowWidthSizeClass,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.seasons_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )

        val cardWidth = widthSizeClass.portraitWidth

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(seasons) { season ->
                SeasonCard(
                    season = season,
                    onClick = {
                        val route = Destination.createItemDetailRoute(season.id.toString())
                        navController.navigate(route)
                    },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
internal fun SeasonCard(season: AfinitySeason, onClick: () -> Unit, cardWidth: Dp) {

    Column(modifier = Modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    imageUrl = season.images.primaryImageUrl,
                    contentDescription = season.name,
                    blurHash = season.images.primaryBlurHash,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth * 3f / 2f,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                if (season.played) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = stringResource(R.string.cd_watched_status),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    season.unplayedItemCount?.let { unwatchedCount ->
                        if (unwatchedCount > 0) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            ) {
                                Text(
                                    text =
                                        if (unwatchedCount > 99)
                                            stringResource(R.string.home_episode_count_plus)
                                        else
                                            stringResource(
                                                R.string.home_episode_count_fmt,
                                                unwatchedCount,
                                            ),
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = season.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )

        season.productionYear?.let { year ->
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
