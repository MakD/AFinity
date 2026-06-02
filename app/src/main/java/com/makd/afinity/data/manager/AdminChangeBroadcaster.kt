package com.makd.afinity.data.manager

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminChangeBroadcaster @Inject constructor() {
    private val _itemChanged = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val itemChanged: SharedFlow<String> = _itemChanged.asSharedFlow()

    fun notifyItemChanged(itemId: String) {
        _itemChanged.tryEmit(itemId)
    }
}