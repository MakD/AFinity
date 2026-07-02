package com.makd.afinity.data.repository.home

import android.content.Context
import com.makd.afinity.R
import com.makd.afinity.data.database.AfinityTypeConverters
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.HomeSectionContent
import com.makd.afinity.data.models.HomeSectionDescriptor
import com.makd.afinity.data.models.HomeSectionType
import com.makd.afinity.data.models.MovieSection
import com.makd.afinity.data.models.MovieSectionType
import com.makd.afinity.data.models.PersonFromMovieSection
import com.makd.afinity.data.models.PersonSectionType
import com.makd.afinity.data.models.PersonWithCount
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.withBaseUrl
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.GenreRepository
import com.makd.afinity.data.repository.PeopleRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PersonKind
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

@Singleton
class HomeSectionsRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val peopleRepository: PeopleRepository,
    private val genreRepository: GenreRepository,
    private val sessionManager: SessionManager,
    private val homeCacheRepository: HomeCacheRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val layoutTTL = 24.hours.inWholeMilliseconds
    private val recentCacheTTL = 6.hours.inWholeMilliseconds

    private val json = Json { ignoreUnknownKeys = true }
    private val converters = AfinityTypeConverters()

    private val _layout = MutableStateFlow<List<HomeSectionDescriptor>>(emptyList())
    val layout: StateFlow<List<HomeSectionDescriptor>> = _layout.asStateFlow()

    private val _content = MutableStateFlow<Map<String, HomeSectionContent>>(emptyMap())
    val content: StateFlow<Map<String, HomeSectionContent>> = _content.asStateFlow()

    private val hydrationMutex = Mutex()
    private val hydrationInFlight = mutableSetOf<String>()
    private val renderedItemIds = mutableSetOf<UUID>()
    private val spotlightSeenIds = mutableSetOf<UUID>()

    private var buildJob: Job? = null
    private var layoutSessionKey: String? = null
    private var recentWatchedCache: Pair<Long, List<AfinityMovie>>? = null

    private fun sessionKey(): String? {
        val session = sessionManager.currentSession.value ?: return null
        if (session.serverId.isBlank()) return null
        return "${session.serverId}_${session.userId}"
    }

    private fun layoutCacheKey(sessionKey: String) = "home_layout_$sessionKey"

    private fun contentCacheKey(sessionKey: String, descriptorKey: String) =
        "home_sec_${sessionKey}_$descriptorKey"

    fun ensureLayout(force: Boolean = false) {
        val sk = sessionKey() ?: return
        if (!force && sk == layoutSessionKey && _layout.value.isNotEmpty()) return
        if (!force && buildJob?.isActive == true) return

        buildJob?.cancel()
        buildJob =
            scope.launch(Dispatchers.IO) {
                try {
                    if (!force) {
                        val cachedLayout =
                            homeCacheRepository.getRaw(layoutCacheKey(sk), layoutTTL)?.let {
                                try {
                                    json.decodeFromString<List<HomeSectionDescriptor>>(it)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to decode cached home layout")
                                    null
                                }
                            }
                        if (!cachedLayout.isNullOrEmpty()) {
                            hydrationMutex.withLock {
                                renderedItemIds.clear()
                                spotlightSeenIds.clear()
                            }
                            layoutSessionKey = sk
                            _content.value = emptyMap()
                            _layout.value = reinterleave(cachedLayout)
                            Timber.d(
                                "Home layout restored from cache (${cachedLayout.size} sections)"
                            )
                            return@launch
                        }
                    }

                    val oldKeys = _layout.value.map { it.key }
                    val fresh = buildLayout()
                    if (fresh.isEmpty()) {
                        Timber.d("Home layout build produced no sections, keeping current state")
                        return@launch
                    }
                    hydrationMutex.withLock {
                        renderedItemIds.clear()
                        spotlightSeenIds.clear()
                    }
                    layoutSessionKey = sk
                    _content.value = emptyMap()
                    _layout.value = fresh
                    homeCacheRepository.putRaw(layoutCacheKey(sk), json.encodeToString(fresh))
                    oldKeys.forEach { homeCacheRepository.invalidate(contentCacheKey(sk, it)) }
                    Timber.d("Built fresh home layout (${fresh.size} sections)")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to build home layout")
                }
            }
    }

    fun hydrate(descriptorKey: String) {
        if (_content.value.containsKey(descriptorKey)) return
        scope.launch(Dispatchers.IO) {
            val descriptor = _layout.value.firstOrNull { it.key == descriptorKey } ?: return@launch
            val shouldRun = hydrationMutex.withLock {
                if (
                    descriptorKey in hydrationInFlight || _content.value.containsKey(descriptorKey)
                ) {
                    false
                } else {
                    hydrationInFlight.add(descriptorKey)
                    true
                }
            }
            if (!shouldRun) return@launch

            try {
                val content = hydrateDescriptor(descriptor)
                if (content != null) {
                    registerRenderedItems(content)
                    _content.update { it + (descriptorKey to content) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to hydrate home section $descriptorKey")
            } finally {
                hydrationMutex.withLock { hydrationInFlight.remove(descriptorKey) }
            }
        }
    }

    fun updateItem(updatedItem: AfinityItem) {
        _content.update { map ->
            var changed = false
            val patched = map.mapValues { (_, content) ->
                val newContent = patchContent(content, updatedItem)
                if (newContent !== content) changed = true
                newContent
            }
            if (changed) patched else map
        }
    }

    suspend fun clearAllData() {
        buildJob?.cancel()
        layoutSessionKey = null
        recentWatchedCache = null
        hydrationMutex.withLock {
            hydrationInFlight.clear()
            renderedItemIds.clear()
            spotlightSeenIds.clear()
        }
        _layout.value = emptyList()
        _content.value = emptyMap()
    }

    private fun patchContent(
        content: HomeSectionContent,
        updatedItem: AfinityItem,
    ): HomeSectionContent {
        fun patchItems(items: List<AfinityItem>): List<AfinityItem>? {
            if (items.none { it.id == updatedItem.id }) return null
            return items.map { if (it.id == updatedItem.id) updatedItem else it }
        }

        return when (content) {
            is HomeSectionContent.Person -> {
                patchItems(content.section.items)?.let {
                    HomeSectionContent.Person(content.section.copy(items = it))
                } ?: content
            }
            is HomeSectionContent.Movie -> {
                if (
                    updatedItem is AfinityMovie &&
                        content.section.recommendedItems.any { it.id == updatedItem.id }
                ) {
                    HomeSectionContent.Movie(
                        content.section.copy(
                            recommendedItems =
                                content.section.recommendedItems.map {
                                    if (it.id == updatedItem.id) updatedItem else it
                                }
                        )
                    )
                } else {
                    content
                }
            }
            is HomeSectionContent.PersonFromMovie -> {
                patchItems(content.section.items)?.let {
                    HomeSectionContent.PersonFromMovie(content.section.copy(items = it))
                } ?: content
            }
            is HomeSectionContent.Spotlight -> {
                patchItems(content.items)?.let { HomeSectionContent.Spotlight(it) } ?: content
            }
            HomeSectionContent.Empty -> content
        }
    }

    private suspend fun registerRenderedItems(content: HomeSectionContent) {
        val (ids, isSpotlight) =
            when (content) {
                is HomeSectionContent.Person -> content.section.items.map { it.id } to false
                is HomeSectionContent.Movie ->
                    content.section.recommendedItems.map { it.id } to false
                is HomeSectionContent.PersonFromMovie ->
                    content.section.items.map { it.id } to false
                is HomeSectionContent.Spotlight -> content.items.map { it.id } to true
                HomeSectionContent.Empty -> return
            }
        hydrationMutex.withLock {
            if (isSpotlight) spotlightSeenIds.addAll(ids) else renderedItemIds.addAll(ids)
        }
    }

    private suspend fun hydrateDescriptor(descriptor: HomeSectionDescriptor): HomeSectionContent? {
        return when (descriptor.type) {
            HomeSectionType.STARRING,
            HomeSectionType.DIRECTED_BY,
            HomeSectionType.WRITTEN_BY -> hydratePersonSection(descriptor)
            HomeSectionType.BECAUSE_YOU_WATCHED -> hydrateBecauseYouWatched(descriptor)
            HomeSectionType.ACTOR_FROM_MOVIE -> hydrateActorFromMovie(descriptor)
            HomeSectionType.SPOTLIGHT_GENRE_MOVIE,
            HomeSectionType.SPOTLIGHT_GENRE_SHOW,
            HomeSectionType.SPOTLIGHT_STUDIO,
            HomeSectionType.SPOTLIGHT_BOXSET -> hydrateSpotlight(descriptor)
            HomeSectionType.GENRE_MOVIE,
            HomeSectionType.GENRE_SHOW -> null
        }
    }

    private suspend fun hydratePersonSection(
        descriptor: HomeSectionDescriptor
    ): HomeSectionContent {
        val cachedPerson = descriptor.person ?: return HomeSectionContent.Empty
        val personWithCount = PersonWithCount.fromCached(cachedPerson, mediaRepository.getBaseUrl())
        val sectionType =
            when (descriptor.type) {
                HomeSectionType.DIRECTED_BY -> PersonSectionType.DIRECTED_BY
                HomeSectionType.WRITTEN_BY -> PersonSectionType.WRITTEN_BY
                else -> PersonSectionType.STARRING
            }
        val section =
            peopleRepository.getPersonSection(personWithCount, sectionType)
                ?: return HomeSectionContent.Empty
        return HomeSectionContent.Person(section)
    }

    private suspend fun decodeReferenceMovie(movieJson: String?): AfinityMovie? {
        val movie = movieJson?.let { converters.toAfinityMovie(it) } ?: return null
        return movie.copy(images = movie.images.withBaseUrl(mediaRepository.getBaseUrl()))
    }

    private suspend fun hydrateBecauseYouWatched(
        descriptor: HomeSectionDescriptor
    ): HomeSectionContent {
        val referenceMovie =
            decodeReferenceMovie(descriptor.referenceMovieJson) ?: return HomeSectionContent.Empty

        val sk = sessionKey() ?: return HomeSectionContent.Empty
        val cacheKey = contentCacheKey(sk, descriptor.key)
        val cachedItems =
            homeCacheRepository
                .getItems(cacheKey, mediaRepository.getBaseUrl())
                ?.filterIsInstance<AfinityMovie>()
        if (!cachedItems.isNullOrEmpty()) {
            return HomeSectionContent.Movie(
                MovieSection(
                    referenceMovie = referenceMovie,
                    recommendedItems = cachedItems,
                    sectionType = MovieSectionType.BECAUSE_YOU_WATCHED,
                )
            )
        }

        val excluded = hydrationMutex.withLock { renderedItemIds.toSet() }
        val similarMovies =
            mediaRepository
                .getSimilarMovies(movieId = referenceMovie.id, limit = 32)
                .filterNot { it.id in excluded }
                .shuffled()
                .take(20)
        if (similarMovies.size < 5) return HomeSectionContent.Empty

        homeCacheRepository.putItems(cacheKey, similarMovies)
        return HomeSectionContent.Movie(
            MovieSection(
                referenceMovie = referenceMovie,
                recommendedItems = similarMovies,
                sectionType = MovieSectionType.BECAUSE_YOU_WATCHED,
            )
        )
    }

    private suspend fun hydrateActorFromMovie(
        descriptor: HomeSectionDescriptor
    ): HomeSectionContent {
        val cachedPerson = descriptor.person ?: return HomeSectionContent.Empty
        val person = PersonWithCount.fromCached(cachedPerson, mediaRepository.getBaseUrl()).person
        val referenceMovie =
            decodeReferenceMovie(descriptor.referenceMovieJson) ?: return HomeSectionContent.Empty

        val sk = sessionKey() ?: return HomeSectionContent.Empty
        val cacheKey = contentCacheKey(sk, descriptor.key)
        val cachedItems = homeCacheRepository.getItems(cacheKey, mediaRepository.getBaseUrl())
        if (!cachedItems.isNullOrEmpty()) {
            return HomeSectionContent.PersonFromMovie(
                PersonFromMovieSection(
                    person = person,
                    referenceMovie = referenceMovie,
                    items = cachedItems,
                )
            )
        }

        val excluded = hydrationMutex.withLock { renderedItemIds.toSet() }
        val actorMovies =
            mediaRepository
                .getPersonItems(
                    personId = person.id,
                    includeItemTypes = listOf("MOVIE"),
                    fields = listOf(ItemFields.PEOPLE),
                )
                .filterIsInstance<AfinityMovie>()
                .filter { movie ->
                    movie.people.any { it.id == person.id && it.type == PersonKind.ACTOR }
                }
                .filterNot { it.id == referenceMovie.id || it.id in excluded }
                .shuffled()
                .take(20)
        if (actorMovies.size < 5) return HomeSectionContent.Empty

        homeCacheRepository.putItems(cacheKey, actorMovies)
        return HomeSectionContent.PersonFromMovie(
            PersonFromMovieSection(
                person = person,
                referenceMovie = referenceMovie,
                items = actorMovies,
            )
        )
    }

    private suspend fun hydrateSpotlight(descriptor: HomeSectionDescriptor): HomeSectionContent {
        val sk = sessionKey() ?: return HomeSectionContent.Empty
        val cacheKey = contentCacheKey(sk, descriptor.key)
        val cachedItems = homeCacheRepository.getItems(cacheKey, mediaRepository.getBaseUrl())
        if (!cachedItems.isNullOrEmpty()) {
            return HomeSectionContent.Spotlight(cachedItems)
        }

        val rawItems =
            when (descriptor.type) {
                HomeSectionType.SPOTLIGHT_GENRE_MOVIE ->
                    mediaRepository.getTopRatedByGenre(
                        descriptor.genreName ?: return HomeSectionContent.Empty,
                        GenreType.MOVIE,
                        limit = 20,
                    )
                HomeSectionType.SPOTLIGHT_GENRE_SHOW ->
                    mediaRepository.getTopRatedByGenre(
                        descriptor.genreName ?: return HomeSectionContent.Empty,
                        GenreType.SHOW,
                        limit = 20,
                    )
                HomeSectionType.SPOTLIGHT_STUDIO ->
                    mediaRepository.getTopRatedByStudio(
                        descriptor.studioName ?: return HomeSectionContent.Empty,
                        limit = 20,
                    )
                HomeSectionType.SPOTLIGHT_BOXSET -> {
                    val boxSetId =
                        descriptor.boxSetId?.let {
                            try {
                                UUID.fromString(it)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: return HomeSectionContent.Empty
                    val baseUrl = mediaRepository.getBaseUrl()
                    mediaRepository
                        .getItems(
                            parentId = boxSetId,
                            sortBy = SortBy.RELEASE_DATE,
                            fields = FieldSets.MEDIA_ITEM_CARDS,
                        )
                        .items
                        ?.mapNotNull { it.toAfinityItem(baseUrl) } ?: emptyList()
                }
                else -> return HomeSectionContent.Empty
            }

        val excluded = hydrationMutex.withLock { spotlightSeenIds.toSet() }
        val items = rawItems.filterNot { it.id in excluded }.take(10)
        if (items.size < 3) return HomeSectionContent.Empty

        homeCacheRepository.putItems(cacheKey, items)
        return HomeSectionContent.Spotlight(items)
    }

    private suspend fun buildLayout(): List<HomeSectionDescriptor> = coroutineScope {
        val actorsDeferred = async {
            peopleRepository.getTopPeople(PersonKind.ACTOR, limit = 75, minAppearances = 5)
        }
        val directorsDeferred = async {
            peopleRepository.getTopPeople(PersonKind.DIRECTOR, limit = 75, minAppearances = 5)
        }
        val writersDeferred = async {
            peopleRepository.getTopPeople(PersonKind.WRITER, limit = 50, minAppearances = 3)
        }
        val studiosDeferred = async {
            try {
                mediaRepository.getStudios(limit = 50)
            } catch (e: Exception) {
                Timber.w(e, "Failed to load studios for spotlight descriptors")
                emptyList()
            }
        }
        val boxSetsDeferred = async {
            try {
                mediaRepository
                    .getItems(
                        includeItemTypes = listOf("BOX_SET"),
                        fields = FieldSets.MEDIA_ITEM_CARDS,
                    )
                    .items
                    ?.filter { (it.childCount ?: 0) >= 3 && it.name != null } ?: emptyList()
            } catch (e: Exception) {
                Timber.w(e, "Failed to load boxsets for spotlight descriptors")
                emptyList()
            }
        }

        val usedPeopleNames = mutableSetOf<String>()

        fun personDescriptors(
            people: List<PersonWithCount>,
            type: HomeSectionType,
            max: Int,
            titleRes: Int,
        ): List<HomeSectionDescriptor> =
            people
                .filterNot { it.person.name in usedPeopleNames }
                .shuffled()
                .take(max)
                .map { personWithCount ->
                    usedPeopleNames.add(personWithCount.person.name)
                    HomeSectionDescriptor(
                        key = "person_${type.name}_${personWithCount.person.id}",
                        type = type,
                        title = context.getString(titleRes, personWithCount.person.name),
                        person = personWithCount.toCached(),
                    )
                }

        val actorDescriptors =
            personDescriptors(
                actorsDeferred.await(),
                HomeSectionType.STARRING,
                max = 15,
                titleRes = R.string.home_person_starring,
            )
        val directorDescriptors =
            personDescriptors(
                directorsDeferred.await(),
                HomeSectionType.DIRECTED_BY,
                max = 8,
                titleRes = R.string.home_person_directed_by,
            )
        val writerDescriptors =
            personDescriptors(
                writersDeferred.await(),
                HomeSectionType.WRITTEN_BY,
                max = 7,
                titleRes = R.string.home_person_written_by,
            )

        val becauseYouWatchedDescriptors = mutableListOf<HomeSectionDescriptor>()
        val usedReferenceMovies = mutableSetOf<UUID>()
        while (becauseYouWatchedDescriptors.size < 7) {
            val referenceMovie = getRandomRecentlyWatchedMovie(usedReferenceMovies) ?: break
            usedReferenceMovies.add(referenceMovie.id)
            val movieJson = converters.fromAfinityMovie(referenceMovie) ?: continue
            becauseYouWatchedDescriptors.add(
                HomeSectionDescriptor(
                    key = "byw_${referenceMovie.id}",
                    type = HomeSectionType.BECAUSE_YOU_WATCHED,
                    title =
                        context.getString(R.string.home_because_you_watched, referenceMovie.name),
                    referenceMovieJson = movieJson,
                )
            )
        }

        val actorFromMovieDescriptors = mutableListOf<HomeSectionDescriptor>()
        val usedActorFromMovies = mutableSetOf<UUID>()
        while (actorFromMovieDescriptors.size < 3) {
            val randomMovie = getRandomRecentlyWatchedMovie(usedActorFromMovies) ?: break
            usedActorFromMovies.add(randomMovie.id)

            val movieWithPeople =
                try {
                    mediaRepository
                        .getItem(itemId = randomMovie.id, fields = listOf(ItemFields.PEOPLE))
                        ?.toAfinityMovie(mediaRepository.getBaseUrl())
                } catch (e: Exception) {
                    null
                } ?: continue

            val availableActors =
                movieWithPeople.people
                    .filter { it.type == PersonKind.ACTOR }
                    .filterNot { it.name in usedPeopleNames }
            val selectedActor = availableActors.take(3).randomOrNull() ?: continue
            usedPeopleNames.add(selectedActor.name)

            val movieJson = converters.fromAfinityMovie(movieWithPeople) ?: continue
            actorFromMovieDescriptors.add(
                HomeSectionDescriptor(
                    key = "actorfrom_${selectedActor.id}_${randomMovie.id}",
                    type = HomeSectionType.ACTOR_FROM_MOVIE,
                    title =
                        context.getString(
                            R.string.home_starring_from_watched,
                            selectedActor.name,
                            randomMovie.name,
                        ),
                    person = PersonWithCount(selectedActor, 0).toCached(),
                    referenceMovieJson = movieJson,
                )
            )
        }

        val genres = genreRepository.combinedGenres.value
        val spotlightDescriptors = buildList {
            genres
                .filter { it.type == GenreType.MOVIE }
                .shuffled()
                .take(7)
                .forEach { genre ->
                    add(
                        HomeSectionDescriptor(
                            key = "spot_genre_movie_${genre.name}",
                            type = HomeSectionType.SPOTLIGHT_GENRE_MOVIE,
                            title =
                                context.getString(R.string.home_genre_top_movies_fmt, genre.name),
                            genreName = genre.name,
                        )
                    )
                }
            genres
                .filter { it.type == GenreType.SHOW }
                .shuffled()
                .take(7)
                .forEach { genre ->
                    add(
                        HomeSectionDescriptor(
                            key = "spot_genre_show_${genre.name}",
                            type = HomeSectionType.SPOTLIGHT_GENRE_SHOW,
                            title =
                                context.getString(R.string.home_genre_top_series_fmt, genre.name),
                            genreName = genre.name,
                        )
                    )
                }
            studiosDeferred.await().shuffled().take(10).forEach { studio ->
                add(
                    HomeSectionDescriptor(
                        key = "spot_studio_${studio.name}",
                        type = HomeSectionType.SPOTLIGHT_STUDIO,
                        title = context.getString(R.string.home_best_of_studio_fmt, studio.name),
                        studioName = studio.name,
                    )
                )
            }
            boxSetsDeferred.await().shuffled().take(8).forEach { boxSet ->
                add(
                    HomeSectionDescriptor(
                        key = "spot_boxset_${boxSet.id}",
                        type = HomeSectionType.SPOTLIGHT_BOXSET,
                        title = boxSet.name.orEmpty(),
                        boxSetId = boxSet.id.toString(),
                    )
                )
            }
        }
        val spotlights = spotlightDescriptors.shuffled().take(20)

        val recommendationDescriptors =
            (actorDescriptors + directorDescriptors).shuffled() +
                (writerDescriptors + becauseYouWatchedDescriptors).shuffled() +
                actorFromMovieDescriptors

        val genreDescriptors =
            genres.shuffled().map { genre ->
                HomeSectionDescriptor(
                    key = "genre_${genre.type.name.lowercase()}_${genre.name}",
                    type =
                        if (genre.type == GenreType.MOVIE) HomeSectionType.GENRE_MOVIE
                        else HomeSectionType.GENRE_SHOW,
                    title = genre.name,
                    genreName = genre.name,
                )
            }

        interleave(genreDescriptors, recommendationDescriptors, spotlights)
    }

    private fun reinterleave(
        descriptors: List<HomeSectionDescriptor>
    ): List<HomeSectionDescriptor> {
        val genres =
            descriptors
                .filter {
                    it.type == HomeSectionType.GENRE_MOVIE || it.type == HomeSectionType.GENRE_SHOW
                }
                .shuffled()
        val spotlights =
            descriptors
                .filter {
                    it.type == HomeSectionType.SPOTLIGHT_GENRE_MOVIE ||
                        it.type == HomeSectionType.SPOTLIGHT_GENRE_SHOW ||
                        it.type == HomeSectionType.SPOTLIGHT_STUDIO ||
                        it.type == HomeSectionType.SPOTLIGHT_BOXSET
                }
                .shuffled()
        val recommendations =
            descriptors
                .filter {
                    it.type == HomeSectionType.STARRING ||
                        it.type == HomeSectionType.DIRECTED_BY ||
                        it.type == HomeSectionType.WRITTEN_BY ||
                        it.type == HomeSectionType.BECAUSE_YOU_WATCHED ||
                        it.type == HomeSectionType.ACTOR_FROM_MOVIE
                }
                .shuffled()

        return interleave(genres, recommendations, spotlights)
    }

    private fun interleave(
        genres: List<HomeSectionDescriptor>,
        recommendations: List<HomeSectionDescriptor>,
        spotlights: List<HomeSectionDescriptor>,
    ): List<HomeSectionDescriptor> {
        val finalLayout = mutableListOf<HomeSectionDescriptor>()
        val genreIterator = genres.iterator()
        val recIterator = recommendations.iterator()
        var counter = 0
        while (genreIterator.hasNext()) {
            finalLayout.add(genreIterator.next())
            counter++
            if (counter % 2 == 0 && recIterator.hasNext()) {
                finalLayout.add(recIterator.next())
            }
        }
        while (recIterator.hasNext()) {
            finalLayout.add(recIterator.next())
        }

        if (spotlights.isNotEmpty()) {
            val positions = computeSpotlightPositions(finalLayout.size, spotlights.size)
            positions.sorted().forEachIndexed { offset, pos ->
                finalLayout.add((pos + offset).coerceIn(0, finalLayout.size), spotlights[offset])
            }
        }
        return finalLayout
    }

    private fun computeSpotlightPositions(listSize: Int, count: Int): List<Int> {
        if (listSize == 0 || count == 0) return emptyList()
        val chunkSize = (listSize / (count + 1)).coerceAtLeast(1)

        val rawPositions =
            (1..count)
                .map { i -> (i * chunkSize + (-2..2).random()).coerceIn(1, listSize) }
                .sorted()

        val adjustedPositions = mutableListOf<Int>()
        var lastPos = -2

        for (i in rawPositions.indices) {
            val pos = rawPositions[i]
            var newPos = if (pos <= lastPos + 1) lastPos + 2 else pos
            val itemsLeft = count - 1 - i
            val maxAllowedPos = (listSize - (itemsLeft * 2)).coerceAtLeast(0)
            newPos = newPos.coerceIn(0, maxAllowedPos)
            adjustedPositions.add(newPos)
            lastPos = newPos
        }
        return adjustedPositions
    }

    private suspend fun getRandomRecentlyWatchedMovie(excludedMovies: Set<UUID>): AfinityMovie? {
        try {
            val now = System.currentTimeMillis()
            val cached = recentWatchedCache

            val allRecentWatched =
                if (cached != null && now - cached.first < recentCacheTTL) {
                    cached.second
                } else {
                    val movies =
                        mediaRepository.getMovies(
                            sortBy = SortBy.DATE_PLAYED,
                            sortDescending = true,
                            limit = 10,
                            isPlayed = true,
                        )
                    recentWatchedCache = now to movies
                    movies
                }

            val recentWatched = allRecentWatched.filterNot { it.id in excludedMovies }

            if (recentWatched.isEmpty()) return null

            val random = Random.nextFloat()
            return if (random < 0.7f && recentWatched.size >= 5) {
                recentWatched.take(5).random()
            } else if (recentWatched.size > 5) {
                recentWatched.drop(5).take(5).randomOrNull() ?: recentWatched.random()
            } else {
                recentWatched.random()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get random recently watched movie")
            return null
        }
    }
}
