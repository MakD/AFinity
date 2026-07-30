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

data class SourceOption(val value: String, val label: String)

data class CustomSectionsUiState(
    val sections: List<CustomHomeSection> = emptyList(),
    val genreOptions: List<SourceOption> = emptyList(),
    val studioOptions: List<SourceOption> = emptyList(),
    val collectionOptions: List<SourceOption> = emptyList(),
    val playlistOptions: List<SourceOption> = emptyList(),
    val libraryOptions: List<SourceOption> = emptyList(),
    val tagOptions: List<SourceOption> = emptyList(),
    val isLoadingSources: Boolean = false,
    val canAddMore: Boolean = true,
    val hiddenRows: Set<HomeRow> = emptySet(),
    val discovery: DiscoveryConfig = DiscoveryConfig(),
)

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
        loadSources()
    }

    fun setRowVisible(row: HomeRow, visible: Boolean) {
        viewModelScope.launch { homeLayoutPreferencesRepository.setRowVisible(row, visible) }
    }

    fun setDensity(density: DiscoveryDensity) {
        viewModelScope.launch { homeLayoutPreferencesRepository.setDiscoveryDensity(density) }
    }

    fun setDiscoveryAuto(section: DiscoverySection) {
        viewModelScope.launch {
            homeLayoutPreferencesRepository.setDiscoverySection(
                section,
                enabled = true,
                maxCount = null,
            )
        }
    }

    fun setDiscoveryOff(section: DiscoverySection) {
        viewModelScope.launch {
            homeLayoutPreferencesRepository.setDiscoverySection(
                section,
                enabled = false,
                maxCount = null,
            )
        }
    }

    fun setDiscoveryCount(section: DiscoverySection, count: Int) {
        viewModelScope.launch {
            homeLayoutPreferencesRepository.setDiscoverySection(
                section,
                enabled = true,
                maxCount = count,
            )
        }
    }

    private fun loadSources() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSources = true) }
            try {
                val genres =
                    genreRepository.combinedGenres.value
                        .map { SourceOption(it.name, it.name) }
                        .distinctBy { it.value }
                        .sortedBy { it.label }

                val studios =
                    withContext(Dispatchers.IO) {
                        try {
                            mediaRepository.getStudios(limit = 200).map {
                                SourceOption(it.name, it.name)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load studios for custom sections")
                            emptyList()
                        }
                    }

                val collections = loadItemOptions("BOX_SET")
                val playlists = loadItemOptions("PLAYLIST")

                val tags =
                    withContext(Dispatchers.IO) {
                        try {
                            mediaRepository
                                .getFilterOptions(
                                    parentId = null,
                                    libraryType = CollectionType.Unknown,
                                )
                                .tags
                                .distinct()
                                .sorted()
                                .map { SourceOption(it, it) }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load tags for custom sections")
                            emptyList()
                        }
                    }

                val libraries =
                    withContext(Dispatchers.IO) {
                        try {
                            mediaRepository.getLibraries().map { library: AfinityCollection ->
                                SourceOption(library.id.toString(), library.name)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load libraries for custom sections")
                            emptyList()
                        }
                    }

                _uiState.update {
                    it.copy(
                        genreOptions = genres,
                        studioOptions = studios,
                        collectionOptions = collections,
                        playlistOptions = playlists,
                        libraryOptions = libraries,
                        tagOptions = tags,
                        isLoadingSources = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load custom section sources")
                _uiState.update { it.copy(isLoadingSources = false) }
            }
        }
    }

    private suspend fun loadItemOptions(itemType: String): List<SourceOption> =
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
                emptyList()
            }
        }

    fun optionsFor(sourceType: CustomSectionSourceType): List<SourceOption> {
        val state = _uiState.value
        return when (sourceType) {
            CustomSectionSourceType.GENRE -> state.genreOptions
            CustomSectionSourceType.STUDIO -> state.studioOptions
            CustomSectionSourceType.COLLECTION -> state.collectionOptions
            CustomSectionSourceType.PLAYLIST -> state.playlistOptions
            CustomSectionSourceType.LIBRARY -> state.libraryOptions
            CustomSectionSourceType.TAG -> state.tagOptions
        }
    }

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

    fun move(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.sections.indices || toIndex !in state.sections.indices) {
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
