package com.makd.afinity.ui.admin.metadata

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.admin.EditableItem
import com.makd.afinity.data.models.admin.EditablePerson
import com.makd.afinity.data.repository.admin.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditMetadataUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val item: EditableItem? = null,
    val edited: EditableItem? = null,
    val saveSuccess: Boolean? = null,
    val error: String? = null,
)

@HiltViewModel
class EditMetadataViewModel
@Inject
constructor(
    private val adminRepository: AdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(EditMetadataUiState())
    val uiState: StateFlow<EditMetadataUiState> = _uiState.asStateFlow()

    init {
        loadItem()
    }

    private fun loadItem() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val item = adminRepository.getEditableItem(itemId)
            if (item != null) {
                _uiState.update { it.copy(loading = false, item = item, edited = item) }
            } else {
                _uiState.update { it.copy(loading = false, error = "Failed to load item") }
            }
        }
    }

    fun updateName(value: String) = update { copy(name = value) }
    fun updateOriginalTitle(value: String) = update { copy(originalTitle = value.ifBlank { null }) }
    fun updateOverview(value: String) = update { copy(overview = value.ifBlank { null }) }
    fun updateYear(value: String) = update { copy(productionYear = value.toIntOrNull()) }
    fun updateOfficialRating(value: String) = update { copy(officialRating = value.ifBlank { null }) }
    fun updateCustomRating(value: String) = update { copy(customRating = value.ifBlank { null }) }
    fun updateCommunityRating(value: String) = update { copy(communityRating = value.toDoubleOrNull()) }
    fun updateStatus(value: String) = update { copy(status = value.ifBlank { null }) }
    fun updateDisplayOrder(value: String) = update { copy(displayOrder = value.ifBlank { null }) }
    fun toggleLockData() = update { copy(lockData = !lockData) }

    fun addGenre(genre: String) = update { copy(genres = genres + genre) }
    fun removeGenre(genre: String) = update { copy(genres = genres - genre) }

    fun addTag(tag: String) = update { copy(tags = tags + tag) }
    fun removeTag(tag: String) = update { copy(tags = tags - tag) }

    fun addStudio(studio: String) = update { copy(studios = studios + studio) }
    fun removeStudio(studio: String) = update { copy(studios = studios - studio) }

    fun addPerson(person: EditablePerson) = update { copy(people = people + person) }
    fun removePerson(index: Int) = update { copy(people = people.toMutableList().also { it.removeAt(index) }) }

    fun toggleLockedField(field: String) = update {
        val updated = if (field in lockedFields) lockedFields - field else lockedFields + field
        copy(lockedFields = updated)
    }

    fun save() {
        val edited = _uiState.value.edited ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            val result = adminRepository.updateItemMetadata(itemId, edited)
            _uiState.update {
                it.copy(
                    saving = false,
                    saveSuccess = result.isSuccess,
                    error = if (result.isFailure) result.exceptionOrNull()?.message else null,
                )
            }
        }
    }

    val isDirty: Boolean
        get() = _uiState.value.item != _uiState.value.edited

    private fun update(block: EditableItem.() -> EditableItem) {
        _uiState.update { state ->
            state.copy(edited = state.edited?.block())
        }
    }
}