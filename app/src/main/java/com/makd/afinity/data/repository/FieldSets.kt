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
            ItemFields.CHILD_COUNT,
            ItemFields.RECURSIVE_ITEM_COUNT,
            ItemFields.DATE_CREATED,
        )

    val CONTINUE_WATCHING = emptyList<ItemFields>()

    val NEXT_UP = emptyList<ItemFields>()

    val LIBRARY_GRID = listOf(ItemFields.RECURSIVE_ITEM_COUNT)

    val SEARCH_RESULTS =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.RECURSIVE_ITEM_COUNT,
        )

    val PLAYABLE_EPISODE = listOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS)

    val ITEM_DETAIL =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.PEOPLE,
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

    val EPISODE_LIST = listOf(ItemFields.OVERVIEW)

    val SEASON_DETAIL =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.TAGLINES,
            ItemFields.EXTERNAL_URLS,
            ItemFields.PEOPLE,
        )

    val PERSON_DETAIL =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.EXTERNAL_URLS,
            ItemFields.PRODUCTION_LOCATIONS,
        )

    val REFRESH_USER_DATA = emptyList<ItemFields>()

    val MINIMAL = listOf(ItemFields.CHILD_COUNT, ItemFields.RECURSIVE_ITEM_COUNT)

    val CACHE_CONTINUE_WATCHING = listOf(ItemFields.OVERVIEW, ItemFields.GENRES)

    val CACHE_LATEST_MEDIA =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.CHILD_COUNT,
            ItemFields.RECURSIVE_ITEM_COUNT,
        )

    val CACHE_NEXT_UP = listOf(ItemFields.OVERVIEW, ItemFields.AIR_TIME)

    val MUSIC_TRACK = emptyList<ItemFields>()

    val MUSIC_ALBUM =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.CHILD_COUNT,
        )

    val MUSIC_ARTIST =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.CHILD_COUNT,
        )

    val MUSIC_PLAYLIST =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.CHILD_COUNT,
        )

    val MUSIC_SEARCH = listOf(ItemFields.CHILD_COUNT)
}