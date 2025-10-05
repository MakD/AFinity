package com.makd.afinity.ui.episode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.makd.afinity.data.paging.EpisodesPagingSource
import com.makd.afinity.data.repository.media.MediaRepository
import kotlinx.coroutines.flow.Flow
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.extensions.toAfinitySeason
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.models.extensions.toAfinityEpisode
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
    private val mediaRepository: MediaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val seasonId: UUID = UUID.fromString(
        savedStateHandle.get<String>("seasonId") ?: throw IllegalArgumentException("seasonId is required")
    )

    private val seasonName: String = savedStateHandle.get<String>("seasonName") ?: "Episodes"

    private val _uiState = MutableStateFlow(EpisodeListUiState())
    val uiState: StateFlow<EpisodeListUiState> = _uiState.asStateFlow()

    private val _episodesPagingData = MutableStateFlow<Flow<PagingData<AfinityEpisode>>>(
        Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false,
                initialLoadSize = 50
            )
        ) {
            EpisodesPagingSource(
                mediaRepository = mediaRepository,
                seasonId = seasonId,
                seriesId = UUID.randomUUID()
            )
        }.flow.cachedIn(viewModelScope)
    )
    val episodesPagingData: StateFlow<Flow<PagingData<AfinityEpisode>>> = _episodesPagingData.asStateFlow()

    init {
        loadSeasonData()
    }

    private fun loadSeasonData() {
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

                val firstEpisode = jellyfinRepository.getEpisodes(seasonId, seriesId, null).firstOrNull()

                val season = AfinitySeason(
                    id = seasonId,
                    name = seasonName.replace("%2F", "/"),
                    seriesId = seriesId,
                    seriesName = firstEpisode?.seriesName ?: "",
                    originalTitle = seasonData?.originalTitle,
                    overview = seasonData?.overview ?: "",
                    sources = emptyList(),
                    indexNumber = firstEpisode?.parentIndexNumber ?: 1,
                    episodes = emptyList(),
                    episodeCount = seasonData?.episodeCount ?: 0,
                    productionYear = seasonData?.productionYear,
                    premiereDate = seasonData?.premiereDate,
                    played = seasonData?.played ?: false,
                    favorite = seasonData?.favorite ?: false,
                    canPlay = true,
                    canDownload = false,
                    unplayedItemCount = seasonData?.unplayedItemCount,
                    images = seasonData?.images ?: AfinityImages(),
                    chapters = emptyList(),
                    providerIds = seasonData?.providerIds,
                    externalUrls = seasonData?.externalUrls
                )

                _uiState.value = _uiState.value.copy(
                    season = season,
                    isLoading = false
                )

                _episodesPagingData.value = Pager(
                    config = PagingConfig(
                        pageSize = 50,
                        enablePlaceholders = false,
                        initialLoadSize = 50
                    )
                ) {
                    EpisodesPagingSource(
                        mediaRepository = mediaRepository,
                        seasonId = seasonId,
                        seriesId = seriesId
                    )
                }.flow.cachedIn(viewModelScope)

                loadSpecialFeatures(seasonId)

            } catch (e: Exception) {
                Timber.e(e, "Failed to load season data: $seasonId")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load season: ${e.message}"
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

    suspend fun getFullEpisodeDetails(episodeId: UUID): AfinityEpisode? {
        return try {
            Timber.d("Fetching full episode details with media sources for: $episodeId")
            val baseItemDto = jellyfinRepository.getItem(episodeId, fields = FieldSets.PLAYER)
            baseItemDto?.toAfinityEpisode(jellyfinRepository.getBaseUrl())
        } catch (e: Exception) {
            Timber.e(e, "Failed to get full episode details for: $episodeId")
            null
        }
    }
}

data class EpisodeListUiState(
    val season: AfinitySeason? = null,
    val specialFeatures: List<AfinityItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)