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
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.GenreRepository
import com.makd.afinity.data.repository.home.CustomHomeSectionsRepository
import com.makd.afinity.data.repository.home.HomeLayoutPreferencesRepository
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

    private val _uiState = MutableStateFlow(CustomSectionsUiState())
    val uiState: StateFlow<CustomSectionsUiState> = _uiState.asStateFlow()

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

    fun ensureSourcesLoaded(sourceType: CustomSectionSourceType, force: Boolean = false) {
        val state = _uiState.value.stateFor(sourceType)
        if (state == SourceLoadState.LOADING) return
        if (!force && state == SourceLoadState.LOADED) return
        loadSourcesFor(sourceType)
    }

    private fun setSourceState(sourceType: CustomSectionSourceType, state: SourceLoadState) {
        _uiState.update { it.copy(sourceStates = it.sourceStates + (sourceType to state)) }
    }

    private fun loadSourcesFor(sourceType: CustomSectionSourceType) {
        viewModelScope.launch {
            setSourceState(sourceType, SourceLoadState.LOADING)
            try {
                val options = fetchSourceOptions(sourceType)
                if (options == null) {
                    setSourceState(sourceType, SourceLoadState.FAILED)
                    return@launch
                }
                _uiState.update {
                    val next =
                        when (sourceType) {
                            CustomSectionSourceType.GENRE -> it.copy(genreOptions = options)
                            CustomSectionSourceType.STUDIO -> it.copy(studioOptions = options)
                            CustomSectionSourceType.COLLECTION ->
                                it.copy(collectionOptions = options)
                            CustomSectionSourceType.PLAYLIST -> it.copy(playlistOptions = options)
                            CustomSectionSourceType.LIBRARY -> it.copy(libraryOptions = options)
                            CustomSectionSourceType.TAG -> it.copy(tagOptions = options)
                        }
                    next.copy(
                        sourceStates = next.sourceStates + (sourceType to SourceLoadState.LOADED)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load $sourceType options for custom sections")
                setSourceState(sourceType, SourceLoadState.FAILED)
            }
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
                genreRepository.combinedGenres.value
                    .map { SourceOption(it.name, it.name) }
                    .distinctBy { it.value }
                    .sortedBy { it.label }
            }
            CustomSectionSourceType.STUDIO ->
                withContext(Dispatchers.IO) {
                    try {
                        mediaRepository
                            .getStudios(includeItemTypes = listOf("MOVIE", "SERIES"), limit = 200)
                            .map { SourceOption(it.name, it.name) }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load studios for custom sections")
                        null
                    }
                }
            CustomSectionSourceType.COLLECTION -> loadItemOptions("BOX_SET")
            CustomSectionSourceType.PLAYLIST -> loadItemOptions("PLAYLIST")
            CustomSectionSourceType.TAG ->
                withContext(Dispatchers.IO) {
                    try {
                        mediaRepository
                            .getFilterOptions(parentId = null, libraryType = CollectionType.Unknown)
                            .tags
                            .distinct()
                            .sorted()
                            .map { SourceOption(it, it) }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load tags for custom sections")
                        null
                    }
                }
            CustomSectionSourceType.LIBRARY ->
                withContext(Dispatchers.IO) {
                    try {
                        mediaRepository
                            .getLibraries()
                            .filterNot { it.type == CollectionType.Music }
                            .map { library: AfinityCollection ->
                                SourceOption(
                                    value = library.id.toString(),
                                    label = library.name,
                                    libraryType = library.type,
                                )
                            }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load libraries for custom sections")
                        null
                    }
                }
        }

    private suspend fun loadItemOptions(itemType: String): List<SourceOption>? =
        withContext(Dispatchers.IO) {
            try {
                mediaRepository
                    .getItems(
                        includeItemTypes = listOf(itemType),
                        fields = FieldSets.MEDIA_ITEM_CARDS,
                    )
                    .items
                    ?.mapNotNull { dto ->
                        val name = dto.name ?: return@mapNotNull null
                        SourceOption(dto.id.toString(), name)
                    }
                    ?.sortedBy { it.label } ?: emptyList()
            } catch (e: Exception) {
                Timber.w(e, "Failed to load $itemType options for custom sections")
                null
            }
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
