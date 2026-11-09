package com.makd.afinity.data.store

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface QueryKey {
    data class BoxSetChildren(val boxSetId: UUID) : QueryKey

    data class ContainingBoxSets(val itemId: UUID) : QueryKey

    data class PlaylistEntries(val playlistId: UUID) : QueryKey
}

@Singleton
class QueryInvalidator @Inject constructor() {
    private val _invalidations = MutableSharedFlow<Set<QueryKey>>(extraBufferCapacity = 16)
    val invalidations: SharedFlow<Set<QueryKey>> = _invalidations.asSharedFlow()

    fun invalidate(keys: Set<QueryKey>) {
        if (keys.isNotEmpty()) _invalidations.tryEmit(keys)
    }
}
