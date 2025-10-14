package com.makd.afinity.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

enum class FilterType {
    ALL, WATCHED, UNWATCHED, WATCHLIST, FAVORITES
}
@HiltViewModel
class LibraryContentViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val libraryId: String = checkNotNull(savedStateHandle["libraryId"])
    private val libraryName: String = checkNotNull(savedStateHandle["libraryName"])

    private val _uiState = MutableStateFlow(
        LibraryContentUiState(
            libraryId = UUID.fromString(libraryId),
            libraryName = libraryName.replace("%2F", "/")
        )
    )
    val uiState: StateFlow<LibraryContentUiState> = _uiState.asStateFlow()

    private val _pagingData = MutableStateFlow<Flow<PagingData<AfinityItem>>>(emptyFlow())
    val pagingData: StateFlow<Flow<PagingData<AfinityItem>>> = _pagingData.asStateFlow()

    private var currentSortBy = SortBy.NAME

    private var currentSortDescending = false

    private val _scrollToIndex = MutableStateFlow(-1)
    val scrollToIndex: StateFlow<Int> = _scrollToIndex.asStateFlow()

    private var libraryType: CollectionType? = null

    private var currentFilter = FilterType.ALL

    init {
        loadLibraryContent()
    }

    private suspend fun loadUserProfileImage(): String? {
        return try {
            jellyfinRepository.getUserProfileImageUrl()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user profile image URL")
            null
        }
    }

    private suspend fun determineLibraryType(): CollectionType {
        return try {
            val libraries = jellyfinRepository.getLibraries()
            val library = libraries.find { it.id.toString() == libraryId }
            Timber.d("Library '$libraryName' has type: ${library?.type}")
            library?.type ?: CollectionType.Mixed
        } catch (e: Exception) {
            Timber.w(e, "Failed to determine library type, falling back to name detection")
            when {
                libraryName.contains("TV", ignoreCase = true) ||
                        libraryName.contains("Shows", ignoreCase = true) ||
                        libraryName.contains("Series", ignoreCase = true) -> CollectionType.TvShows
                libraryName.contains("Movie", ignoreCase = true) -> CollectionType.Movies
                else -> CollectionType.Mixed
            }
        }
    }

    private fun loadItems() {
        val type = libraryType ?: return

        _pagingData.value = jellyfinRepository.getItemsPaging(
            parentId = UUID.fromString(libraryId),
            libraryType = type,
            sortBy = currentSortBy,
            sortDescending = currentSortDescending,
            filter = currentFilter,
            nameStartsWith = null
        ).cachedIn(viewModelScope)
    }

    private fun loadLibraryContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val userProfileImageUrl = loadUserProfileImage()
                val type = determineLibraryType()
                libraryType = type

                _uiState.value = _uiState.value.copy(
                    libraryType = type,
                    userProfileImageUrl = userProfileImageUrl,
                    currentSortBy = currentSortBy,
                    currentSortDescending = currentSortDescending,
                    currentFilter = currentFilter,
                    isLoading = false
                )

                loadItems()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load library content")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load library: ${e.message}"
                )
            }
        }
    }

    fun updateFilter(filterType: FilterType) {
        if (currentFilter != filterType) {
            currentFilter = filterType
            _uiState.value = _uiState.value.copy(currentFilter = currentFilter)
            loadItems()
        }
    }

    fun updateSort(sortBy: SortBy, descending: Boolean) {
        if (currentSortBy != sortBy || currentSortDescending != descending) {
            currentSortBy = sortBy
            currentSortDescending = descending
            _uiState.value = _uiState.value.copy(
                currentSortBy = currentSortBy,
                currentSortDescending = currentSortDescending
            )
            loadItems()
        }
    }

    fun onItemClick(item: AfinityItem) {
        Timber.d("Item clicked: ${item.name} (${item.id})")
        // TODO: Navigate to item detail screen
    }

    fun resetScrollIndex() {
        _scrollToIndex.value = -1
    }

    fun scrollToLetter(letter: String) {
        viewModelScope.launch {
            try {
                val type = libraryType ?: return@launch

                val letterFilter = when (letter) {
                    "#" -> "0"
                    else -> letter
                }

                _pagingData.value = jellyfinRepository.getItemsPaging(
                    parentId = UUID.fromString(libraryId),
                    libraryType = type,
                    sortBy = currentSortBy,
                    sortDescending = currentSortDescending,
                    filter = currentFilter,
                    nameStartsWith = letterFilter
                ).cachedIn(viewModelScope)

                Timber.d("Alphabet scroll: Created new paging source for letter '$letter'")

            } catch (e: Exception) {
                Timber.e(e, "Failed to scroll to letter $letter")
            }
        }
    }
}

data class LibraryContentUiState(
    val libraryId: UUID,
    val libraryName: String,
    val libraryType: CollectionType? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfileImageUrl: String? = null,
    val currentSortBy: SortBy = SortBy.NAME,
    val currentSortDescending: Boolean = false,
    val currentFilter: FilterType = FilterType.ALL
)