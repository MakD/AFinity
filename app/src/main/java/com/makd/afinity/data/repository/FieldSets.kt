package com.makd.afinity.data.repository

import org.jellyfin.sdk.model.api.ItemFields

object FieldSets {

    val HERO_CAROUSEL =
        listOf(
            ItemFields.GENRES,
            ItemFields.OVERVIEW,
            ItemFields.REMOTE_TRAILERS,
            ItemFields.CHILD_COUNT,
        )

    val MEDIA_ITEM_CARDS =
        listOf(
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ItemFields.CHILD_COUNT,
            ItemFields.RECURSIVE_ITEM_COUNT,
            ItemFields.DATE_CREATED,
            ItemFields.DATE_LAST_MEDIA_ADDED,
        )

    val CONTINUE_WATCHING = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)

    val NEXT_UP = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)

    val LIBRARY_GRID =
        listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.RECURSIVE_ITEM_COUNT)

    val SEARCH_RESULTS =
        listOf(
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ItemFields.OVERVIEW,
            ItemFields.RECURSIVE_ITEM_COUNT,
        )

    val ITEM_DETAIL =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.PEOPLE,
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ItemFields.MEDIA_SOURCES,
            ItemFields.MEDIA_STREAMS,
            ItemFields.TRICKPLAY,
            ItemFields.CHAPTERS,
            ItemFields.EXTERNAL_URLS,
            ItemFields.TAGLINES,
            ItemFields.PROVIDER_IDS,
            ItemFields.STUDIOS,
            ItemFields.DATE_CREATED,
            ItemFields.CHILD_COUNT,
            ItemFields.RECURSIVE_ITEM_COUNT,
            ItemFields.PRODUCTION_LOCATIONS,
            ItemFields.REMOTE_TRAILERS,
        )

    val SIMILAR_ITEMS = MEDIA_ITEM_CARDS

    val PLAYER =
        listOf(
            ItemFields.MEDIA_SOURCES,
            ItemFields.MEDIA_STREAMS,
            ItemFields.TRICKPLAY,
            ItemFields.CHAPTERS,
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        )

    val EPISODE_LIST = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)

    val SEASON_DETAIL =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ItemFields.TAGLINES,
            ItemFields.EXTERNAL_URLS,
            ItemFields.PEOPLE,
        )

    val PERSON_DETAIL =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ItemFields.EXTERNAL_URLS,
            ItemFields.PRODUCTION_LOCATIONS,
        )

    val REFRESH_USER_DATA = emptyList<ItemFields>()

    val CACHE_CONTINUE_WATCHING =
        listOf(ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)

    val CACHE_LATEST_MEDIA =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ItemFields.CHILD_COUNT,
            ItemFields.RECURSIVE_ITEM_COUNT,
        )

    val CACHE_NEXT_UP =
        listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.AIR_TIME)
}
