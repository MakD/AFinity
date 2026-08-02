package com.makd.afinity.data.manager

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AdminChangeKind {
    IMAGES,
    METADATA,
    DELETED,
}

data class AdminChange(val itemId: String, val kind: AdminChangeKind)

data class ItemDeletedEvent(
    val itemId: String,
    val tmdbId: Int? = null,
    val isMovie: Boolean? = null,
)

@Singleton
class AdminChangeBroadcaster @Inject constructor() {
    private val _changes = MutableSharedFlow<AdminChange>(extraBufferCapacity = 8)
    val changes: SharedFlow<AdminChange> = _changes.asSharedFlow()

    val itemChanged: Flow<String> = _changes.map { it.itemId }

    private val _itemDeleted = MutableSharedFlow<ItemDeletedEvent>(extraBufferCapacity = 16)
    val itemDeleted: SharedFlow<ItemDeletedEvent> = _itemDeleted.asSharedFlow()

    fun notifyItemChanged(itemId: String) {
        _changes.tryEmit(AdminChange(itemId, AdminChangeKind.METADATA))
    }

    fun notifyImagesChanged(itemId: String) {
        _changes.tryEmit(AdminChange(itemId, AdminChangeKind.IMAGES))
    }

    fun notifyItemDeleted(itemId: String) {
        _changes.tryEmit(AdminChange(itemId, AdminChangeKind.DELETED))
    }

    fun notifyItemDeleted(itemId: String, tmdbId: Int? = null, isMovie: Boolean? = null) {
        _changes.tryEmit(AdminChange(itemId, AdminChangeKind.DELETED))
        _itemDeleted.tryEmit(ItemDeletedEvent(itemId, tmdbId, isMovie))
    }
}