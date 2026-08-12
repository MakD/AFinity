package com.makd.afinity.data.repository.home

import android.content.Context
import com.makd.afinity.R
import com.makd.afinity.data.database.AfinityTypeConverters
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.CustomHomeSection
import com.makd.afinity.data.models.CustomSectionSourceType
import com.makd.afinity.data.models.DiscoveryConfig
import com.makd.afinity.data.models.DiscoverySection
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
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.models.media.toItemFilterCriteria
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
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
    private val customHomeSectionsRepository: CustomHomeSectionsRepository,
    private val homeLayoutPreferencesRepository: HomeLayoutPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val layoutTTL = 24.hours.inWholeMilliseconds
    private val recentCacheTTL = 6.hours.inWholeMilliseconds
    private val studiosTTL = 24.hours.inWholeMilliseconds

    private val json = Json { ignoreUnknownKeys = true }
    private val converters = AfinityTypeConverters()

    private val _layout = MutableStateFlow<List<HomeSectionDescriptor>>(emptyList())
    val layout: StateFlow<List<HomeSectionDescriptor>> = _layout.asStateFlow()

    private val _content = MutableStateFlow<Map<String, HomeSectionContent>>(emptyMap())
    val content: StateFlow<Map<String, HomeSectionContent>> = _content.asStateFlow()

    private val _pinnedLayout = MutableStateFlow<List<HomeSectionDescriptor>>(emptyList())
    val pinnedLayout: StateFlow<List<HomeSectionDescriptor>> = _pinnedLayout.asStateFlow()

    private val _watchAgain = MutableStateFlow<List<AfinityItem>>(emptyList())
    val watchAgain: StateFlow<List<AfinityItem>> = _watchAgain.asStateFlow()

    private val _watchAgainLoaded = MutableStateFlow(false)
    val watchAgainLoaded: StateFlow<Boolean> = _watchAgainLoaded.asStateFlow()

    private val _criticsChoice = MutableStateFlow<List<AfinityItem>>(emptyList())
    val criticsChoice: StateFlow<List<AfinityItem>> = _criticsChoice.asStateFlow()

    private val _criticsChoiceLoaded = MutableStateFlow(false)
    val criticsChoiceLoaded: StateFlow<Boolean> = _criticsChoiceLoaded.asStateFlow()

    private val _popularStudios = MutableStateFlow<List<AfinityStudio>>(emptyList())
    val popularStudios: StateFlow<List<AfinityStudio>> = _popularStudios.asStateFlow()

    private val _popularStudiosLoaded = MutableStateFlow(false)
    val popularStudiosLoaded: StateFlow<Boolean> = _popularStudiosLoaded.asStateFlow()

    private var criticsChoiceJob: Job? = null

    private var watchAgainJob: Job? = null

    private var popularStudiosJob: Job? = null

    private var customRefreshJob: Job? = null

    private val customSectionSignatures = mutableMapOf<String, String>()

    private val seasonRecheck = MutableStateFlow(0)

    init {
        scope.launch {
            combine(customHomeSectionsRepository.sections, seasonRecheck) { sections, _ ->
                    sections
                }
                .collect { sections -> applyCustomSections(sections) }
        }

        scope.launch {
            homeLayoutPreferencesRepository.discoveryConfig.distinctUntilChanged().drop(1).collect {
                sessionKey()?.let { sk ->
                    homeCacheRepository.invalidate(layoutCacheKey(sk))
                }
                ensureLayout(force = true)
            }
        }
    }

    private suspend fun applyCustomSections(sections: List<CustomHomeSection>) {
        val visible = sections.filter {
            it.enabled && CustomHomeSectionsRepository.isInSeason(it)
        }

        val sk = sessionKey()
        val staleKeys = mutableListOf<String>()
        val nextSignatures = mutableMapOf<String, String>()
        visible.forEach { section ->
            val signature = section.signature()
            nextSignatures[section.id] = signature
            val previous = customSectionSignatures[section.id]
            if (previous != null && previous != signature) {
                staleKeys.add(customDescriptorKey(section.id))
            }
        }
        customSectionSignatures.keys.retainAll(nextSignatures.keys)
        customSectionSignatures.putAll(nextSignatures)

        if (staleKeys.isNotEmpty()) {
            if (sk != null) {
                staleKeys.forEach { homeCacheRepository.invalidate(contentCacheKey(sk, it)) }
            }
            _content.update { map -> map - staleKeys.toSet() }
        }

        _pinnedLayout.value =
            visible
                .sortedWith(
                    compareByDescending<CustomHomeSection> { it.isSeasonal }.thenBy { it.position }
                )
                .map { section ->
                    HomeSectionDescriptor(
                        key = customDescriptorKey(section.id),
                        type = HomeSectionType.CUSTOM,
                        title = section.title,
                        customSectionId = section.id,
                        cardStyle = section.effectiveCardStyle.name,
                        supportsFullList = section.sourceType.supportsFullList,
                    )
                }
    }

    private fun customDescriptorKey(sectionId: String) = "custom_$sectionId"

    private fun CustomHomeSection.signature(): String =
        listOf(
                sourceType.name,
                sourceValues.joinToString("|"),
                includeItemTypes.joinToString("|"),
                itemLimit.toString(),
                sortBy.name,
                sortDescending.toString(),
                randomOrder.toString(),
                if (filters.isEmpty) "" else json.encodeToString(filters),
            )
            .joinToString(";")

    private val hydrationMutex = Mutex()
    private val hydrationQueue = LinkedHashSet<String>()
    private val hydrationRefreshKeys = mutableSetOf<String>()
    private var isHydrating = false
    private val renderedItemIds = mutableSetOf<UUID>()
    private val spotlightSeenIds = mutableSetOf<UUID>()

    private var presentationSeed = System.currentTimeMillis()

    fun <T> presentationOrder(key: String, items: List<T>): List<T> =
        if (items.size < 2) items else items.shuffled(Random(presentationSeed + key.hashCode()))

    private fun <T> presentationSample(key: String, items: List<T>, size: Int): List<T> =
        presentationOrder(key, items).take(size)

    private var buildJob: Job? = null
    private var layoutSessionKey: String? = null
    private var recentWatchedCache: Pair<Long, List<AfinityMovie>>? = null
    private var favoriteMoviesCache: Pair<Long, List<AfinityMovie>>? = null

    private fun sessionKey(): String? {
        val session = sessionManager.currentSession.value ?: return null
        if (session.serverId.isBlank()) return null
        return "${session.serverId}_${session.userId}"
    }

    private fun layoutCacheKey(sessionKey: String) = "home_layout_$sessionKey"

    private fun studiosCacheKey(sessionKey: String) = "home_studios_$sessionKey"

    private val studiosMutex = Mutex()
    private var cachedStudios: List<AfinityStudio>? = null

    private suspend fun studiosPool(force: Boolean): List<AfinityStudio> =
        studiosMutex.withLock {
            if (!force) cachedStudios?.let { return it }

            val sk = sessionKey()
            val baseUrl = mediaRepository.getBaseUrl()

            if (!force && sk != null) {
                val cached =
                    homeCacheRepository.getRaw(studiosCacheKey(sk), studiosTTL)?.let { raw ->
                        runCatching { json.decodeFromString<List<CachedStudio>>(raw) }.getOrNull()
                    }
                if (!cached.isNullOrEmpty()) {
                    val restored =
                        cached.mapNotNull { it.toStudio() }.map { it.withBaseUrl(baseUrl) }
                    if (restored.isNotEmpty()) {
                        cachedStudios = restored
                        return restored
                    }
                }
            }

            val fetched =
                try {
                    mediaRepository.getStudios(
                        limit = STUDIOS_POOL,
                        includeItemTypes = POPULAR_STUDIOS_TYPES,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load studios")
                    emptyList()
                }

            if (fetched.isNotEmpty()) {
                cachedStudios = fetched
                if (sk != null) {
                    runCatching {
                        homeCacheRepository.putRaw(
                            studiosCacheKey(sk),
                            json.encodeToString(fetched.map { CachedStudio.from(it) }),
                        )
                    }
                }
            }
            return fetched
        }

    private fun contentCacheKey(sessionKey: String, descriptorKey: String) =
        "home_sec_${sessionKey}_$descriptorKey"

    fun ensureLayout(force: Boolean = false) {
        seasonRecheck.update { it + 1 }
        ensureWatchAgain(force)
        ensureCriticsChoice(force)
        ensurePopularStudios(force)
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
                            retainPinnedContent()
                            _layout.value = reinterleave(cachedLayout.distinctBy { it.key })
                            Timber.d(
                                "Home layout restored from cache (${cachedLayout.size} sections)"
                            )
                            return@launch
                        }
                    }

                    val oldKeys = _layout.value.map { it.key }
                    val discovery = homeLayoutPreferencesRepository.getDiscoveryConfig()
                    val fresh =
                        if (discovery.isDiscoveryOff) emptyList() else buildLayout(discovery)
                    if (fresh.isEmpty() && !discovery.isDiscoveryOff) {
                        Timber.d("Home layout build produced no sections, keeping current state")
                        return@launch
                    }
                    hydrationMutex.withLock {
                        renderedItemIds.clear()
                        spotlightSeenIds.clear()
                    }
                    layoutSessionKey = sk
                    retainPinnedContent()
                    oldKeys.forEach { homeCacheRepository.invalidate(contentCacheKey(sk, it)) }
                    _layout.value = fresh
                    homeCacheRepository.putRaw(layoutCacheKey(sk), json.encodeToString(fresh))
                    if (force) refreshPinnedSections("home layout rebuild")
                    Timber.d("Built fresh home layout (${fresh.size} sections)")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to build home layout")
                }
            }
    }

    private fun ensureCriticsChoice(force: Boolean) {
        if (sessionKey() == null) return
        if (!force && _criticsChoice.value.isNotEmpty()) return
        if (!force && criticsChoiceJob?.isActive == true) return

        criticsChoiceJob?.cancel()
        criticsChoiceJob =
            scope.launch(Dispatchers.IO) {
                try {
                    val items = loadCriticsChoiceItems(bypassCache = force)
                    if (items.isNotEmpty()) {
                        hydrationMutex.withLock { renderedItemIds.addAll(items.map { it.id }) }
                    }
                    _criticsChoice.value = items
                    _criticsChoiceLoaded.value = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load critics choice")
                    _criticsChoiceLoaded.value = true
                }
            }
    }

    private fun ensureWatchAgain(force: Boolean) {
        if (sessionKey() == null) return
        if (!force && _watchAgain.value.isNotEmpty()) return
        if (!force && watchAgainJob?.isActive == true) return

        watchAgainJob?.cancel()
        watchAgainJob =
            scope.launch(Dispatchers.IO) {
                try {
                    val items =
                        presentationSample(
                            WATCH_AGAIN_KEY,
                            loadWatchAgainItems(bypassCache = force),
                            ROW_SIZE,
                        )
                    if (items.isNotEmpty()) {
                        hydrationMutex.withLock { renderedItemIds.addAll(items.map { it.id }) }
                    }
                    _watchAgain.value = items
                    _watchAgainLoaded.value = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load watch again")
                    _watchAgainLoaded.value = true
                }
            }
    }

    private fun ensurePopularStudios(force: Boolean) {
        if (sessionKey() == null) return
        if (!force && _popularStudios.value.isNotEmpty()) return
        if (!force && popularStudiosJob?.isActive == true) return

        popularStudiosJob?.cancel()
        popularStudiosJob =
            scope.launch(Dispatchers.IO) {
                try {
                    val studios = studiosPool(force).take(POPULAR_STUDIOS_POOL)
                    _popularStudios.value =
                        if (studios.size < MIN_POPULAR_STUDIOS) emptyList()
                        else presentationOrder(POPULAR_STUDIOS_KEY, studios)
                    _popularStudiosLoaded.value = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load popular studios")
                    _popularStudiosLoaded.value = true
                }
            }
    }

    fun hydrate(descriptorKey: String) {
        val layout = _layout.value
        val list = if (layout.any { it.key == descriptorKey }) layout else _pinnedLayout.value
        val index = list.indexOfFirst { it.key == descriptorKey }
        if (index < 0) {
            enqueueHydration(listOf(descriptorKey))
            return
        }
        enqueueHydration(list.subList(index, minOf(index + HYDRATE_AHEAD, list.size)).map { it.key })
    }

    private fun enqueueHydration(keys: List<String>, refresh: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val shouldDrain =
                hydrationMutex.withLock {
                    if (refresh) {
                        hydrationRefreshKeys.addAll(keys)
                        hydrationQueue.addAll(keys)
                    } else {
                        hydrationQueue.addAll(keys.filterNot { _content.value.containsKey(it) })
                    }
                    if (isHydrating || hydrationQueue.isEmpty()) {
                        false
                    } else {
                        isHydrating = true
                        true
                    }
                }
            if (shouldDrain) drainHydrationQueue()
        }
    }

    private suspend fun drainHydrationQueue() {
        while (true) {
            val next =
                hydrationMutex.withLock {
                    val key = hydrationQueue.firstOrNull()
                    if (key == null) {
                        isHydrating = false
                        null
                    } else {
                        hydrationQueue.remove(key)
                        key to hydrationRefreshKeys.remove(key)
                    }
                } ?: break
            val (descriptorKey, isRefresh) = next

            if (!isRefresh && _content.value.containsKey(descriptorKey)) continue

            val descriptor =
                _layout.value.firstOrNull { it.key == descriptorKey }
                    ?: _pinnedLayout.value.firstOrNull { it.key == descriptorKey }
                    ?: continue

            try {
                val content = hydrateDescriptor(descriptor, bypassCache = isRefresh)
                if (content != null) {
                    registerRenderedItems(content)
                    _content.update { it + (descriptorKey to content) }
                    if (content == HomeSectionContent.Empty) pruneEmptyDescriptor(descriptorKey)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to hydrate home section $descriptorKey")
            }
        }
    }

    private suspend fun pruneEmptyDescriptor(descriptorKey: String) {
        val sk = sessionKey() ?: return
        val current = _layout.value
        if (current.none { it.key == descriptorKey }) return
        val pruned = current.filterNot { it.key == descriptorKey }
        _layout.value = pruned
        homeCacheRepository.invalidate(contentCacheKey(sk, descriptorKey))
        homeCacheRepository.putRaw(layoutCacheKey(sk), json.encodeToString(pruned))
    }

    private fun retainPinnedContent() {
        val pinnedKeys = _pinnedLayout.value.map { it.key }.toSet()
        _content.update { map ->
            when {
                map.isEmpty() -> map
                pinnedKeys.isEmpty() -> emptyMap()
                map.keys.all { it in pinnedKeys } -> map
                else -> map.filterKeys { it in pinnedKeys }
            }
        }
    }

    fun refreshCustomSections(
        reason: String,
        debounceMs: Long = LIBRARY_REFRESH_DEBOUNCE_MS,
    ) {
        if (_pinnedLayout.value.isEmpty()) return
        customRefreshJob?.cancel()
        customRefreshJob =
            scope.launch {
                delay(debounceMs)
                if (sessionKey() == null) return@launch
                refreshPinnedSections(reason)
            }
    }

    private fun refreshPinnedSections(reason: String) {
        val keys = _pinnedLayout.value.map { it.key }
        if (keys.isEmpty()) return
        val (rendered, offScreen) = keys.partition { _content.value.containsKey(it) }
        Timber.d(
            "Refreshing pinned home sections ($reason): ${rendered.size} on screen, ${offScreen.size} deferred"
        )
        if (offScreen.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                hydrationMutex.withLock { hydrationRefreshKeys.addAll(offScreen) }
            }
        }
        if (rendered.isNotEmpty()) enqueueHydration(rendered, refresh = true)
    }

    fun updateItem(updatedItem: AfinityItem) {
        _watchAgain.update { items ->
            if (items.none { it.id == updatedItem.id }) items
            else if (!updatedItem.played) items.filterNot { it.id == updatedItem.id }
            else items.map { if (it.id == updatedItem.id) updatedItem else it }
        }
        _criticsChoice.update { items ->
            if (items.none { it.id == updatedItem.id }) items
            else items.map { if (it.id == updatedItem.id) updatedItem else it }
        }
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

    fun removeItem(itemId: String) {
        val uuid = try { UUID.fromString(itemId) } catch (e: Exception) { null }
        val matches: (UUID) -> Boolean = { id -> id.toString() == itemId || (uuid != null && id == uuid) }

        _watchAgain.update { items -> items.filterNot { matches(it.id) } }
        _criticsChoice.update { items -> items.filterNot { matches(it.id) } }
        _content.update { map ->
            var changed = false
            val patched = map.mapValues { (_, content) ->
                val newContent = removeContent(content, matches)
                if (newContent !== content) changed = true
                newContent
            }
            if (changed) patched else map
        }
    }

    private fun removeContent(
        content: HomeSectionContent,
        matches: (UUID) -> Boolean,
    ): HomeSectionContent {
        fun filterItems(items: List<AfinityItem>): List<AfinityItem>? {
            if (items.none { matches(it.id) }) return null
            return items.filterNot { matches(it.id) }
        }

        return when (content) {
            is HomeSectionContent.Person -> {
                filterItems(content.section.items)?.let {
                    HomeSectionContent.Person(content.section.copy(items = it))
                } ?: content
            }
            is HomeSectionContent.Movie -> {
                if (content.section.recommendedItems.any { matches(it.id) }) {
                    HomeSectionContent.Movie(
                        content.section.copy(
                            recommendedItems = content.section.recommendedItems.filterNot { matches(it.id) }
                        )
                    )
                } else {
                    content
                }
            }
            is HomeSectionContent.PersonFromMovie -> {
                filterItems(content.section.items)?.let {
                    HomeSectionContent.PersonFromMovie(content.section.copy(items = it))
                } ?: content
            }
            is HomeSectionContent.Spotlight -> {
                filterItems(content.items)?.let { HomeSectionContent.Spotlight(it) } ?: content
            }
            is HomeSectionContent.Items -> {
                filterItems(content.items)?.let { HomeSectionContent.Items(it) } ?: content
            }
            is HomeSectionContent.RankedItems -> {
                filterItems(content.items)?.let { HomeSectionContent.RankedItems(it) } ?: content
            }
            HomeSectionContent.Empty -> content
        }
    }


    suspend fun clearAllData() {
        buildJob?.cancel()
        customRefreshJob?.cancel()
        layoutSessionKey = null
        recentWatchedCache = null
        favoriteMoviesCache = null
        hydrationMutex.withLock {
            hydrationQueue.clear()
            hydrationRefreshKeys.clear()
            isHydrating = false
            renderedItemIds.clear()
            spotlightSeenIds.clear()
        }
        _layout.value = emptyList()
        _content.value = emptyMap()
        _pinnedLayout.value = emptyList()
        cachedStudios = null
        customSectionSignatures.clear()
        presentationSeed = System.currentTimeMillis()
        watchAgainJob?.cancel()
        _watchAgain.value = emptyList()
        _watchAgainLoaded.value = false
        criticsChoiceJob?.cancel()
        _criticsChoice.value = emptyList()
        _criticsChoiceLoaded.value = false
        popularStudiosJob?.cancel()
        _popularStudios.value = emptyList()
        _popularStudiosLoaded.value = false
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
            is HomeSectionContent.Items -> {
                patchItems(content.items)?.let { HomeSectionContent.Items(it) } ?: content
            }
            is HomeSectionContent.RankedItems -> {
                patchItems(content.items)?.let { HomeSectionContent.RankedItems(it) } ?: content
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
                is HomeSectionContent.Items -> content.items.map { it.id } to false
                is HomeSectionContent.RankedItems -> content.items.map { it.id } to false
                HomeSectionContent.Empty -> return
            }
        hydrationMutex.withLock {
            if (isSpotlight) spotlightSeenIds.addAll(ids) else renderedItemIds.addAll(ids)
        }
    }

    private suspend fun hydrateDescriptor(
        descriptor: HomeSectionDescriptor,
        bypassCache: Boolean = false,
    ): HomeSectionContent? {
        return when (descriptor.type) {
            HomeSectionType.STARRING,
            HomeSectionType.DIRECTED_BY,
            HomeSectionType.WRITTEN_BY -> hydratePersonSection(descriptor, bypassCache)
            HomeSectionType.BECAUSE_YOU_WATCHED ->
                hydrateSimilarToReference(
                    descriptor,
                    MovieSectionType.BECAUSE_YOU_WATCHED,
                    bypassCache,
                )
            HomeSectionType.BECAUSE_YOU_LIKED ->
                hydrateSimilarToReference(
                    descriptor,
                    MovieSectionType.BECAUSE_YOU_LIKED,
                    bypassCache,
                )
            HomeSectionType.ACTOR_FROM_MOVIE ->
                hydratePersonFromMovie(
                    descriptor,
                    PersonKind.ACTOR,
                    PersonSectionType.STARRING,
                    bypassCache,
                )
            HomeSectionType.DIRECTOR_FROM_MOVIE ->
                hydratePersonFromMovie(
                    descriptor,
                    PersonKind.DIRECTOR,
                    PersonSectionType.DIRECTED_BY,
                    bypassCache,
                )
            HomeSectionType.WRITER_FROM_MOVIE ->
                hydratePersonFromMovie(
                    descriptor,
                    PersonKind.WRITER,
                    PersonSectionType.WRITTEN_BY,
                    bypassCache,
                )
            HomeSectionType.WATCH_AGAIN -> hydrateWatchAgain(descriptor, bypassCache)
            HomeSectionType.CRITICS_CHOICE -> hydrateCriticsChoice(descriptor, bypassCache)
            HomeSectionType.CUSTOM -> hydrateCustomSection(descriptor, bypassCache)
            HomeSectionType.SPOTLIGHT_GENRE_MOVIE,
            HomeSectionType.SPOTLIGHT_GENRE_SHOW,
            HomeSectionType.SPOTLIGHT_STUDIO,
            HomeSectionType.SPOTLIGHT_BOXSET -> hydrateSpotlight(descriptor, bypassCache)
            HomeSectionType.GENRE_MOVIE,
            HomeSectionType.GENRE_SHOW -> null
        }
    }

    private suspend fun hydratePersonSection(
        descriptor: HomeSectionDescriptor,
        bypassCache: Boolean = false,
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
            peopleRepository.getPersonSection(personWithCount, sectionType, bypassCache)
                ?: return HomeSectionContent.Empty
        return HomeSectionContent.Person(
            section.copy(items = presentationOrder(descriptor.key, section.items))
        )
    }

    private suspend fun decodeReferenceMovie(movieJson: String?): AfinityMovie? {
        val movie = movieJson?.let { converters.toAfinityMovie(it) } ?: return null
        return movie.copy(images = movie.images.withBaseUrl(mediaRepository.getBaseUrl()))
    }

    private suspend fun hydrateSimilarToReference(
        descriptor: HomeSectionDescriptor,
        sectionType: MovieSectionType,
        bypassCache: Boolean = false,
    ): HomeSectionContent {
        val referenceMovie =
            decodeReferenceMovie(descriptor.referenceMovieJson) ?: return HomeSectionContent.Empty

        val sk = sessionKey() ?: return HomeSectionContent.Empty
        val cacheKey = contentCacheKey(sk, descriptor.key)
        val cachedItems =
            if (bypassCache) null
            else
                homeCacheRepository
                    .getItems(cacheKey, mediaRepository.getBaseUrl(), recentCacheTTL)
                    ?.filterIsInstance<AfinityMovie>()
        if (!cachedItems.isNullOrEmpty()) {
            return HomeSectionContent.Movie(
                MovieSection(
                    referenceMovie = referenceMovie,
                    recommendedItems = presentationSample(descriptor.key, cachedItems, ROW_SIZE),
                    sectionType = sectionType,
                )
            )
        }

        val excluded = hydrationMutex.withLock { renderedItemIds.toSet() }
        val pool =
            mediaRepository
                .getSimilarMovies(movieId = referenceMovie.id, limit = SIMILAR_POOL)
                .filterNot { it.id in excluded }
        if (pool.size < 5) return HomeSectionContent.Empty

        homeCacheRepository.putItems(cacheKey, pool)
        return HomeSectionContent.Movie(
            MovieSection(
                referenceMovie = referenceMovie,
                recommendedItems = presentationSample(descriptor.key, pool, ROW_SIZE),
                sectionType = sectionType,
            )
        )
    }

    private suspend fun hydrateWatchAgain(
        descriptor: HomeSectionDescriptor,
        bypassCache: Boolean = false,
    ): HomeSectionContent {
        val items =
            presentationSample(
                descriptor.key,
                loadWatchAgainItems(descriptor.key, bypassCache),
                ROW_SIZE,
            )
        return if (items.isEmpty()) HomeSectionContent.Empty else HomeSectionContent.Items(items)
    }

    private suspend fun loadWatchAgainItems(
        descriptorKey: String = WATCH_AGAIN_KEY,
        bypassCache: Boolean = false,
    ): List<AfinityItem> {
        val sk = sessionKey() ?: return emptyList()
        val cacheKey = contentCacheKey(sk, descriptorKey)
        val baseUrl = mediaRepository.getBaseUrl()
        val cachedItems =
            if (bypassCache) null
            else homeCacheRepository.getItems(cacheKey, baseUrl, recentCacheTTL)
        if (!cachedItems.isNullOrEmpty()) {
            val playedOnly = cachedItems.filter { it.played }
            return if (playedOnly.size < WATCH_AGAIN_MIN_ITEMS) emptyList() else playedOnly
        }

        val (watchedItems, watchedBoxSets) =
            coroutineScope {
                val showsDeferred = async {
                    try {
                        mediaRepository.getShows(
                            sortBy = SortBy.DATE_PLAYED,
                            sortDescending = true,
                            isPlayed = true,
                            limit = WATCH_AGAIN_POOL,
                            fields = FieldSets.MEDIA_ITEM_CARDS,
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load watched shows for watch again")
                        emptyList()
                    }
                }
                val moviesDeferred = async {
                    try {
                        mediaRepository.getMovies(
                            sortBy = SortBy.DATE_PLAYED,
                            sortDescending = true,
                            isPlayed = true,
                            limit = WATCH_AGAIN_POOL,
                            fields = FieldSets.MEDIA_ITEM_CARDS,
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load watched movies for watch again")
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
                            ?.mapNotNull { it.toAfinityItem(baseUrl) }
                            ?.filterIsInstance<AfinityBoxSet>()
                            ?.filter { it.unplayedItemCount == 0 && (it.itemCount ?: 0) >= 2 }
                            ?: emptyList()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load watched boxsets for watch again")
                        emptyList()
                    }
                }
                val shows =
                    showsDeferred.await().filter {
                        it.unplayedItemCount == null || it.unplayedItemCount == 0
                    }
                (shows + moviesDeferred.await()) to boxSetsDeferred.await()
            }

        val candidates =
            buildList<AfinityItem> {
                addAll(watchedItems)
                addAll(watchedBoxSets)
            }
        homeCacheRepository.putItems(cacheKey, candidates)
        return if (candidates.size < WATCH_AGAIN_MIN_ITEMS) emptyList() else candidates
    }

    private suspend fun hydrateCriticsChoice(
        descriptor: HomeSectionDescriptor,
        bypassCache: Boolean = false,
    ): HomeSectionContent {
        val items = loadCriticsChoiceItems(descriptor.key, bypassCache)
        return if (items.isEmpty()) HomeSectionContent.Empty
        else HomeSectionContent.RankedItems(items)
    }

    private suspend fun loadCriticsChoiceItems(
        descriptorKey: String = CRITICS_CHOICE_KEY,
        bypassCache: Boolean = false,
    ): List<AfinityItem> {
        val sk = sessionKey() ?: return emptyList()
        val cacheKey = contentCacheKey(sk, descriptorKey)
        val cachedItems =
            if (bypassCache) null
            else homeCacheRepository.getItems(cacheKey, mediaRepository.getBaseUrl(), recentCacheTTL)
        if (!cachedItems.isNullOrEmpty()) {
            return rankByRating(presentationSample(descriptorKey, cachedItems, CRITICS_ROW_SIZE))
        }

        val topMovies =
            try {
                mediaRepository.getMovies(
                    sortBy = SortBy.IMDB_RATING,
                    sortDescending = true,
                    limit = 25,
                    fields = FieldSets.MEDIA_ITEM_CARDS,
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to load top rated movies")
                emptyList()
            }

        val topShows =
            try {
                mediaRepository.getShows(
                    sortBy = SortBy.IMDB_RATING,
                    sortDescending = true,
                    limit = 25,
                    fields = FieldSets.MEDIA_ITEM_CARDS,
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to load top rated shows")
                emptyList()
            }

        val items =
            buildList<AfinityItem> {
                    addAll(topMovies.filter { (it.communityRating ?: 0f) > MIN_CRITICS_RATING })
                    addAll(topShows.filter { (it.communityRating ?: 0f) > MIN_CRITICS_RATING })
                }
                .distinctBy { it.id }
        if (items.size < 5) return emptyList()

        homeCacheRepository.putItems(cacheKey, items)
        return rankByRating(presentationSample(descriptorKey, items, CRITICS_ROW_SIZE))
    }

    private fun rankByRating(items: List<AfinityItem>): List<AfinityItem> =
        items.sortedByDescending {
            when (it) {
                is AfinityMovie -> it.communityRating ?: 0f
                is AfinityShow -> it.communityRating ?: 0f
                else -> 0f
            }
        }

    private suspend fun hydrateCustomSection(
        descriptor: HomeSectionDescriptor,
        bypassCache: Boolean = false,
    ): HomeSectionContent {
        val sectionId = descriptor.customSectionId ?: return HomeSectionContent.Empty
        val section = customHomeSectionsRepository.get(sectionId) ?: return HomeSectionContent.Empty

        val sk = sessionKey() ?: return HomeSectionContent.Empty
        val cacheKey = contentCacheKey(sk, descriptor.key)
        val baseUrl = mediaRepository.getBaseUrl()

        if (!section.randomOrder && !bypassCache) {
            val cachedItems = homeCacheRepository.getItems(cacheKey, baseUrl, recentCacheTTL)
            if (!cachedItems.isNullOrEmpty()) {
                return HomeSectionContent.Items(cachedItems.take(section.itemLimit))
            }
        }

        val sourceId =
            when (section.sourceType) {
                CustomSectionSourceType.COLLECTION,
                CustomSectionSourceType.PLAYLIST,
                CustomSectionSourceType.LIBRARY ->
                    try {
                        UUID.fromString(section.primarySourceValue.orEmpty())
                    } catch (e: Exception) {
                        Timber.w(e, "Custom section ${section.id} has an invalid source id")
                        return HomeSectionContent.Empty
                    }
                else -> null
            }

        val refinement = section.filters.toItemFilterCriteria()
        val criteria =
            when (section.sourceType) {
                CustomSectionSourceType.GENRE ->
                    refinement.copy(genres = (refinement.genres + section.sourceValues).distinct())
                CustomSectionSourceType.STUDIO -> refinement.copy(studios = section.sourceValues)
                CustomSectionSourceType.TAG ->
                    refinement.copy(tags = (refinement.tags + section.sourceValues).distinct())
                else -> refinement
            }

        val fetchLimit = section.itemLimit

        val items =
            try {
                if (section.sourceType == CustomSectionSourceType.PLAYLIST && sourceId != null) {
                    mediaRepository
                        .getPlaylistItems(sourceId, fields = FieldSets.MEDIA_ITEM_CARDS)
                        .filter { it.type.name in section.includeItemTypes }
                        .let { if (section.randomOrder) it.shuffled() else it }
                        .take(fetchLimit)
                        .mapNotNull { it.toAfinityItem(baseUrl) }
                } else {
                    mediaRepository
                        .getItems(
                            parentId = sourceId,
                            sortBy = if (section.randomOrder) SortBy.RANDOM else section.sortBy,
                            sortDescending = section.sortDescending,
                            limit = fetchLimit,
                            includeItemTypes = section.includeItemTypes,
                            fields = FieldSets.MEDIA_ITEM_CARDS,
                            recursive =
                                when (section.sourceType) {
                                    CustomSectionSourceType.LIBRARY -> true
                                    CustomSectionSourceType.COLLECTION -> false
                                    else -> null
                                },
                            criteria = criteria,
                        )
                        .items
                        ?.mapNotNull { it.toAfinityItem(baseUrl) } ?: emptyList()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load custom home section ${section.id}")
                val stale =
                    if (section.randomOrder) null
                    else homeCacheRepository.getItems(cacheKey, baseUrl)
                return if (stale.isNullOrEmpty()) {
                    HomeSectionContent.Empty
                } else {
                    HomeSectionContent.Items(stale.take(section.itemLimit))
                }
            }

        if (items.isEmpty()) return HomeSectionContent.Empty

        if (!section.randomOrder) {
            homeCacheRepository.putItems(cacheKey, items)
        }
        return HomeSectionContent.Items(items.take(section.itemLimit))
    }

    private suspend fun hydratePersonFromMovie(
        descriptor: HomeSectionDescriptor,
        personKind: PersonKind,
        sectionType: PersonSectionType,
        bypassCache: Boolean = false,
    ): HomeSectionContent {
        val cachedPerson = descriptor.person ?: return HomeSectionContent.Empty
        val person = PersonWithCount.fromCached(cachedPerson, mediaRepository.getBaseUrl()).person
        val referenceMovie =
            decodeReferenceMovie(descriptor.referenceMovieJson) ?: return HomeSectionContent.Empty

        val sk = sessionKey() ?: return HomeSectionContent.Empty
        val cacheKey = contentCacheKey(sk, descriptor.key)
        val cachedItems =
            if (bypassCache) null
            else homeCacheRepository.getItems(cacheKey, mediaRepository.getBaseUrl(), recentCacheTTL)
        if (!cachedItems.isNullOrEmpty()) {
            return HomeSectionContent.PersonFromMovie(
                PersonFromMovieSection(
                    person = person,
                    referenceMovie = referenceMovie,
                    items = presentationSample(descriptor.key, cachedItems, ROW_SIZE),
                    sectionType = sectionType,
                )
            )
        }

        val excluded = hydrationMutex.withLock { renderedItemIds.toSet() }
        val personMovies =
            mediaRepository
                .getPersonItems(
                    personId = person.id,
                    includeItemTypes = listOf("MOVIE"),
                    fields = listOf(ItemFields.PEOPLE),
                )
                .filterIsInstance<AfinityMovie>()
                .filter { movie ->
                    movie.people.any { it.id == person.id && it.type == personKind }
                }
                .filterNot { it.id == referenceMovie.id || it.id in excluded }
                .take(PERSON_POOL)
        if (personMovies.size < 5) return HomeSectionContent.Empty

        homeCacheRepository.putItems(cacheKey, personMovies)
        return HomeSectionContent.PersonFromMovie(
            PersonFromMovieSection(
                person = person,
                referenceMovie = referenceMovie,
                items = presentationSample(descriptor.key, personMovies, ROW_SIZE),
                sectionType = sectionType,
            )
        )
    }

    private suspend fun hydrateSpotlight(
        descriptor: HomeSectionDescriptor,
        bypassCache: Boolean = false,
    ): HomeSectionContent {
        val sk = sessionKey() ?: return HomeSectionContent.Empty
        val cacheKey = contentCacheKey(sk, descriptor.key)
        val cachedItems =
            if (bypassCache) null
            else homeCacheRepository.getItems(cacheKey, mediaRepository.getBaseUrl(), recentCacheTTL)
        if (!cachedItems.isNullOrEmpty()) {
            return HomeSectionContent.Spotlight(
                presentationSample(descriptor.key, cachedItems, SPOTLIGHT_ROW_SIZE)
            )
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
        val pool = rawItems.filterNot { it.id in excluded }
        if (pool.size < 3) return HomeSectionContent.Empty

        homeCacheRepository.putItems(cacheKey, pool)
        return HomeSectionContent.Spotlight(
            presentationSample(descriptor.key, pool, SPOTLIGHT_ROW_SIZE)
        )
    }

    private suspend fun buildLayout(discovery: DiscoveryConfig): List<HomeSectionDescriptor> =
        coroutineScope {
            fun cap(section: DiscoverySection) = discovery.countFor(section)

            val actorsDeferred = async {
                peopleRepository.getTopPeople(PersonKind.ACTOR, limit = 75, minAppearances = 5)
            }
            val directorsDeferred = async {
                peopleRepository.getTopPeople(PersonKind.DIRECTOR, limit = 75, minAppearances = 5)
            }
            val writersDeferred = async {
                peopleRepository.getTopPeople(PersonKind.WRITER, limit = 50, minAppearances = 3)
            }
            val studiosDeferred = async { studiosPool(force = false) }
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
            val usedPeopleIds = mutableSetOf<UUID>()

            fun personDescriptors(
                people: List<PersonWithCount>,
                type: HomeSectionType,
                max: Int,
                titleRes: Int,
            ): List<HomeSectionDescriptor> =
                people
                    .distinctBy { it.person.id }
                    .filterNot {
                        it.person.name in usedPeopleNames || it.person.id in usedPeopleIds
                    }
                    .shuffled()
                    .take(max)
                    .map { personWithCount ->
                        usedPeopleNames.add(personWithCount.person.name)
                        usedPeopleIds.add(personWithCount.person.id)
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
                    max = cap(DiscoverySection.STARRING),
                    titleRes = R.string.home_person_starring,
                )
            val directorDescriptors =
                personDescriptors(
                    directorsDeferred.await(),
                    HomeSectionType.DIRECTED_BY,
                    max = cap(DiscoverySection.DIRECTED_BY),
                    titleRes = R.string.home_person_directed_by,
                )
            val writerDescriptors =
                personDescriptors(
                    writersDeferred.await(),
                    HomeSectionType.WRITTEN_BY,
                    max = cap(DiscoverySection.WRITTEN_BY),
                    titleRes = R.string.home_person_written_by,
                )

            val becauseYouWatchedDescriptors = mutableListOf<HomeSectionDescriptor>()
            val usedReferenceMovies = mutableSetOf<UUID>()
            while (becauseYouWatchedDescriptors.size < cap(DiscoverySection.BECAUSE_YOU_WATCHED)) {
                val referenceMovie = getRandomRecentlyWatchedMovie(usedReferenceMovies) ?: break
                usedReferenceMovies.add(referenceMovie.id)
                val movieJson = converters.fromAfinityMovie(referenceMovie) ?: continue
                becauseYouWatchedDescriptors.add(
                    HomeSectionDescriptor(
                        key = "byw_${referenceMovie.id}",
                        type = HomeSectionType.BECAUSE_YOU_WATCHED,
                        title =
                            context.getString(
                                R.string.home_because_you_watched,
                                referenceMovie.name,
                            ),
                        referenceMovieJson = movieJson,
                    )
                )
            }

            val becauseYouLikedDescriptors = mutableListOf<HomeSectionDescriptor>()
            val usedFavoriteMovies = mutableSetOf<UUID>()
            while (becauseYouLikedDescriptors.size < cap(DiscoverySection.BECAUSE_YOU_LIKED)) {
                val referenceMovie = getRandomFavoriteMovie(usedFavoriteMovies) ?: break
                usedFavoriteMovies.add(referenceMovie.id)
                if (referenceMovie.id in usedReferenceMovies) continue
                val movieJson = converters.fromAfinityMovie(referenceMovie) ?: continue
                becauseYouLikedDescriptors.add(
                    HomeSectionDescriptor(
                        key = "byl_${referenceMovie.id}",
                        type = HomeSectionType.BECAUSE_YOU_LIKED,
                        title =
                            context.getString(R.string.home_because_you_liked, referenceMovie.name),
                        referenceMovieJson = movieJson,
                    )
                )
            }

            val personFromMovieDescriptors = mutableListOf<HomeSectionDescriptor>()
            val usedPersonFromMovies = mutableSetOf<UUID>()

            suspend fun addPersonFromMovieDescriptors(
                type: HomeSectionType,
                personKind: PersonKind,
                max: Int,
                titleRes: Int,
            ) {
                var added = 0
                while (added < max) {
                    val randomMovie = getRandomRecentlyWatchedMovie(usedPersonFromMovies) ?: break
                    usedPersonFromMovies.add(randomMovie.id)

                    val movieWithPeople =
                        try {
                            mediaRepository
                                .getItem(
                                    itemId = randomMovie.id,
                                    fields = listOf(ItemFields.PEOPLE),
                                )
                                ?.toAfinityMovie(mediaRepository.getBaseUrl())
                        } catch (e: Exception) {
                            null
                        } ?: continue

                    val availablePeople =
                        movieWithPeople.people
                            .filter { it.type == personKind }
                            .filterNot { it.name in usedPeopleNames || it.id in usedPeopleIds }
                    val selectedPerson = availablePeople.take(3).randomOrNull() ?: continue
                    usedPeopleNames.add(selectedPerson.name)
                    usedPeopleIds.add(selectedPerson.id)

                    val movieJson = converters.fromAfinityMovie(movieWithPeople) ?: continue
                    personFromMovieDescriptors.add(
                        HomeSectionDescriptor(
                            key = "personfrom_${type.name}_${selectedPerson.id}_${randomMovie.id}",
                            type = type,
                            title =
                                context.getString(titleRes, selectedPerson.name, randomMovie.name),
                            person = PersonWithCount(selectedPerson, 0).toCached(),
                            referenceMovieJson = movieJson,
                        )
                    )
                    added++
                }
            }

            addPersonFromMovieDescriptors(
                HomeSectionType.ACTOR_FROM_MOVIE,
                PersonKind.ACTOR,
                max = cap(DiscoverySection.ACTOR_FROM_MOVIE),
                titleRes = R.string.home_starring_from_watched,
            )
            addPersonFromMovieDescriptors(
                HomeSectionType.DIRECTOR_FROM_MOVIE,
                PersonKind.DIRECTOR,
                max = cap(DiscoverySection.DIRECTOR_FROM_MOVIE),
                titleRes = R.string.home_directed_by_from_watched,
            )
            addPersonFromMovieDescriptors(
                HomeSectionType.WRITER_FROM_MOVIE,
                PersonKind.WRITER,
                max = cap(DiscoverySection.WRITER_FROM_MOVIE),
                titleRes = R.string.home_written_by_from_watched,
            )

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
                                    context.getString(
                                        R.string.home_genre_top_movies_fmt,
                                        genre.name,
                                    ),
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
                                    context.getString(
                                        R.string.home_genre_top_series_fmt,
                                        genre.name,
                                    ),
                                genreName = genre.name,
                            )
                        )
                    }
                studiosDeferred.await().shuffled().take(10).forEach { studio ->
                    add(
                        HomeSectionDescriptor(
                            key = "spot_studio_${studio.name}",
                            type = HomeSectionType.SPOTLIGHT_STUDIO,
                            title =
                                context.getString(R.string.home_best_of_studio_fmt, studio.name),
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
            val spotlights = spotlightDescriptors.shuffled().take(cap(DiscoverySection.SPOTLIGHTS))

            val recommendationDescriptors =
                (actorDescriptors + directorDescriptors).shuffled() +
                    (writerDescriptors + becauseYouWatchedDescriptors + becauseYouLikedDescriptors)
                        .shuffled() +
                    personFromMovieDescriptors.shuffled()

            val genreDescriptors =
                genres.shuffled().take(cap(DiscoverySection.GENRES)).map { genre ->
                    HomeSectionDescriptor(
                        key = "genre_${genre.type.name.lowercase()}_${genre.name}",
                        type =
                            if (genre.type == GenreType.MOVIE) HomeSectionType.GENRE_MOVIE
                            else HomeSectionType.GENRE_SHOW,
                        title = genre.name,
                        genreName = genre.name,
                    )
                }

            interleave(genreDescriptors, recommendationDescriptors, spotlights).distinctBy {
                it.key
            }
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
                        it.type == HomeSectionType.BECAUSE_YOU_LIKED ||
                        it.type == HomeSectionType.ACTOR_FROM_MOVIE ||
                        it.type == HomeSectionType.DIRECTOR_FROM_MOVIE ||
                        it.type == HomeSectionType.WRITER_FROM_MOVIE
                }
                .shuffled()

        return interleave(genres, recommendations, spotlights)
    }

    private fun interleave(
        genres: List<HomeSectionDescriptor>,
        recommendations: List<HomeSectionDescriptor>,
        spotlights: List<HomeSectionDescriptor>,
    ): List<HomeSectionDescriptor> {
        val finalLayout = mergeProportionally(genres, recommendations).toMutableList()

        if (spotlights.isNotEmpty()) {
            val positions = computeSpotlightPositions(finalLayout.size, spotlights.size)
            positions.sorted().forEachIndexed { offset, pos ->
                finalLayout.add((pos + offset).coerceIn(0, finalLayout.size), spotlights[offset])
            }
        }
        return finalLayout
    }

    private fun mergeProportionally(
        primary: List<HomeSectionDescriptor>,
        secondary: List<HomeSectionDescriptor>,
    ): List<HomeSectionDescriptor> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary

        val total = primary.size + secondary.size
        val merged = ArrayList<HomeSectionDescriptor>(total)
        var primaryIndex = 0
        var secondaryIndex = 0

        for (slot in 0 until total) {
            val takePrimary =
                if (primaryIndex >= primary.size) false
                else if (secondaryIndex >= secondary.size) true
                else {
                    val primaryProgress = (primaryIndex + 1).toFloat() / primary.size
                    val secondaryProgress = (secondaryIndex + 1).toFloat() / secondary.size
                    primaryProgress <= secondaryProgress
                }

            if (takePrimary) {
                merged.add(primary[primaryIndex])
                primaryIndex++
            } else {
                merged.add(secondary[secondaryIndex])
                secondaryIndex++
            }
        }
        return merged
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

    private suspend fun getRandomFavoriteMovie(excludedMovies: Set<UUID>): AfinityMovie? {
        try {
            val now = System.currentTimeMillis()
            val cached = favoriteMoviesCache

            val allFavorites =
                if (cached != null && now - cached.first < recentCacheTTL) {
                    cached.second
                } else {
                    val movies =
                        mediaRepository.getMovies(
                            sortBy = SortBy.DATE_ADDED,
                            sortDescending = true,
                            limit = 10,
                            isFavorite = true,
                        )
                    favoriteMoviesCache = now to movies
                    movies
                }

            return allFavorites.filterNot { it.id in excludedMovies }.randomOrNull()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get random favorite movie")
            return null
        }
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

    companion object {
        const val ADMIN_REFRESH_DEBOUNCE_MS = 400L
        const val LIBRARY_REFRESH_DEBOUNCE_MS = 2_000L
    }
}

private const val WATCH_AGAIN_KEY = "watch_again"
private const val WATCH_AGAIN_POOL = 50
private const val WATCH_AGAIN_MIN_ITEMS = 5
private const val POPULAR_STUDIOS_KEY = "popular_studios"
private const val STUDIOS_POOL = 50

@Serializable
private data class CachedStudio(
    val id: String,
    val name: String,
    val primaryImageUrl: String?,
    val itemCount: Int,
) {
    fun toStudio(): AfinityStudio? =
        runCatching {
                AfinityStudio(
                    id = UUID.fromString(id),
                    name = name,
                    primaryImageUrl = primaryImageUrl,
                    itemCount = itemCount,
                )
            }
            .getOrNull()

    companion object {
        fun from(studio: AfinityStudio) =
            CachedStudio(
                id = studio.id.toString(),
                name = studio.name,
                primaryImageUrl = studio.primaryImageUrl,
                itemCount = studio.itemCount,
            )
    }
}

private const val POPULAR_STUDIOS_POOL = 20
private const val MIN_POPULAR_STUDIOS = 3
private val POPULAR_STUDIOS_TYPES = listOf("MOVIE", "SERIES")
private const val MIN_CRITICS_RATING = 6.5f
private const val CRITICS_CHOICE_KEY = "critics_choice"

private const val HYDRATE_AHEAD = 4

private const val ROW_SIZE = 20
private const val SPOTLIGHT_ROW_SIZE = 10
private const val CRITICS_ROW_SIZE = 10
private const val SIMILAR_POOL = 50
private const val PERSON_POOL = 50
