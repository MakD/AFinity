package com.makd.afinity.data.models.media

data class AfinityRecommendationCategory(
    val title: String,
    val items: List<AfinityItem>,
    val recommendationType: RecommendationType,
    val baselineItemName: String?
)

enum class RecommendationType {
    SIMILAR_TO_RECENTLY_PLAYED,
    SIMILAR_TO_LIKED_ITEM,
    HAS_DIRECTOR_FROM_RECENTLY_PLAYED,
    HAS_ACTOR_FROM_RECENTLY_PLAYED,
    HAS_LIKED_DIRECTOR,
    HAS_LIKED_ACTOR,
    RECOMMENDED_FOR_YOU
}

fun org.jellyfin.sdk.model.api.RecommendationType.toAfinityRecommendationType(): RecommendationType {
    return when (this) {
        org.jellyfin.sdk.model.api.RecommendationType.SIMILAR_TO_RECENTLY_PLAYED -> RecommendationType.SIMILAR_TO_RECENTLY_PLAYED
        org.jellyfin.sdk.model.api.RecommendationType.SIMILAR_TO_LIKED_ITEM -> RecommendationType.SIMILAR_TO_LIKED_ITEM
        org.jellyfin.sdk.model.api.RecommendationType.HAS_DIRECTOR_FROM_RECENTLY_PLAYED -> RecommendationType.HAS_DIRECTOR_FROM_RECENTLY_PLAYED
        org.jellyfin.sdk.model.api.RecommendationType.HAS_ACTOR_FROM_RECENTLY_PLAYED -> RecommendationType.HAS_ACTOR_FROM_RECENTLY_PLAYED
        org.jellyfin.sdk.model.api.RecommendationType.HAS_LIKED_DIRECTOR -> RecommendationType.HAS_LIKED_DIRECTOR
        org.jellyfin.sdk.model.api.RecommendationType.HAS_LIKED_ACTOR -> RecommendationType.HAS_LIKED_ACTOR
    }
}

fun RecommendationType.buildTitle(baselineItemName: String?): String {
    return when (this) {
        RecommendationType.SIMILAR_TO_RECENTLY_PLAYED ->
            baselineItemName?.let { "Because you watched $it" } ?: "Similar to Recently Watched"
        RecommendationType.SIMILAR_TO_LIKED_ITEM ->
            baselineItemName?.let { "Because you liked $it" } ?: "Similar to Liked Items"
        RecommendationType.HAS_ACTOR_FROM_RECENTLY_PLAYED, RecommendationType.HAS_LIKED_ACTOR ->
            baselineItemName?.let { "Starring $it" } ?: "Featuring Actors You Like"
        RecommendationType.HAS_DIRECTOR_FROM_RECENTLY_PLAYED, RecommendationType.HAS_LIKED_DIRECTOR ->
            baselineItemName?.let { "Directed by $it" } ?: "From Directors You Like"
        RecommendationType.RECOMMENDED_FOR_YOU -> "Recommended for You"
    }
}