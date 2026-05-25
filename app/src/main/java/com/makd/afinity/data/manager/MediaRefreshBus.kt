package com.makd.afinity.data.manager

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class RefreshTrigger {
    USER_DATA_CHANGED,
    LIBRARY_CHANGED,
}

@Singleton
class MediaRefreshBus @Inject constructor() {
    private val _events = MutableSharedFlow<RefreshTrigger>(extraBufferCapacity = 8)
    val events: SharedFlow<RefreshTrigger> = _events.asSharedFlow()

    fun emit(trigger: RefreshTrigger) {
        _events.tryEmit(trigger)
    }
}