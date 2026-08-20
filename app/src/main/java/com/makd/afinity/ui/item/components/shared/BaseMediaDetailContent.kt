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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.makd.afinity.data.models.wikidata.WikidataAwards
import com.makd.afinity.navigation.LocalShowAwards
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.ui.components.ratings.communityRatingOf
import com.makd.afinity.ui.components.ratings.criticRatingOf
import com.makd.afinity.ui.components.ratings.displayPriority
import com.makd.afinity.ui.components.ratings.excludingSupersededBy
import com.makd.afinity.ui.components.ratings.toDisplay
import java.util.UUID

@Composable
fun BaseMediaDetailContent(
    item: AfinityItem,
    specialFeatures: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    tmdbReviews: List<TmdbReview>,
    mdbRatings: List<MdbListRating> = emptyList(),
    mdbRatingBadges: MdbListRatingBadges = MdbListRatingBadges(),
    omdbAwards: String? = null,
    wikidataAwards: WikidataAwards? = null,
    isRatingsFromCache: Boolean = false,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    onBoxSetClick: (AfinityBoxSet) -> Unit,
    onPersonClick: (UUID) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    selectedSourceId: String? = null,
    typeSpecificContent: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TaglineSection(item = item)
        OverviewSection(item = item)
        ExternalLinksSection(item = item)

        DirectorSection(item = item)
        WriterSection(item = item)
        ProducerSection(item = item)

        MediaLanguageFlagsSection(item = item, selectedSourceId = selectedSourceId)

        typeSpecificContent()

        CastSection(item = item, onPersonClick = onPersonClick, widthSizeClass = widthSizeClass)

        GuestStarSection(
            item = item,
            onPersonClick = onPersonClick,
            widthSizeClass = widthSizeClass,
        )

        if (LocalShowAwards.current) {
            val omdbHeadline = omdbAwardsHeadline(omdbAwards)
            val headline =
                if (omdbHeadline == null && wikidataAwards != null) {
                    derivedAwardsHeadline(wikidataAwards)
                } else {
                    omdbHeadline
                }

            if (headline != null) {
                AwardBanner(headline = headline, isFromCache = isRatingsFromCache)
            }

            if (wikidataAwards != null) {
                WikidataAwardsSection(
                    awards = wikidataAwards,
                    style = AwardsSectionStyle.COLLAPSED_BAR,
                )
            }
        }

        if (LocalShowRatings.current) {
            RatingsAndReviews(
                item = item,
                mdbRatings = mdbRatings,
                mdbRatingBadges = mdbRatingBadges,
                tmdbReviews = tmdbReviews,
                isRatingsFromCache = isRatingsFromCache,
            )
        }

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

    val orderedRatings =
        listOfNotNull(communityRatingOf(communityRating), criticRatingOf(criticRating)) +
            mdbRatings.excludingSupersededBy(criticRating).sortedBy { it.displayPriority() }

    val hasRatings = mdbRatingBadges.hasAny || orderedRatings.isNotEmpty()
    val hasReviews = tmdbReviews.isNotEmpty()

    if (!hasRatings && !hasReviews) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (hasRatings) {
            Text(
                text = stringResource(R.string.section_ratings),
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
                                label = stringResource(R.string.rt_certified_fresh),
                            )
                        }
                    }

                    if (mdbRatingBadges.verifiedHot) {
                        item {
                            BadgeCard(
                                sourceName = "Popcornmeter",
                                iconRes = R.drawable.ic_verified_hot,
                                label = stringResource(R.string.rt_verified_hot),
                            )
                        }
                    }

                    items(orderedRatings) { rating ->
                        val display = rating.toDisplay() ?: return@items

                        Scorecard(
                            sourceName = display.sourceName,
                            iconRes = display.iconRes,
                            score = display.score,
                            subtext = display.subtext,
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
private fun AwardBanner(headline: String, isFromCache: Boolean) {
    val mainHighlight = headline
    val goldAccent = AwardGold

    AnimatedVisibility(
        visible = true,
        enter = if (isFromCache) EnterTransition.None else fadeIn(tween(500)),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_laurel),
                contentDescription = null,
                tint = goldAccent,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = mainHighlight.uppercase(),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    ),
                color = goldAccent,
                modifier = Modifier.padding(horizontal = 16.dp).weight(1f, fill = false),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_laurel),
                contentDescription = null,
                tint = goldAccent,
                modifier = Modifier.size(36.dp),
            )
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
