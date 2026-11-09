package com.makd.afinity.data.store

import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.AfinityUserDataOwner
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.media.patchedWith
import com.makd.afinity.data.models.media.patchedWithUserData
import com.makd.afinity.data.models.media.withUserDataFrom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.UserItemDataDto
import timber.log.Timber

@Singleton
class ItemStore @Inject constructor() {

    private val _items = MutableStateFlow<Map<UUID, AfinityUserDataOwner>>(emptyMap())

    val overlay: StateFlow<Map<UUID, AfinityUserDataOwner>> = _items.asStateFlow()

    private val _contentIds = MutableStateFlow<Set<UUID>>(emptySet())

    private val lock = Any()
    private val contentStale = HashSet<UUID>()
    private val userDataStale = HashSet<UUID>()
    private val retainedByOwner = HashMap<Any, Set<UUID>>()
    private val retainCounts = HashMap<UUID, Int>()

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
        put(listOf(item))
    }

    fun put(items: Collection<AfinityUserDataOwner>) {
        if (items.isEmpty()) return
        val staleNow = contentStaleSnapshot()
        val replaced = HashSet<UUID>()
        _items.update { current ->
            replaced.clear()
            var changed = false
            val next = current.toMutableMap()
            items.forEach { item ->
                val existing = next[item.id]
                val chosen =
                    chooseIncoming(existing, item, item.id in staleNow, preferIncoming = true)
                if (chosen !== existing) {
                    next.remove(item.id)
                    next[item.id] = chosen
                    changed = true
                    if (chosen === item) replaced += item.id
                }
            }
            if (changed) next else current
        }
        if (replaced.isNotEmpty()) _contentIds.update { it - replaced }
        synchronized(lock) {
            contentStale.removeAll(replaced)
            userDataStale.removeAll(items.mapTo(HashSet()) { it.id })
        }
        afterGrowth()
    }

    fun putLoaded(items: Collection<AfinityUserDataOwner>) {
        if (items.isEmpty()) return
        val staleNow = contentStaleSnapshot()
        val refreshed = HashSet<UUID>()
        _items.update { current ->
            refreshed.clear()
            var changed = false
            val next = current.toMutableMap()
            items.forEach { item ->
                val existing = next[item.id]
                val isStale = item.id in staleNow
                val chosen = chooseIncoming(existing, item, isStale, preferIncoming = false)
                if (chosen !== existing) {
                    next.remove(item.id)
                    next[item.id] = chosen
                    changed = true
                }
                if (isStale && chosen === item) refreshed += item.id
            }
            if (changed) next else current
        }
        if (refreshed.isNotEmpty()) {
            _contentIds.update { it - refreshed }
            synchronized(lock) {
                contentStale.removeAll(refreshed)
                userDataStale.removeAll(refreshed)
            }
        }
        afterGrowth()
    }

    fun putContent(items: Collection<AfinityUserDataOwner>) {
        if (items.isEmpty()) return
        val ids = items.mapTo(HashSet()) { it.id }
        synchronized(lock) {
            contentStale.removeAll(ids)
            userDataStale.removeAll(ids)
        }
        _contentIds.update { it + ids }
        _items.update { current ->
            var changed = false
            val next = current.toMutableMap()
            items.forEach { item ->
                if (next[item.id] !== item) {
                    next.remove(item.id)
                    next[item.id] = item
                    changed = true
                }
            }
            if (changed) next else current
        }
        afterGrowth()
    }

    fun markStale(ids: Collection<UUID>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            contentStale.addAll(ids)
            userDataStale.addAll(ids)
        }
        _contentIds.update { current ->
            if (ids.none { it in current }) current else current - ids.toSet()
        }
    }

    fun markAllStale() {
        val ids = _items.value.keys
        synchronized(lock) {
            contentStale.addAll(ids)
            userDataStale.addAll(ids)
        }
        _contentIds.value = emptySet()
        Timber.d("ItemStore marked ${ids.size} entries stale")
    }

    fun isContentStale(id: UUID): Boolean = synchronized(lock) { id in contentStale }

    fun onScreen(ids: Collection<UUID>): List<UUID> =
        synchronized(lock) { ids.filter { it in retainCounts } }

    fun <T : AfinityItem> merge(items: List<T>): List<T> {
        val overlay = _items.value
        if (overlay.isEmpty()) return items
        val contentIds = _contentIds.value
        val staleNow = userDataStaleSnapshot()
        var changed = false
        val merged = items.map { item ->
            if (item.id in staleNow) return@map item
            val source = overlay[item.id] ?: return@map item
            @Suppress("UNCHECKED_CAST")
            val next =
                if (item.id in contentIds) item.withContentFrom(source) as T
                else item.withUserDataFrom(source) as T
            if (next !== item) changed = true
            next
        }
        return if (changed) merged else items
    }

    fun pagingMerge(item: AfinityItem, source: AfinityUserDataOwner): AfinityItem {
        val (staleContent, staleUserData) =
            synchronized(lock) { (item.id in contentStale) to (item.id in userDataStale) }
        return when {
            staleUserData -> item
            staleContent || source !is AfinityItem || source::class != item::class ->
                item.withUserDataFrom(source)
            else -> source
        }
    }

    private fun AfinityItem.withContentFrom(source: AfinityUserDataOwner): AfinityItem {
        if (source === this) return this
        if (source !is AfinityItem || source::class != this::class) return withUserDataFrom(source)
        if (source.sources.isEmpty() && sources.isNotEmpty()) return withUserDataFrom(source)
        return source
    }

    fun <T : AfinityUserDataOwner> mergeOwner(item: T): T {
        val source = _items.value[item.id] ?: return item
        if (synchronized(lock) { item.id in userDataStale }) return item
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

    fun putUserDataFrom(items: Collection<AfinityItem>) {
        if (items.isEmpty()) return
        _items.update { current ->
            var changed = false
            val next = current.toMutableMap()
            items.forEach { item ->
                val existing = next[item.id]
                val updated =
                    when (existing) {
                        null -> item
                        is AfinityItem -> existing.withUserDataFrom(item)
                        else -> existing
                    }
                if (updated !== existing) {
                    next.remove(item.id)
                    next[item.id] = updated
                    changed = true
                }
            }
            if (changed) next else current
        }
        synchronized(lock) { userDataStale.removeAll(items.mapTo(HashSet()) { it.id }) }
        afterGrowth()
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
        afterGrowth()
    }

    fun applyPatch(id: UUID, patch: UserDataPatch) {
        _items.update { current ->
            val existing = current[id] ?: return@update current
            val patched = existing.patchedWith(patch)
            if (patched === existing) current else current.movedToEnd(id, patched)
        }
    }

    fun applyUserData(id: UUID, data: UserItemDataDto) {
        var applied = false
        _items.update { current ->
            val existing = current[id]
            applied = existing != null
            if (existing == null) return@update current
            val patched = existing.patchedWithUserData(data)
            if (patched === existing) current else current.movedToEnd(id, patched)
        }
        if (applied) synchronized(lock) { userDataStale.remove(id) }
    }

    fun remove(ids: Collection<UUID>) {
        if (ids.isEmpty()) return
        val removed = ids.toSet()
        synchronized(lock) {
            contentStale.removeAll(removed)
            userDataStale.removeAll(removed)
        }
        _contentIds.update { current ->
            if (removed.none { it in current }) current else current - removed
        }
        _items.update { current ->
            if (removed.none { it in current }) current else current - removed
        }
    }

    fun clear() {
        val size = _items.value.size
        if (size > 0) {
            Timber.d("ItemStore cleared at size=$size (peak=$peakSize)")
        }
        loggedMilestone = 0
        synchronized(lock) {
            contentStale.clear()
            userDataStale.clear()
        }
        _contentIds.value = emptySet()
        _items.value = emptyMap()
    }

    fun <S> project(
        scope: CoroutineScope,
        base: StateFlow<S>,
        transform: ItemProjection.(S) -> S,
    ): StateFlow<S> {
        val projection = ItemProjection()
        val token = Any()
        val output = MutableStateFlow(runPass(projection, token, base.value, transform))
        scope.launch {
            try {
                combine(base, _items) { state, _ -> state }
                    .collect { state ->
                        output.value = runPass(projection, token, state, transform)
                    }
            } finally {
                release(token)
            }
        }
        return output.asStateFlow()
    }

    private fun <S> runPass(
        projection: ItemProjection,
        token: Any,
        state: S,
        transform: ItemProjection.(S) -> S,
    ): S {
        projection.begin(_items.value, contentStaleSnapshot(), _contentIds.value)
        val result = projection.transform(state)
        val pass = projection.end()
        retain(token, pass.touched)
        if (pass.fresh.isNotEmpty()) putLoaded(pass.fresh)
        return result
    }

    private fun retain(owner: Any, ids: Set<UUID>) {
        synchronized(lock) {
            val previous = retainedByOwner[owner].orEmpty()
            if (previous == ids) return
            (ids - previous).forEach { retainCounts[it] = (retainCounts[it] ?: 0) + 1 }
            (previous - ids).forEach { id ->
                val count = (retainCounts[id] ?: 0) - 1
                if (count <= 0) retainCounts.remove(id) else retainCounts[id] = count
            }
            retainedByOwner[owner] = ids
        }
    }

    private fun release(owner: Any) {
        retain(owner, emptySet())
        synchronized(lock) { retainedByOwner.remove(owner) }
    }

    private fun contentStaleSnapshot(): Set<UUID> =
        synchronized(lock) { if (contentStale.isEmpty()) emptySet() else HashSet(contentStale) }

    private fun userDataStaleSnapshot(): Set<UUID> =
        synchronized(lock) { if (userDataStale.isEmpty()) emptySet() else HashSet(userDataStale) }

    private fun afterGrowth() {
        logGrowth()
        if (_items.value.size <= MAX_SIZE) return
        val victims =
            synchronized(lock) {
                val excess = _items.value.size - TRIM_TARGET
                _items.value.keys.asSequence().filter { it !in retainCounts }.take(excess).toSet()
            }
        if (victims.isEmpty()) return
        remove(victims)
        Timber.d("ItemStore evicted ${victims.size} unobserved entries")
    }

    private fun chooseIncoming(
        existing: AfinityUserDataOwner?,
        incoming: AfinityUserDataOwner,
        isStale: Boolean,
        preferIncoming: Boolean,
    ): AfinityUserDataOwner {
        if (existing == null) return incoming
        if (existing === incoming) return existing
        if (existing !is AfinityItem || incoming !is AfinityItem) {
            return if (isStale || preferIncoming) incoming else existing
        }
        if (existing::class != incoming::class) return if (isStale) incoming else existing
        val incomingRichness = incoming.richness()
        val existingRichness = existing.richness()
        return when {
            incomingRichness < existingRichness ->
                if (isStale || preferIncoming) existing.withUserDataFrom(incoming) else existing
            isStale || preferIncoming -> incoming
            incomingRichness > existingRichness -> incoming.withUserDataFrom(existing)
            else -> existing
        }
    }

    private companion object {
        const val SIZE_LOG_INTERVAL = 100
        const val MAX_SIZE = 2_000
        const val TRIM_TARGET = 1_500
    }
}

private fun Map<UUID, AfinityUserDataOwner>.movedToEnd(
    id: UUID,
    value: AfinityUserDataOwner,
): Map<UUID, AfinityUserDataOwner> =
    LinkedHashMap(this).apply {
        remove(id)
        put(id, value)
    }

internal fun AfinityItem.richness(): Int =
    (if (sources.any { it.type != AfinitySourceType.LOCAL }) 4 else 0) +
        (if (externalUrls != null) 2 else 0) +
        (if (overview.isNotBlank()) 1 else 0)
