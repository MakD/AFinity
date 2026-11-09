package com.makd.afinity.data.store

import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityUserDataOwner
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.models.media.patchedWith
import com.makd.afinity.data.models.media.withUserDataFrom
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

class ItemProjection internal constructor() {

    internal class Pass(val fresh: List<AfinityUserDataOwner>, val touched: Set<UUID>)

    private var snapshot: Map<UUID, AfinityUserDataOwner> = emptyMap()
    private var staleIds: Set<UUID> = emptySet()
    private var contentIds: Set<UUID> = emptySet()
    private var previousSeen: MutableSet<Any> = identitySet()
    private var currentSeen: MutableSet<Any> = identitySet()
    private var previousResults = IdentityHashMap<Any, Any>()
    private var currentResults = IdentityHashMap<Any, Any>()
    private val fresh = ArrayList<AfinityUserDataOwner>()
    private val touched = HashSet<UUID>()

    internal fun begin(
        snapshot: Map<UUID, AfinityUserDataOwner>,
        staleIds: Set<UUID>,
        contentIds: Set<UUID>,
    ) {
        this.snapshot = snapshot
        this.staleIds = staleIds
        this.contentIds = contentIds
        currentSeen = identitySet()
        currentResults = IdentityHashMap()
        fresh.clear()
        touched.clear()
    }

    internal fun end(): Pass {
        val pass = Pass(ArrayList(fresh), HashSet(touched))
        previousSeen = currentSeen
        previousResults = currentResults
        return pass
    }

    fun <T : AfinityItem> live(item: T): T {
        val isFresh = note(item)
        if (isFresh && item.id in staleIds) return item
        return resolve(item)
    }

    fun <T : AfinityItem> liveOrNull(item: T?): T? = item?.let { live(it) }

    fun <T : AfinityItem> live(list: List<T>): List<T> {
        if (list.isEmpty()) return list
        val resolved = list.map { live(it) }
        return remembered(list, resolved) { it.sameElementsAs(resolved) }
    }

    fun <T : AfinityUserDataOwner> liveOwners(list: List<T>): List<T> {
        if (list.isEmpty()) return list
        val resolved = list.map { owner ->
            val isFresh = note(owner)
            if (isFresh && owner.id in staleIds) owner else resolveOwner(owner)
        }
        return remembered(list, resolved) { it.sameElementsAs(resolved) }
    }

    fun <T : AfinityUserDataOwner> liveOwner(owner: T): T {
        val isFresh = note(owner)
        if (isFresh && owner.id in staleIds) return owner
        return resolveOwner(owner)
    }

    fun <E : Any, I : AfinityUserDataOwner> liveEach(
        list: List<E>,
        inner: (E) -> I,
        rebuild: (E, I) -> E,
    ): List<E> {
        if (list.isEmpty()) return list
        val resolved = list.map { element ->
            val original = inner(element)
            val current = liveOwner(original)
            if (current === original) {
                element
            } else {
                @Suppress("UNCHECKED_CAST") val previous = previousResults[element] as? E
                val next =
                    if (previous != null && inner(previous) === current) previous
                    else rebuild(element, current)
                currentResults[element] = next
                next
            }
        }
        return remembered(list, resolved) { it.sameElementsAs(resolved) }
    }

    fun <E : Any, T : AfinityItem> liveNested(
        list: List<E>,
        items: (E) -> List<T>,
        rebuild: (E, List<T>) -> E,
    ): List<E> {
        if (list.isEmpty()) return list
        val resolved = list.map { element ->
            val original = items(element)
            val current = live(original)
            if (current === original) {
                element
            } else {
                @Suppress("UNCHECKED_CAST") val previous = previousResults[element] as? E
                val next =
                    if (previous != null && items(previous).sameElementsAs(current)) previous
                    else rebuild(element, current)
                currentResults[element] = next
                next
            }
        }
        return remembered(list, resolved) { it.sameElementsAs(resolved) }
    }

    fun <E : Any, T : AfinityUserDataOwner> liveNestedOwners(
        list: List<E>,
        items: (E) -> List<T>,
        rebuild: (E, List<T>) -> E,
    ): List<E> {
        if (list.isEmpty()) return list
        val resolved = list.map { element ->
            val original = items(element)
            val current = liveOwners(original)
            if (current.sameElementsAs(original)) {
                element
            } else {
                @Suppress("UNCHECKED_CAST") val previous = previousResults[element] as? E
                val next =
                    if (previous != null && items(previous).sameElementsAs(current)) previous
                    else rebuild(element, current)
                currentResults[element] = next
                next
            }
        }
        return remembered(list, resolved) { it.sameElementsAs(resolved) }
    }

    fun <K, T : AfinityItem> liveValues(map: Map<K, List<T>>): Map<K, List<T>> {
        if (map.isEmpty()) return map
        val resolved = map.mapValues { (_, list) -> live(list) }
        val unchanged = map.all { (key, list) -> resolved[key] === list }
        if (unchanged) {
            currentResults[map] = map
            return map
        }
        @Suppress("UNCHECKED_CAST") val previous = previousResults[map] as? Map<K, List<T>>
        val result =
            if (
                previous != null &&
                    previous.size == resolved.size &&
                    resolved.all { (key, list) -> previous[key] === list }
            )
                previous
            else resolved
        currentResults[map] = result
        return result
    }

    private fun <T> remembered(
        base: List<T>,
        resolved: List<T>,
        matchesPrevious: (List<T>) -> Boolean,
    ): List<T> {
        if (base.sameElementsAs(resolved)) {
            currentResults[base] = base
            return base
        }
        @Suppress("UNCHECKED_CAST") val previous = previousResults[base] as? List<T>
        val result = if (previous != null && matchesPrevious(previous)) previous else resolved
        currentResults[base] = result
        return result
    }

    private fun note(owner: AfinityUserDataOwner): Boolean {
        touched += owner.id
        val isFresh = owner !in previousSeen
        if (currentSeen.add(owner) && isFresh) fresh += owner
        return isFresh
    }

    private fun <T : AfinityItem> resolve(item: T): T {
        val source = snapshot[item.id] ?: return item
        if (source === item) return item
        @Suppress("UNCHECKED_CAST")
        return if (
            source is AfinityItem &&
                source::class == item::class &&
                source.richness() >= item.richness()
        ) {
            source as T
        } else {
            item.withUserDataFrom(source) as T
        }
    }

    private fun <T : AfinityUserDataOwner> resolveOwner(owner: T): T {
        if (owner is AfinityItem) {
            @Suppress("UNCHECKED_CAST")
            return resolve(owner) as T
        }
        val source = snapshot[owner.id] ?: return owner
        if (source === owner) return owner
        if (owner.id in contentIds && source::class == owner::class) {
            @Suppress("UNCHECKED_CAST")
            return source as T
        }
        @Suppress("UNCHECKED_CAST")
        return owner.patchedWith(
            UserDataPatch(
                played = source.played,
                favorite = source.favorite,
                liked = source.liked,
                playbackPositionTicks = source.playbackPositionTicks,
            )
        ) as T
    }

    private fun <T> List<T>.sameElementsAs(other: List<T>): Boolean =
        this === other || size == other.size && indices.all { this[it] === other[it] }

    private companion object {
        fun identitySet(): MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
    }
}
