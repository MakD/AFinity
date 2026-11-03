package com.makd.afinity.ui.music.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.MusicAdminActions
import com.makd.afinity.data.models.admin.DeleteSummary
import com.makd.afinity.ui.item.DeleteOutcome
import com.makd.afinity.ui.item.toDeleteOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MusicAdminViewModel @Inject constructor(private val actions: MusicAdminActions) :
    ViewModel() {

    val isAdmin: StateFlow<Boolean> =
        actions.isAdmin.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _deletableIds = MutableStateFlow<Set<UUID>>(emptySet())
    val deletableIds: StateFlow<Set<UUID>> = _deletableIds.asStateFlow()

    fun checkCanDelete(itemId: UUID) {
        if (itemId in _deletableIds.value) return
        viewModelScope.launch {
            if (actions.canDelete(itemId)) _deletableIds.update { it + itemId }
        }
    }

    suspend fun deleteSummary(itemId: UUID): DeleteSummary? = actions.deleteSummary(itemId)

    fun delete(itemId: UUID, onResult: (DeleteOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = actions.delete(itemId).toDeleteOutcome()
            if (outcome == DeleteOutcome.DELETED) _deletableIds.update { it - itemId }
            onResult(outcome)
        }
    }
}
