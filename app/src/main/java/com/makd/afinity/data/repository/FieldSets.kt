package com.makd.afinity.data.repository

import org.jellyfin.sdk.model.api.ItemFields

object FieldSets {

    val HERO_CAROUSEL = listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.GENRES,
        ItemFields.REMOTE_TRAILERS,
        ItemFields.CHILD_COUNT,
        ItemFields.RECURSIVE_ITEM_COUNT,
        ItemFields.OVERVIEW,
    )

    val MEDIA_ITEM_CARDS = listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.CHILD_COUNT,
        ItemFields.RECURSIVE_ITEM_COUNT,
        ItemFields.DATE_CREATED,
        ItemFields.DATE_LAST_MEDIA_ADDED
    )

    val CONTINUE_WATCHING = listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.TRICKPLAY,
        ItemFields.OVERVIEW,
        ItemFields.MEDIA_SOURCES,
        ItemFields.MEDIA_STREAMS
        )

    val NEXT_UP = listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.MEDIA_SOURCES,
        ItemFields.MEDIA_STREAMS
    )

    val LIBRARY_GRID = listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.CHILD_COUNT,
        ItemFields.RECURSIVE_ITEM_COUNT
    )

    val SEARCH_RESULTS = listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.OVERVIEW
    )

    val ITEM_DETAIL = listOf(
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
        ItemFields.REMOTE_TRAILERS
    )

    val SIMILAR_ITEMS = MEDIA_ITEM_CARDS

    val PLAYER = listOf(
        ItemFields.MEDIA_SOURCES,
        ItemFields.MEDIA_STREAMS,
        ItemFields.TRICKPLAY,
        ItemFields.CHAPTERS,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
    )

    val EPISODE_LIST = listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
    )

    val SEASON_DETAIL = listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.CHILD_COUNT
    )

    val PERSON_DETAIL = listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
    )

    val REFRESH_USER_DATA = listOf(
        ItemFields.OVERVIEW,
        ItemFields.GENRES,
        ItemFields.PEOPLE,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.MEDIA_SOURCES,
        ItemFields.MEDIA_STREAMS,
        ItemFields.TRICKPLAY,
        ItemFields.CHAPTERS,
        ItemFields.EXTERNAL_URLS,
        ItemFields.TAGLINES
    )

    val CACHE_CONTINUE_WATCHING = listOf(
        ItemFields.OVERVIEW,
        ItemFields.GENRES,
        ItemFields.PEOPLE,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.MEDIA_SOURCES,
        ItemFields.MEDIA_STREAMS,
        ItemFields.TRICKPLAY
    )

    val CACHE_LATEST_MEDIA = listOf(
        ItemFields.OVERVIEW,
        ItemFields.GENRES,
        ItemFields.PEOPLE,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.CHILD_COUNT,
        ItemFields.RECURSIVE_ITEM_COUNT
    )

    val CACHE_NEXT_UP = listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
    )
}