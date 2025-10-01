package com.makd.afinity.ui.episode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.extensions.toAfinitySeason
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EpisodeListViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val seasonId: UUID = UUID.fromString(
        savedStateHandle.get<String>("seasonId") ?: throw IllegalArgumentException("seasonId is required")
    )

    private val seasonName: String = savedStateHandle.get<String>("seasonName") ?: "Episodes"

    private val _uiState = MutableStateFlow(EpisodeListUiState())
    val uiState: StateFlow<EpisodeListUiState> = _uiState.asStateFlow()

    init {
        loadEpisodes()
    }

    private fun loadEpisodes() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val seasonData = jellyfinRepository.getItem(seasonId)?.toAfinitySeason(jellyfinRepository.getBaseUrl())

                val seriesId = seasonData?.seriesId ?: run {
                    Timber.e("Could not find seriesId for season: $seasonId")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Could not find series information for this season"
                    )
                    return@launch
                }

                val rawEpisodes = jellyfinRepository.getEpisodes(seasonId, seriesId)

                val episodes = rawEpisodes.mapNotNull { episode ->
                    try {
                        val fullEpisode = jellyfinRepository.getItemById(episode.id) as? AfinityEpisode
                        if (fullEpisode != null) {
                            Timber.d("Loaded episode with ${fullEpisode.sources.size} sources: ${fullEpisode.name}")
                            fullEpisode
                        } else {
                            Timber.w("Could not load full episode details for: ${episode.name}")
                            episode
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load full details for episode ${episode.name}")
                        episode
                    }
                }

                val season = seasonData.copy(
                    id = seasonId,
                    name = seasonName.replace("%2F", "/"),
                    seriesId = episodes.firstOrNull()?.seriesId ?: UUID.randomUUID(),
                    seriesName = episodes.firstOrNull()?.seriesName ?: "",
                    originalTitle = null,
                    overview = "",
                    sources = emptyList(),
                    indexNumber = episodes.firstOrNull()?.parentIndexNumber ?: 1,
                    episodes = episodes,
                    episodeCount = episodes.size,
                    productionYear = null,
                    premiereDate = episodes.minByOrNull { it.indexNumber }?.premiereDate,
                    played = episodes.all { it.played },
                    favorite = false,
                    canPlay = true,
                    canDownload = false,
                    unplayedItemCount = episodes.count { !it.played },
                    images = episodes.firstOrNull()?.images ?: AfinityImages(),
                    chapters = emptyList(),
                    providerIds = null,
                    externalUrls = null
                )

                _uiState.value = _uiState.value.copy(
                    season = season,
                    episodes = episodes,
                    isLoading = false
                )

                loadSpecialFeatures(seasonId)

            } catch (e: Exception) {
                Timber.e(e, "Failed to load episodes for season: $seasonId")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load episodes: ${e.message}"
                )
            }
        }
    }

    private suspend fun loadSpecialFeatures(seasonId: UUID) {
        try {
            val userId = getCurrentUserId() ?: return
            Timber.d("Loading special features for season: $seasonId, user: $userId")
            val specialFeatures = jellyfinRepository.getSpecialFeatures(seasonId, userId)
            Timber.d("Found ${specialFeatures.size} special features for season")
            specialFeatures.forEach { feature ->
                Timber.d("Season special feature: ${feature.name} (${feature.javaClass.simpleName})")
            }
            _uiState.value = _uiState.value.copy(specialFeatures = specialFeatures)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load special features for season: $seasonId")
        }
    }

    private suspend fun getCurrentUserId(): UUID? {
        return try {
            jellyfinRepository.getCurrentUser()?.id
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user ID")
            null
        }
    }
}

data class EpisodeListUiState(
    val season: AfinitySeason? = null,
    val episodes: List<AfinityEpisode> = emptyList(),
    val specialFeatures: List<AfinityItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)