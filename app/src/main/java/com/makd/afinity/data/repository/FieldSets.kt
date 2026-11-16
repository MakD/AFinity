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
            ItemFields.PARENT_ID,
        )

    val CONTINUE_WATCHING = emptyList<ItemFields>()

    val SEASON_CARDS = emptyList<ItemFields>()

    val NEXT_UP = emptyList<ItemFields>()

    val LIBRARY_GRID = listOf(ItemFields.RECURSIVE_ITEM_COUNT)

    val PLAYLIST_GRID = listOf(ItemFields.CHILD_COUNT)

    val BOOK_GRID =
        listOf(
            ItemFields.PATH,
            ItemFields.CAN_DOWNLOAD,
            ItemFields.DATE_CREATED,
            ItemFields.PARENT_ID,
        )

    val BOOK_DETAIL =
        listOf(
            ItemFields.PARENT_ID,
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.PEOPLE,
            ItemFields.PATH,
            ItemFields.CAN_DOWNLOAD,
            ItemFields.STUDIOS,
            ItemFields.TAGS,
            ItemFields.EXTERNAL_URLS,
            ItemFields.PROVIDER_IDS,
            ItemFields.DATE_CREATED,
        )

    val AUDIOBOOK_GRID =
        listOf(
            ItemFields.PARENT_ID,
            ItemFields.PEOPLE,
            ItemFields.CAN_DOWNLOAD,
            ItemFields.DATE_CREATED,
        )

    val AUDIOBOOK_DETAIL =
        listOf(
            ItemFields.PARENT_ID,
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.PEOPLE,
            ItemFields.CHAPTERS,
            ItemFields.CAN_DOWNLOAD,
            ItemFields.STUDIOS,
            ItemFields.EXTERNAL_URLS,
            ItemFields.PROVIDER_IDS,
            ItemFields.DATE_CREATED,
            ItemFields.MEDIA_SOURCES,
        )

    val SEARCH_RESULTS =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.RECURSIVE_ITEM_COUNT,
            ItemFields.PARENT_ID,
        )

    val PLAYABLE_EPISODE = listOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS)

    val MEDIA_INFO = listOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS)

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
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        )

    val PHOTO_GRID =
        listOf(
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ItemFields.WIDTH,
            ItemFields.HEIGHT,
            ItemFields.PARENT_ID,
            ItemFields.DATE_CREATED,
            ItemFields.CHILD_COUNT,
            ItemFields.CAN_DOWNLOAD,
        )

    val PHOTO_DETAIL = PHOTO_GRID + listOf(ItemFields.OVERVIEW, ItemFields.TAGS, ItemFields.GENRES)

    val MUSIC_VIDEO_GRID =
        listOf(
            ItemFields.PARENT_ID,
            ItemFields.DATE_CREATED,
            ItemFields.MEDIA_SOURCE_COUNT,
            ItemFields.GENRES,
        )

    val MUSIC_VIDEO_DETAIL =
        ITEM_DETAIL +
            listOf(
                ItemFields.PARENT_ID,
                ItemFields.TAGS,
                ItemFields.MEDIA_SOURCE_COUNT,
                ItemFields.CAN_DOWNLOAD,
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
            ItemFields.PROVIDER_IDS,
        )

    val REFRESH_USER_DATA = emptyList<ItemFields>()

    val MINIMAL = listOf(ItemFields.CHILD_COUNT, ItemFields.RECURSIVE_ITEM_COUNT)

    val CACHE_CONTINUE_WATCHING = emptyList<ItemFields>()

    val CACHE_NEXT_UP = emptyList<ItemFields>()

    val MUSIC_TRACK = emptyList<ItemFields>()

    val MUSIC_ALBUM =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.CHILD_COUNT,
            ItemFields.EXTERNAL_URLS,
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

    val LIVE_TV_PROGRAM_CARD =
        listOf(
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.CHANNEL_INFO,
            ItemFields.CHANNEL_IMAGE,
        )

    val LIVE_TV_PROGRAM_SCHEDULE = listOf(ItemFields.OVERVIEW)

    val LIVE_TV_GUIDE = emptyList<ItemFields>()

    val LIVE_TV_RECORDINGS =
        listOf(
            ItemFields.CAN_DELETE,
            ItemFields.DATE_CREATED,
            ItemFields.OVERVIEW,
        )
}
