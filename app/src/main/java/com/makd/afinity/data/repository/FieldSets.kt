package com.makd.afinity.data.repository

import org.jellyfin.sdk.model.api.ItemFields

/**
 * Centralized field sets for Jellyfin API optimization.
 * Each field set is tailored to specific UI components to minimize data transfer.
 *
 * IMPORTANT: Fields like communityRating, criticRating, officialRating, productionYear,
 * runTimeTicks, name, id, and type are BASE PROPERTIES of BaseItemDto and are ALWAYS
 * returned by the API, regardless of the fields parameter. They do NOT exist in ItemFields enum.
 *
 * This file contains ONLY fields that:
 * 1. Exist in the ItemFields enum
 * 2. Are actually displayed in the UI
 * 3. Are NOT automatically included in BaseItemDto
 */
object FieldSets {

    /**
     * HERO_CAROUSEL - Home screen banner (OptimizedHeroCarousel.kt)
     *
     * Displays:
     * - Logo image OR title text (if no logo)
     * - Genres list (formatted as "Genre1 • Genre2 • Genre3")
     * - IMDB rating (communityRating - BASE PROPERTY, always returned)
     * - Backdrop/Primary images
     *
     * Fields needed: GENRES only
     * Everything else is base properties or image URLs calculated from base properties
     */
    val HERO_CAROUSEL = listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.GENRES,
        ItemFields.REMOTE_TRAILERS,
        ItemFields.CHILD_COUNT,
        ItemFields.RECURSIVE_ITEM_COUNT,
        ItemFields.OVERVIEW,
    )

    /**
     * MEDIA_ITEM_CARDS - Grid/List item cards (MediaItemCard.kt)
     *
     * Displays:
     * - Primary/Backdrop image
     * - Item name (BASE PROPERTY)
     * - Production year (BASE PROPERTY)
     * - Community rating - IMDB (BASE PROPERTY)
     * - Critic rating - Rotten Tomatoes (BASE PROPERTY, movies only)
     * - Episode count badge for TV shows
     *
     * Fields needed: PRIMARY_IMAGE_ASPECT_RATIO only
     * All metadata shown (year, ratings) are base properties
     */
    val MEDIA_ITEM_CARDS = listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.CHILD_COUNT,
        ItemFields.RECURSIVE_ITEM_COUNT,
        ItemFields.DATE_CREATED,
        ItemFields.DATE_LAST_MEDIA_ADDED
    )

    /**
     * CONTINUE_WATCHING - Resume playback carousel (ContinueWatchingCard.kt)
     *
     * Displays:
     * - Thumbnail image (thumb/backdrop/primary)
     * - Progress bar (calculated from playbackPositionTicks / runTimeTicks - BASE PROPERTIES)
     * - Item name or Series info (BASE PROPERTIES)
     * - Production year (BASE PROPERTY)
     * - Community/Critic ratings (BASE PROPERTIES)
     *
     * Fields needed: TRICKPLAY for scrubbing preview
     */
    val CONTINUE_WATCHING = listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.TRICKPLAY,
        ItemFields.OVERVIEW
        )

    /**
     * NEXT_UP - Next up episodes section (HomeScreen.kt, NextUpSection.kt)
     *
     * Displays:
     * - Episode backdrop/thumbnail
     * - Episode title (BASE PROPERTY)
     * - Episode overview
     * - Series name (BASE PROPERTY)
     * - "Season X, Episode Y" (BASE PROPERTIES)
     * - Air date (BASE PROPERTY)
     *
     * Fields needed: OVERVIEW, MEDIA_SOURCES (for playback)
     * Note: Includes MEDIA_SOURCES since these are likely to be played immediately
     */
    val NEXT_UP = listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
    )

    /**
     * LIBRARY_GRID - Full library browsing (LibraryContentScreen.kt)
     *
     * Displays:
     * - Poster images
     * - Item names (BASE PROPERTY)
     * - Production year (BASE PROPERTY)
     * - Community rating (BASE PROPERTY)
     * - Episode count badge for TV shows ("99 EP" or "X EP")
     *
     * Fields needed: Episode count fields for TV shows
     */
    val LIBRARY_GRID = listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.CHILD_COUNT,
        ItemFields.RECURSIVE_ITEM_COUNT
    )

    /**
     * SEARCH_RESULTS - Search results display (SearchScreen.kt)
     *
     * Displays:
     * - Primary image
     * - Item name (BASE PROPERTY)
     * - Item type and year (BASE PROPERTIES)
     * - Overview snippet (3 lines max)
     *
     * Fields needed: OVERVIEW for description
     */
    val SEARCH_RESULTS = listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.OVERVIEW
    )

    /**
     * ITEM_DETAIL - Full detail pages (ItemDetailScreen.kt, MovieDetailContent.kt, SeriesDetailContent.kt)
     *
     * Displays ALL metadata:
     * - TaglineSection: item.tagline
     * - OverviewSection: item.overview
     * - DirectorSection: item.people.filter { PersonKind.DIRECTOR }
     * - WriterSection: item.people.filter { PersonKind.WRITER }
     * - CastSection: item.people.filter { PersonKind.ACTOR }
     * - ExternalLinksSection: item.externalUrls (IMDB, TMDB, etc.)
     * - ChaptersSection: item.chapters
     * - MetadataRow: studios, production locations, ratings, year, runtime
     * - VideoQualitySelection: media sources and streams
     *
     * Fields needed: Comprehensive metadata for detail view
     */
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

    /**
     * SIMILAR_ITEMS - Similar items recommendations section
     *
     * Displays: Same as MEDIA_ITEM_CARDS (just images and names)
     */
    val SIMILAR_ITEMS = MEDIA_ITEM_CARDS

    /**
     * PLAYER - Video player interface
     *
     * Needs:
     * - Media sources for playback URL generation
     * - Media streams for audio/subtitle track selection
     * - Trickplay for scrubbing preview
     * - Chapters for chapter navigation
     * - Image aspect ratio for poster display
     */
    val PLAYER = listOf(
        ItemFields.MEDIA_SOURCES,
        ItemFields.MEDIA_STREAMS,
        ItemFields.TRICKPLAY,
        ItemFields.CHAPTERS,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
    )

    /**
     * EPISODE_LIST - Season episode listings (EpisodeListContent.kt, NextUpSection.kt)
     *
     * Displays:
     * - Episode thumbnail
     * - Episode name (BASE PROPERTY)
     * - Episode overview
     * - Runtime (BASE PROPERTY - runTimeTicks)
     * - Community rating (BASE PROPERTY)
     *
     * Fields needed: OVERVIEW only
     */
    val EPISODE_LIST = listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
    )

    /**
     * SEASON_DETAIL - Season detail pages (EpisodeListContent.kt - SeasonDetailsSection)
     *
     * Displays:
     * - Season poster
     * - Season overview
     * - Episode count ("X Episodes")
     * - Season name (BASE PROPERTY)
     * - Production year (BASE PROPERTY)
     *
     * Fields needed: OVERVIEW, CHILD_COUNT
     */
    val SEASON_DETAIL = listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.CHILD_COUNT
    )

    /**
     * PERSON_DETAIL - Actor/Director detail pages (PersonDetailContent.kt)
     *
     * Displays:
     * - Person image
     * - Person name (BASE PROPERTY)
     * - Biography (overview)
     * - Filmography (fetched separately)
     *
     * Fields needed: OVERVIEW only (for biography)
     */
    val PERSON_DETAIL = listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
    )

    /**
     * REFRESH_USER_DATA - Update single item's user data (playback progress, favorites)
     * Used by: invalidateItemCache, refreshItemUserData methods
     *
     * Needs: All metadata that might change during playback or user interaction
     * This is used to refresh a single item's data after playback or favorite toggle
     */
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

    /**
     * CACHE_CONTINUE_WATCHING - Full refresh of continue watching cache
     * Includes extra metadata for comprehensive caching
     */
    val CACHE_CONTINUE_WATCHING = listOf(
        ItemFields.OVERVIEW,
        ItemFields.GENRES,
        ItemFields.PEOPLE,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.MEDIA_SOURCES,
        ItemFields.MEDIA_STREAMS,
        ItemFields.TRICKPLAY
    )

    /**
     * CACHE_LATEST_MEDIA - Full refresh of latest media cache
     * Includes extra metadata for comprehensive caching
     */
    val CACHE_LATEST_MEDIA = listOf(
        ItemFields.OVERVIEW,
        ItemFields.GENRES,
        ItemFields.PEOPLE,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.CHILD_COUNT,
        ItemFields.RECURSIVE_ITEM_COUNT
    )
    /**
     * CACHE_NEXT_UP - Full refresh of next up cache
     * Includes extra metadata for comprehensive caching
     */
    val CACHE_NEXT_UP = listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
    )
}