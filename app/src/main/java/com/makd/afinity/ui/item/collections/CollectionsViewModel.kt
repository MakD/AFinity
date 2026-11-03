package com.makd.afinity.ui.item.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.admin.CollectionChoice
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.admin.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionsState(
    val collections: List<CollectionChoice> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val result: CollectionResult? = null,
)

sealed interface CollectionResult {
    data class Added(val collectionName: String) : CollectionResult

    data class Created(val collectionId: String, val collectionName: String) : CollectionResult

    data class Removed(val collectionName: String) : CollectionResult

    data object Failed : CollectionResult
}

@HiltViewModel
class CollectionsViewModel
@Inject
constructor(
    private val adminRepository: AdminRepository,
    private val appDataRepository: AppDataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CollectionsState())
    val state: StateFlow<CollectionsState> = _state.asStateFlow()

    fun loadCollections() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val collections = adminRepository.getCollections().getOrDefault(emptyList())
            _state.update { it.copy(collections = collections, isLoading = false) }
        }
    }

    fun add(collection: CollectionChoice, itemIds: List<String>) =
        submit(CollectionResult.Added(collection.name)) {
            adminRepository.addToCollection(collection.id, itemIds)
        }

    fun remove(collectionId: String, collectionName: String, itemIds: List<String>) =
        submit(CollectionResult.Removed(collectionName)) {
            adminRepository.removeFromCollection(collectionId, itemIds)
        }

    fun create(name: String, itemIds: List<String>, searchMetadata: Boolean) {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = adminRepository.createCollection(name, itemIds, searchMetadata)
            val id = result.getOrNull()
            if (id != null) appDataRepository.reloadLibraries()
            _state.update {
                it.copy(
                    isSubmitting = false,
                    result =
                        if (id != null) CollectionResult.Created(id, name)
                        else CollectionResult.Failed,
                )
            }
        }
    }

    fun clearResult() = _state.update { it.copy(result = null) }

    private fun submit(success: CollectionResult, action: suspend () -> Result<Unit>) {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val ok = action().isSuccess
            _state.update {
                it.copy(
                    isSubmitting = false,
                    result = if (ok) success else CollectionResult.Failed,
                )
            }
        }
    }
}
