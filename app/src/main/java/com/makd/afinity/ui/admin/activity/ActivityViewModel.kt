package com.makd.afinity.ui.admin.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertSeparators
import androidx.paging.map
import com.makd.afinity.data.models.admin.ActivityEntry
import com.makd.afinity.data.models.admin.ActivityFilter
import com.makd.afinity.data.models.admin.ActivityToday
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface ActivityListItem {
    data class Day(val date: LocalDate) : ActivityListItem

    data class Entry(val entry: ActivityEntry) : ActivityListItem
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _filter =
        MutableStateFlow(
            ActivityFilter.entries.firstOrNull { it.name == savedStateHandle.get<String>("filter") }
                ?: ActivityFilter.ALL
        )
    val filter: StateFlow<ActivityFilter> = _filter.asStateFlow()

    private val _hasNewActivity = MutableStateFlow(false)
    val hasNewActivity: StateFlow<Boolean> = _hasNewActivity.asStateFlow()

    val items: Flow<PagingData<ActivityListItem>> =
        _filter
            .flatMapLatest { adminRepository.getActivityPaging(it) }
            .map { pagingData -> pagingData.withDayHeaders() }
            .cachedIn(viewModelScope)

    private val _today = MutableStateFlow<ActivityToday?>(null)
    val today: StateFlow<ActivityToday?> = _today.asStateFlow()

    init {
        viewModelScope.launch {
            adminRepository.activityLogChanges().collect { _hasNewActivity.value = true }
        }
        loadToday()
    }

    fun toggleFilter(filter: ActivityFilter) {
        _filter.value = if (_filter.value == filter) ActivityFilter.ALL else filter
        _hasNewActivity.value = false
    }

    fun onRefreshed() {
        _hasNewActivity.value = false
        loadToday()
    }

    private fun loadToday() {
        viewModelScope.launch { adminRepository.getTodayActivity().onSuccess { _today.value = it } }
    }

    private fun PagingData<ActivityEntry>.withDayHeaders(): PagingData<ActivityListItem> {
        val seen = HashSet<Long>()
        return this.filter { seen.add(it.id) }
            .map { ActivityListItem.Entry(it) }
            .insertSeparators<ActivityListItem.Entry, ActivityListItem> { before, after ->
                val day = after?.entry?.date?.toLocalDate()
                if (day != null && day != before?.entry?.date?.toLocalDate()) {
                    ActivityListItem.Day(day)
                } else {
                    null
                }
            }
    }
}
