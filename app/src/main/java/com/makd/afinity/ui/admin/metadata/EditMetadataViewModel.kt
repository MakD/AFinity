package com.makd.afinity.ui.admin.metadata

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.AdminRequestStillRunningException
import com.makd.afinity.data.models.admin.EditableItem
import com.makd.afinity.data.models.admin.EditablePerson
import com.makd.afinity.data.repository.admin.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val RATING_INPUT = Regex("^\\d{0,2}(\\.\\d{0,2})?$")
private const val MAX_COMMUNITY_RATING = 10.0

enum class ChipField {
    GENRES,
    TAGS,
    STUDIOS,
}

enum class EditMetadataMessage(@param:StringRes val textRes: Int) {
    SAVE_FAILED(R.string.admin_metadata_save_failed),
    SAVE_STILL_RUNNING(R.string.admin_metadata_save_still_running),
}

data class EditMetadataUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val item: EditableItem? = null,
    val edited: EditableItem? = null,
    val yearText: String = "",
    val communityRatingText: String = "",
    val pendingChips: Map<ChipField, String> = emptyMap(),
    val message: EditMetadataMessage? = null,
) {
    val isDirty: Boolean
        get() = item != null && (item != edited || pendingChips.values.any { it.isNotBlank() })

    val titleMissing: Boolean
        get() = edited != null && edited.name.isBlank()

    val displayOrderChanged: Boolean
        get() = !item?.displayOrder.orEmpty().equals(edited?.displayOrder.orEmpty(), true)
}

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

    fun loadItem() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loadFailed = false) }
            val item = adminRepository.getEditableItem(itemId)
            _uiState.update {
                if (item == null) {
                    it.copy(loading = false, loadFailed = true)
                } else {
                    it.copy(
                        loading = false,
                        item = item,
                        edited = item,
                        yearText = item.productionYear?.toString().orEmpty(),
                        communityRatingText = ratingText(item.communityRating),
                        pendingChips = emptyMap(),
                    )
                }
            }
        }
    }

    fun updateName(value: String) = update { copy(name = value) }

    fun updateOriginalTitle(value: String) = update { copy(originalTitle = value.ifBlank { null }) }

    fun updateOverview(value: String) = update { copy(overview = value.ifBlank { null }) }

    fun updateYear(value: String) {
        val digits = value.filter(Char::isDigit).take(4)
        _uiState.update {
            it.copy(
                yearText = digits,
                edited = it.edited?.copy(productionYear = digits.toIntOrNull()),
            )
        }
    }

    fun updateOfficialRating(value: String) = update {
        copy(officialRating = value.ifBlank { null })
    }

    fun updateCustomRating(value: String) = update { copy(customRating = value.ifBlank { null }) }

    fun updateCommunityRating(value: String) {
        val text = value.replace(',', '.')
        if (!RATING_INPUT.matches(text)) return
        val rating = text.toDoubleOrNull()
        if (rating != null && rating > MAX_COMMUNITY_RATING) return
        _uiState.update {
            it.copy(
                communityRatingText = text,
                edited = it.edited?.copy(communityRating = rating),
            )
        }
    }

    fun updateStatus(value: String?) = update { copy(status = value) }

    fun updateDisplayOrder(value: String?) = update { copy(displayOrder = value?.ifBlank { null }) }

    fun toggleLockData() = update { copy(lockData = !lockData) }

    fun updatePendingChip(field: ChipField, text: String) = _uiState.update {
        it.copy(pendingChips = it.pendingChips + (field to text))
    }

    fun commitChip(field: ChipField) {
        _uiState.update { state ->
            val text = state.pendingChips[field]?.trim().orEmpty()
            val edited = state.edited
            if (text.isEmpty() || edited == null) {
                state.copy(pendingChips = state.pendingChips - field)
            } else {
                val current = edited.chips(field)
                val updated =
                    if (current.any { it.equals(text, ignoreCase = true) }) current
                    else current + text
                state.copy(
                    edited = edited.withChips(field, updated),
                    pendingChips = state.pendingChips - field,
                )
            }
        }
    }

    fun removeChip(field: ChipField, value: String) = update {
        withChips(field, chips(field) - value)
    }

    fun addPerson(person: EditablePerson) = update { copy(people = people + person) }

    fun updatePerson(index: Int, person: EditablePerson) = update {
        if (index in people.indices) {
            copy(people = people.toMutableList().also { it[index] = person })
        } else this
    }

    fun removePerson(index: Int) = update {
        if (index in people.indices) {
            copy(people = people.toMutableList().also { it.removeAt(index) })
        } else this
    }

    fun toggleLockedField(field: String) = update {
        val updated = if (field in lockedFields) lockedFields - field else lockedFields + field
        copy(lockedFields = updated)
    }

    fun save() {
        ChipField.entries.forEach(::commitChip)
        val state = _uiState.value
        val edited = state.edited ?: return
        if (state.saving || edited.name.isBlank() || state.item == edited) return
        val toSave = edited.copy(name = edited.name.trim())
        _uiState.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val result = adminRepository.updateItemMetadata(itemId, toSave)
            _uiState.update {
                when {
                    result.isSuccess -> it.copy(saving = false, saved = true, item = it.edited)
                    result.exceptionOrNull() is AdminRequestStillRunningException ->
                        it.copy(
                            saving = false,
                            item = it.edited,
                            message = EditMetadataMessage.SAVE_STILL_RUNNING,
                        )

                    else -> it.copy(saving = false, message = EditMetadataMessage.SAVE_FAILED)
                }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun update(block: EditableItem.() -> EditableItem) {
        _uiState.update { state -> state.copy(edited = state.edited?.block()) }
    }

    private fun EditableItem.chips(field: ChipField): List<String> =
        when (field) {
            ChipField.GENRES -> genres
            ChipField.TAGS -> tags
            ChipField.STUDIOS -> studios
        }

    private fun EditableItem.withChips(field: ChipField, values: List<String>): EditableItem =
        when (field) {
            ChipField.GENRES -> copy(genres = values)
            ChipField.TAGS -> copy(tags = values)
            ChipField.STUDIOS -> copy(studios = values)
        }

    private fun ratingText(rating: Double?): String =
        when {
            rating == null -> ""
            rating % 1.0 == 0.0 -> rating.toInt().toString()
            else -> rating.toString()
        }
}
