package com.makd.afinity.ui.item.components.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.mdblist.MdbListRatingBadges
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.tmdb.TmdbReview
import java.util.Locale
import java.util.UUID

@Composable
fun BaseMediaDetailContent(
    item: AfinityItem,
    specialFeatures: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    tmdbReviews: List<TmdbReview>,
    mdbRatings: List<MdbListRating> = emptyList(),
    mdbRatingBadges: MdbListRatingBadges = MdbListRatingBadges(),
    isRatingsFromCache: Boolean = false,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    onBoxSetClick: (AfinityBoxSet) -> Unit,
    onPersonClick: (UUID) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    typeSpecificContent: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TaglineSection(item = item)
        OverviewSection(item = item)
        ExternalLinksSection(item = item)

        DirectorSection(item = item)
        WriterSection(item = item)

        typeSpecificContent()

        CastSection(item = item, onPersonClick = onPersonClick, widthSizeClass = widthSizeClass)

        RatingsAndReviews(
            item = item,
            mdbRatings = mdbRatings,
            mdbRatingBadges = mdbRatingBadges,
            tmdbReviews = tmdbReviews,
            isRatingsFromCache = isRatingsFromCache,
        )

        SpecialFeaturesSection(
            specialFeatures = specialFeatures,
            onItemClick = onSpecialFeatureClick,
            widthSizeClass = widthSizeClass,
        )

        InCollectionsSection(
            boxSets = containingBoxSets,
            onBoxSetClick = onBoxSetClick,
            widthSizeClass = widthSizeClass,
        )
    }
}

@Composable
private fun RatingsAndReviews(
    item: AfinityItem,
    mdbRatings: List<MdbListRating>,
    mdbRatingBadges: MdbListRatingBadges,
    tmdbReviews: List<TmdbReview>,
    isRatingsFromCache: Boolean,
) {
    val communityRating =
        when (item) {
            is AfinityMovie -> item.communityRating
            is AfinityShow -> item.communityRating
            is AfinityBoxSet -> item.communityRating
            else -> null
        }

    val criticRating =
        when (item) {
            is AfinityMovie -> item.criticRating
            else -> null
        }

    val hasRatings =
        mdbRatingBadges.hasAny ||
            mdbRatings.isNotEmpty() ||
            communityRating != null ||
            criticRating != null
    val hasReviews = tmdbReviews.isNotEmpty()

    if (!hasRatings && !hasReviews) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (hasRatings) {
            Text(
                text = "Ratings",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )

            AnimatedVisibility(
                visible = true,
                enter = if (isRatingsFromCache) EnterTransition.None else fadeIn(tween(500)),
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (mdbRatingBadges.certifiedFresh) {
                        item {
                            BadgeCard(
                                sourceName = "Tomatometer",
                                iconRes = R.drawable.ic_certified_fresh,
                                label = "Certified Fresh",
                            )
                        }
                    }

                    if (mdbRatingBadges.verifiedHot) {
                        item {
                            BadgeCard(
                                sourceName = "Popcornmeter",
                                iconRes = R.drawable.ic_verified_hot,
                                label = "Verified Hot",
                            )
                        }
                    }

                    communityRating?.let { rating ->
                        item {
                            Scorecard(
                                sourceName = "IMDb",
                                iconRes = R.drawable.ic_imdb_logo,
                                score = String.format(Locale.US, "%.1f", rating),
                                subtext = "/ 10",
                            )
                        }
                    }

                    criticRating?.let { rating ->
                        item {
                            Scorecard(
                                sourceName = "Rotten Tomatoes",
                                iconRes =
                                    if (rating > 60) R.drawable.ic_rotten_tomato_fresh
                                    else R.drawable.ic_rotten_tomato_rotten,
                                score = "${rating.toInt()}%",
                                subtext = if (rating > 60) "Fresh" else "Rotten",
                            )
                        }
                    }

                    val sortedMdbRatings = mdbRatings.sortedBy {
                        if (it.source.lowercase() == "popcorn") 0 else 1
                    }

                    items(sortedMdbRatings) { rating ->
                        val sourceLower = rating.source.lowercase()
                        val rawValue =
                            if (sourceLower == "metacriticuser") {
                                rating.score ?: (rating.value?.times(10.0)) ?: return@items
                            } else {
                                rating.value ?: return@items
                            }

                        val formattedScore =
                            if (sourceLower == "metacriticuser") {
                                String.format(Locale.US, "%.1f", rawValue / 10.0)
                            } else if (rawValue % 1.0 == 0.0) {
                                rawValue.toInt().toString()
                            } else {
                                rawValue.toString()
                            }

                        val isPercentage = sourceLower in listOf("trakt", "tmdb", "popcorn")

                        val iconRes =
                            when (sourceLower) {
                                "trakt" -> R.drawable.ic_trakt
                                "tmdb" -> R.drawable.ic_tmdb
                                "letterboxd" -> R.drawable.ic_letterboxd
                                "popcorn" ->
                                    if (rawValue >= 60.0) R.drawable.ic_rt_fresh_popcorn
                                    else R.drawable.ic_rt_stale_popcorn
                                "metacritic" ->
                                    when {
                                        rawValue >= 75.0 -> R.drawable.ic_metacritic_green
                                        rawValue >= 50.0 -> R.drawable.ic_metacritic_yellow
                                        else -> R.drawable.ic_metacritic_red
                                    }
                                "metacriticuser" ->
                                    when {
                                        rawValue >= 75.0 -> R.drawable.ic_metacritic_user_green
                                        rawValue >= 50.0 -> R.drawable.ic_metacritic_user_yellow
                                        else -> R.drawable.ic_metacritic_user_red
                                    }
                                "rogerebert" -> R.drawable.ic_ebert
                                "myanimelist" -> R.drawable.ic_mal
                                else -> null
                            }
                        val dynamicSubtext =
                            when (sourceLower) {
                                "popcorn" -> if (rawValue >= 60.0) "Hot" else "Stale"
                                "metacritic" -> "/ 100"
                                "metacriticuser" -> "/ 10"
                                "letterboxd" -> "/ 5"
                                "rogerebert" -> "/ 4"
                                "myanimelist" -> "/ 10"
                                "trakt",
                                "tmdb" -> "Score"
                                else -> "/ 10"
                            }

                        Scorecard(
                            sourceName = rating.source.replaceFirstChar { it.uppercase() },
                            iconRes = iconRes,
                            score = if (isPercentage) "$formattedScore%" else formattedScore,
                            subtext = dynamicSubtext,
                        )
                    }
                }
            }
        }
        if (hasReviews) {
            ReviewsSection(reviews = tmdbReviews)
        }
    }
}

@Composable
private fun BadgeCard(sourceName: String, iconRes: Int, label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier.width(132.dp).height(96.dp),
    ) {
        val parts = label.split(" ", limit = 2)
        val prefixText = if (parts.size > 1) parts[0].uppercase() else ""
        val emphasisText = if (parts.size > 1) parts[1].uppercase() else label.uppercase()

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = sourceName,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = sourceName,
                    style =
                        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (prefixText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = prefixText,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text = emphasisText,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Scorecard(sourceName: String, iconRes: Int?, score: String, subtext: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier.width(132.dp).height(96.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = sourceName,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = sourceName,
                    style =
                        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = score,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}
