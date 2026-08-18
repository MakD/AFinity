package com.makd.afinity.data.store

import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityUserDataOwner
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.media.patchedWith
import com.makd.afinity.data.models.media.patchedWithUserData
import com.makd.afinity.data.models.media.withUserDataFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.model.api.UserItemDataDto
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemStore @Inject constructor() {

    private val _items = MutableStateFlow<Map<UUID, AfinityUserDataOwner>>(emptyMap())

    val overlay: StateFlow<Map<UUID, AfinityUserDataOwner>> = _items.asStateFlow()

    private var loggedMilestone = 0
    private var peakSize = 0

    private fun logGrowth() {
        val size = _items.value.size
        if (size > peakSize) peakSize = size
        val milestone = size / SIZE_LOG_INTERVAL
        if (milestone > loggedMilestone) {
            loggedMilestone = milestone
            val byType =
                _items.value.values
                    .groupingBy { it.javaClass.simpleName }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .joinToString(", ") { "${it.key}=${it.value}" }
            Timber.d("ItemStore size=$size peak=$peakSize [$byType]")
        }
    }

    fun get(id: UUID): AfinityUserDataOwner? = _items.value[id]

    inline fun <reified T : AfinityUserDataOwner> getAs(id: UUID): T? = get(id) as? T

    fun observe(id: UUID): Flow<AfinityUserDataOwner?> =
        _items.map { it[id] }.distinctUntilChanged()

    fun observe(ids: Collection<UUID>): Flow<List<AfinityUserDataOwner>> {
        val wanted = ids.toSet()
        return _items.map { current -> wanted.mapNotNull { current[it] } }.distinctUntilChanged()
    }

    fun put(item: AfinityUserDataOwner) {
        _items.update { current ->
            if (current[item.id] === item) current else current + (item.id to item)
        }
        logGrowth()
    }

    fun put(items: Collection<AfinityUserDataOwner>) {
        if (items.isEmpty()) return
        _items.update { current ->
            var changed = false
            val next = current.toMutableMap()
            items.forEach { item ->
                if (next[item.id] !== item) {
                    next[item.id] = item
                    changed = true
                }
            }
            if (changed) next else current
        }
        logGrowth()
    }

    fun <T : AfinityItem> merge(items: List<T>): List<T> {
        val overlay = _items.value
        if (overlay.isEmpty()) return items
        var changed = false
        val merged = items.map { item ->
            val source = overlay[item.id] ?: return@map item
            @Suppress("UNCHECKED_CAST") val next = item.withUserDataFrom(source) as T
            if (next !== item) changed = true
            next
        }
        return if (changed) merged else items
    }

    fun <T : AfinityUserDataOwner> mergeOwner(item: T): T {
        val source = _items.value[item.id] ?: return item
        val patch =
            UserDataPatch(
                played = source.played,
                favorite = source.favorite,
                liked = source.liked,
                playbackPositionTicks = source.playbackPositionTicks,
            )
        @Suppress("UNCHECKED_CAST")
        return item.patchedWith(patch) as T
    }

    fun <T : AfinityUserDataOwner> mergeOwners(items: List<T>): List<T> {
        if (_items.value.isEmpty()) return items
        var changed = false
        val merged = items.map { item ->
            val next = mergeOwner(item)
            if (next !== item) changed = true
            next
        }
        return if (changed) merged else items
    }

    fun putIfAbsent(items: Collection<AfinityUserDataOwner>) {
        if (items.isEmpty()) return
        _items.update { current ->
            var changed = false
            val next = current.toMutableMap()
            items.forEach { item ->
                if (item.id !in next) {
                    next[item.id] = item
                    changed = true
                }
            }
            if (changed) next else current
        }
        logGrowth()
    }

    fun applyPatch(id: UUID, patch: UserDataPatch) {
        _items.update { current ->
            val existing = current[id] ?: return@update current
            val patched = existing.patchedWith(patch)
            if (patched === existing) current else current + (id to patched)
        }
    }

    fun applyUserData(id: UUID, data: UserItemDataDto) {
        _items.update { current ->
            val existing = current[id] ?: return@update current
            val patched = existing.patchedWithUserData(data)
            if (patched === existing) current else current + (id to patched)
        }
    }

    fun remove(ids: Collection<UUID>) {
        if (ids.isEmpty()) return
        _items.update { current ->
            if (ids.none { it in current }) current else current - ids.toSet()
        }
    }

    fun clear() {
        val size = _items.value.size
        if (size > 0) {
            Timber.d("ItemStore cleared at size=$size (peak=$peakSize)")
        }
        loggedMilestone = 0
        _items.value = emptyMap()
    }

    private companion object {
        const val SIZE_LOG_INTERVAL = 100
    }
}
