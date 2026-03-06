package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.tmdb.TmdbReview
import java.util.UUID

@Composable
fun BaseMediaDetailContent(
    item: AfinityItem,
    specialFeatures: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    tmdbReviews: List<TmdbReview>,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    onBoxSetClick: (AfinityBoxSet) -> Unit,
    onPersonClick: (UUID) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    typeSpecificContent: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TaglineSection(item = item)
        OverviewSection(item = item)
        DirectorSection(item = item)
        WriterSection(item = item)

        typeSpecificContent()

        CastSection(item = item, onPersonClick = onPersonClick, widthSizeClass = widthSizeClass)

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

        if (tmdbReviews.isNotEmpty()) {
            ReviewsSection(reviews = tmdbReviews)
        }

        ExternalLinksSection(item = item)
    }
}
