package com.makd.afinity.ui.settings.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.CustomHomeSection
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.CustomSectionSourceType
import com.makd.afinity.data.models.DiscoveryConfig
import com.makd.afinity.data.models.DiscoveryDensity
import com.makd.afinity.data.models.DiscoverySection
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.LibraryFilterOptions
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.GenreRepository
import com.makd.afinity.data.repository.home.CustomHomeSectionsRepository
import com.makd.afinity.data.repository.home.HomeLayoutPreferencesRepository
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

enum class SourceLoadState {
    LOADING,
    LOADED,
    FAILED,
}

data class SourceOption(
    val value: String,
    val label: String,
    val libraryType: CollectionType? = null,
)

data class CustomSectionsUiState(
    val sections: List<CustomHomeSection> = emptyList(),
    val genreOptions: List<SourceOption> = emptyList(),
    val studioOptions: List<SourceOption> = emptyList(),
    val collectionOptions: List<SourceOption> = emptyList(),
    val playlistOptions: List<SourceOption> = emptyList(),
    val libraryOptions: List<SourceOption> = emptyList(),
    val tagOptions: List<SourceOption> = emptyList(),
    val filterOptions: LibraryFilterOptions = LibraryFilterOptions(),
    val sourceStates: Map<CustomSectionSourceType, SourceLoadState> = emptyMap(),
    val canAddMore: Boolean = true,
    val hiddenRows: Set<HomeRow> = emptySet(),
    val discovery: DiscoveryConfig = DiscoveryConfig(),
) {
    fun stateFor(sourceType: CustomSectionSourceType): SourceLoadState? = sourceStates[sourceType]

    fun optionsFor(sourceType: CustomSectionSourceType): List<SourceOption> =
        when (sourceType) {
            CustomSectionSourceType.GENRE -> genreOptions
            CustomSectionSourceType.STUDIO -> studioOptions
            CustomSectionSourceType.COLLECTION -> collectionOptions
            CustomSectionSourceType.PLAYLIST -> playlistOptions
            CustomSectionSourceType.LIBRARY -> libraryOptions
            CustomSectionSourceType.TAG -> tagOptions
        }

    fun sourceLabelsFor(section: CustomHomeSection): List<String>? {
        val options = optionsFor(section.sourceType)
        val labels =
            section.sourceValues.map { value ->
                options.firstOrNull { it.value == value }?.label
                    ?: if (section.sourceType.usesItemIds) return null else value
            }
        return labels
    }
}

@HiltViewModel
class CustomSectionsViewModel
@Inject
constructor(
    private val customHomeSectionsRepository: CustomHomeSectionsRepository,
    private val homeLayoutPreferencesRepository: HomeLayoutPreferencesRepository,
    private val mediaRepository: MediaRepository,
    private val genreRepository: GenreRepository,
) : ViewModel() {

    private val _pendingTemplate = MutableStateFlow<CustomHomeSection?>(null)
    val pendingTemplate: StateFlow<CustomHomeSection?> = _pendingTemplate.asStateFlow()

    private val _uiState = MutableStateFlow(CustomSectionsUiState())
    val uiState: StateFlow<CustomSectionsUiState> = _uiState.asStateFlow()

    private var cachedFilterOptions: LibraryFilterOptions? = null

    init {
        viewModelScope.launch {
            customHomeSectionsRepository.sections.collect { sections ->
                _uiState.update {
                    it.copy(
                        sections = sections,
                        canAddMore = sections.size < CustomHomeSection.MAX_SECTIONS,
                    )
                }
                sections
                    .map { it.sourceType }
                    .filter { it.usesItemIds }
                    .distinct()
                    .forEach { ensureSourcesLoaded(it) }
            }
        }
        viewModelScope.launch {
            homeLayoutPreferencesRepository.hiddenRows.collect { hidden ->
                _uiState.update { it.copy(hiddenRows = hidden) }
            }
        }

        viewModelScope.launch {
            homeLayoutPreferencesRepository.discoveryConfig.collect { config ->
                _uiState.update { it.copy(discovery = config) }
            }
        }
    }

    fun setRowVisible(row: HomeRow, visible: Boolean) {
        viewModelScope.launch { homeLayoutPreferencesRepository.setRowVisible(row, visible) }
    }

    fun setDensity(density: DiscoveryDensity) {
        viewModelScope.launch { homeLayoutPreferencesRepository.setDiscoveryDensity(density) }
    }

    fun setDiscoveryCount(section: DiscoverySection, count: Int) {
        viewModelScope.launch {
            if (count <= 0) {
                homeLayoutPreferencesRepository.setDiscoverySection(
                    section,
                    enabled = false,
                    maxCount = null,
                )
            } else {
                homeLayoutPreferencesRepository.setDiscoverySection(
                    section,
                    enabled = true,
                    maxCount = count,
                )
            }
        }
    }

    fun requestPreset(preset: SeasonalPreset) {
        viewModelScope.launch {
            awaitSources(CustomSectionSourceType.TAG)
            awaitSources(CustomSectionSourceType.GENRE)
            _pendingTemplate.value = presetTemplate(preset)
        }
    }

    fun consumePendingTemplate() {
        _pendingTemplate.value = null
    }

    fun ensureFilterOptionsLoaded() {
        ensureSourcesLoaded(CustomSectionSourceType.TAG)
    }

    fun ensureSourcesLoaded(sourceType: CustomSectionSourceType, force: Boolean = false) {
        val state = _uiState.value.stateFor(sourceType)
        if (state == SourceLoadState.LOADING) return
        if (!force && state == SourceLoadState.LOADED) return
        if (force) cachedFilterOptions = null
        loadSourcesFor(sourceType)
    }

    private fun setSourceState(sourceType: CustomSectionSourceType, state: SourceLoadState) {
        _uiState.update { it.copy(sourceStates = it.sourceStates + (sourceType to state)) }
    }

    private fun loadSourcesFor(sourceType: CustomSectionSourceType) {
        viewModelScope.launch { loadSourcesNow(sourceType) }
    }

    private suspend fun awaitSources(sourceType: CustomSectionSourceType) {
        if (_uiState.value.stateFor(sourceType) == SourceLoadState.LOADED) return
        loadSourcesNow(sourceType)
    }

    private suspend fun loadSourcesNow(sourceType: CustomSectionSourceType) {
        setSourceState(sourceType, SourceLoadState.LOADING)
        try {
            val options = fetchSourceOptions(sourceType)
            if (options == null) {
                setSourceState(sourceType, SourceLoadState.FAILED)
                return
            }
            _uiState.update {
                val next =
                    when (sourceType) {
                        CustomSectionSourceType.GENRE -> it.copy(genreOptions = options)
                        CustomSectionSourceType.STUDIO -> it.copy(studioOptions = options)
                        CustomSectionSourceType.COLLECTION -> it.copy(collectionOptions = options)
                        CustomSectionSourceType.PLAYLIST -> it.copy(playlistOptions = options)
                        CustomSectionSourceType.LIBRARY -> it.copy(libraryOptions = options)
                        CustomSectionSourceType.TAG -> it.copy(tagOptions = options)
                    }
                next.copy(sourceStates = next.sourceStates + (sourceType to SourceLoadState.LOADED))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load $sourceType options for custom sections")
            setSourceState(sourceType, SourceLoadState.FAILED)
        }
    }

    private suspend fun fetchSourceOptions(
        sourceType: CustomSectionSourceType
    ): List<SourceOption>? =
        when (sourceType) {
            CustomSectionSourceType.GENRE -> {
                if (genreRepository.combinedGenres.value.isEmpty()) {
                    try {
                        genreRepository.loadCombinedGenres()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load genres for custom sections")
                    }
                }
                val indexed = genreRepository.combinedGenres.value.map { it.name }
                val fromItems = filterOptionsOrLoad()?.genres.orEmpty()
                (indexed + fromItems)
                    .map { SourceOption(it, it) }
                    .distinctBy { it.value.lowercase() }
                    .sortedBy { it.label }
            }
            CustomSectionSourceType.STUDIO ->
                perLibrary { library ->
                    mediaRepository
                        .getStudiosResult(
                            includeItemTypes = listOf("MOVIE", "SERIES", "BOX_SET"),
                            parentId = library.id,
                            limit = null,
                            requireImages = false,
                            minItemCount = 0,
                        )
                        .map { studios -> studios.map { SourceOption(it.name, it.name) } }
                }
            CustomSectionSourceType.COLLECTION -> loadItemOptions("BOX_SET")
            CustomSectionSourceType.PLAYLIST -> loadItemOptions("PLAYLIST")
            CustomSectionSourceType.TAG ->
                filterOptionsOrLoad()?.let { merged -> merged.tags.map { SourceOption(it, it) } }
            CustomSectionSourceType.LIBRARY ->
                withContext(Dispatchers.IO) {
                    mediaRepository
                        .getLibrariesResult()
                        .getOrElse {
                            Timber.w(it, "Failed to load libraries for custom sections")
                            return@withContext null
                        }
                        .filterNot { it.type == CollectionType.Music }
                        .map { library: AfinityCollection ->
                            SourceOption(
                                value = library.id.toString(),
                                label = library.name,
                                libraryType = library.type,
                            )
                        }
                }
        }

    private suspend fun filterOptionsOrLoad(): LibraryFilterOptions? =
        cachedFilterOptions
            ?: mergedFilterOptions()?.also { merged ->
                cachedFilterOptions = merged
                _uiState.update { state -> state.copy(filterOptions = merged) }
            }

    private suspend fun mergedFilterOptions(): LibraryFilterOptions? =
        withContext(Dispatchers.IO) {
            val libraries =
                mediaRepository
                    .getLibrariesResult()
                    .getOrElse {
                        Timber.w(it, "Failed to load libraries for custom section filters")
                        return@withContext null
                    }
                    .filterNot { it.type == CollectionType.Music }
            if (libraries.isEmpty()) return@withContext LibraryFilterOptions()

            val results = coroutineScope {
                libraries
                    .map { library ->
                        async {
                            mediaRepository.getFilterOptionsResult(
                                parentId = library.id,
                                libraryType = library.type,
                            )
                        }
                    }
                    .awaitAll()
            }
            if (results.all { it.isFailure }) {
                results
                    .firstNotNullOfOrNull { it.exceptionOrNull() }
                    ?.let { Timber.w(it, "Failed to load filter options from every library") }
                return@withContext null
            }

            val loaded = results.mapNotNull { it.getOrNull() }
            LibraryFilterOptions(
                genres = loaded.flatMap { it.genres }.distinct().sortedBy { it.lowercase() },
                tags = loaded.flatMap { it.tags }.distinct().sortedBy { it.lowercase() },
                officialRatings =
                    loaded.flatMap { it.officialRatings }.distinct().sortedBy { it.lowercase() },
                years = loaded.flatMap { it.years }.distinct().sortedDescending(),
            )
        }

    private suspend fun perLibrary(
        fetch: suspend (AfinityCollection) -> Result<List<SourceOption>>
    ): List<SourceOption>? =
        withContext(Dispatchers.IO) {
            val libraries =
                mediaRepository
                    .getLibrariesResult()
                    .getOrElse {
                        Timber.w(it, "Failed to load libraries for custom section sources")
                        return@withContext null
                    }
                    .filterNot { it.type == CollectionType.Music }
            if (libraries.isEmpty()) return@withContext emptyList()

            val results = coroutineScope { libraries.map { async { fetch(it) } }.awaitAll() }
            if (results.all { it.isFailure }) {
                results
                    .firstNotNullOfOrNull { it.exceptionOrNull() }
                    ?.let {
                        Timber.w(it, "Failed to load custom section sources from every library")
                    }
                return@withContext null
            }
            results
                .mapNotNull { it.getOrNull() }
                .flatten()
                .distinctBy { it.value }
                .sortedBy { it.label.lowercase() }
        }

    private suspend fun loadItemOptions(itemType: String): List<SourceOption>? =
        withContext(Dispatchers.IO) {
            mediaRepository
                .getItemsResult(
                    includeItemTypes = listOf(itemType),
                    fields = FieldSets.MEDIA_ITEM_CARDS,
                )
                .getOrElse {
                    Timber.w(it, "Failed to load $itemType options for custom sections")
                    return@withContext null
                }
                .items
                .orEmpty()
                .mapNotNull { dto ->
                    val name = dto.name ?: return@mapNotNull null
                    SourceOption(dto.id.toString(), name)
                }
                .distinctBy { it.value }
                .sortedBy { it.label.lowercase() }
        }

    fun optionsFor(sourceType: CustomSectionSourceType): List<SourceOption> =
        _uiState.value.optionsFor(sourceType)

    fun save(section: CustomHomeSection, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) {
                customHomeSectionsRepository.create(section)
            } else {
                customHomeSectionsRepository.upsert(section)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { customHomeSectionsRepository.delete(id) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { customHomeSectionsRepository.setEnabled(id, enabled) }
    }

    fun move(fromKey: Any?, toKey: Any?) {
        val fromId = fromKey as? String ?: return
        val toId = toKey as? String ?: return
        if (fromId == toId) return
        _uiState.update { state ->
            val fromIndex = state.sections.indexOfFirst { it.id == fromId }
            val toIndex = state.sections.indexOfFirst { it.id == toId }
            if (fromIndex < 0 || toIndex < 0) {
                state
            } else {
                val reordered = state.sections.toMutableList()
                reordered.add(toIndex, reordered.removeAt(fromIndex))
                state.copy(sections = reordered)
            }
        }
    }

    fun commitOrder() {
        val ordered = _uiState.value.sections.map { it.id }
        viewModelScope.launch { customHomeSectionsRepository.reorder(ordered) }
    }

    fun newSectionTemplate(): CustomHomeSection =
        CustomHomeSection(
            id = UUID.randomUUID().toString(),
            position = -1,
            title = "",
            sourceType = CustomSectionSourceType.GENRE,
        )

    fun presetTemplate(preset: SeasonalPreset): CustomHomeSection {
        val state = _uiState.value
        val tagMatch =
            preset.suggestedTag?.let { suggested ->
                state.tagOptions.firstOrNull { it.label.equals(suggested, ignoreCase = true) }
            }
        val genreMatch =
            preset.suggestedGenre?.let { suggested ->
                state.genreOptions.firstOrNull { it.label.equals(suggested, ignoreCase = true) }
            }

        val (sourceType, sourceValues) =
            when {
                tagMatch != null -> CustomSectionSourceType.TAG to listOf(tagMatch.value)
                genreMatch != null -> CustomSectionSourceType.GENRE to listOf(genreMatch.value)
                else -> CustomSectionSourceType.GENRE to emptyList()
            }

        return CustomHomeSection(
            id = UUID.randomUUID().toString(),
            position = -1,
            title = preset.defaultTitle,
            sourceType = sourceType,
            sourceValues = sourceValues,
            cardStyle = CustomSectionCardStyle.SPOTLIGHT,
            randomOrder = true,
            seasonStart = preset.start,
            seasonEnd = preset.end,
        )
    }
}

enum class SeasonalPreset(
    val defaultTitle: String,
    val start: String,
    val end: String,
    val suggestedTag: String?,
    val suggestedGenre: String?,
) {
    HALLOWEEN("Halloween", "10-01", "10-31", "halloween", "Horror"),
    CHRISTMAS("Christmas", "12-01", "12-26", "christmas", null),
    NEW_YEAR("New Year", "12-28", "01-02", "new year", null),
    VALENTINES("Valentine's", "02-07", "02-15", "valentine", "Romance"),
}
