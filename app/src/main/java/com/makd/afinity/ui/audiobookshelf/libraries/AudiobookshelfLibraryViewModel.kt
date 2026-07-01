package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.repository.AudiobookshelfConfig
import com.makd.afinity.data.repository.AudiobookshelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class AudiobookshelfLibraryViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val audiobookshelfRepository: AudiobookshelfRepository,
) : ViewModel() {

    val libraryId: String = checkNotNull(savedStateHandle["libraryId"])
    val libraryName: String =
        URLDecoder.decode(checkNotNull(savedStateHandle["libraryName"]), "UTF-8")

    val currentConfig: StateFlow<AudiobookshelfConfig?> = audiobookshelfRepository.currentConfig

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())

    private val _selectedLetter = MutableStateFlow<String?>(null)
    val selectedLetter: StateFlow<String?> = _selectedLetter.asStateFlow()

    private val _filteredItems = MutableStateFlow<List<LibraryItem>?>(null)

    private val progressMap: StateFlow<Map<String, MediaProgress>> =
        audiobookshelfRepository
            .getAllProgressFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val displayItems: StateFlow<List<LibraryItem>> =
        combine(_items, _filteredItems, progressMap) { items, filtered, progress ->
                enrichItems(filtered ?: items, progress)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val result = audiobookshelfRepository.refreshLibraryItems(libraryId)
            result.fold(
                onSuccess = { items -> _items.value = items },
                onFailure = { error ->
                    Timber.e(error, "Failed to load items for library $libraryId")
                },
            )
            _isLoading.value = false
        }
    }

    fun onLetterSelected(letter: String) {
        val newLetter = if (_selectedLetter.value == letter) null else letter
        _selectedLetter.value = newLetter
        _filteredItems.value =
            if (newLetter == null) {
                null
            } else {
                _items.value.filter { item ->
                    val firstChar = item.media.metadata.title?.firstOrNull()?.uppercase() ?: ""
                    if (newLetter == "#") {
                        firstChar.isNotEmpty() && !firstChar[0].isLetter()
                    } else {
                        firstChar == newLetter
                    }
                }
            }
    }

    private fun enrichItems(
        items: List<LibraryItem>,
        progressMap: Map<String, MediaProgress>,
    ): List<LibraryItem> {
        if (progressMap.isEmpty()) return items
        return items.map { item ->
            if (item.userMediaProgress != null) item
            else progressMap[item.id]?.let { item.copy(userMediaProgress = it) } ?: item
        }
    }
}