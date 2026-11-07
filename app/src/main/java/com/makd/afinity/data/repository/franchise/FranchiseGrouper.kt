package com.makd.afinity.data.repository.franchise

import com.makd.afinity.data.models.franchise.FranchiseGroup
import com.makd.afinity.data.models.franchise.FranchiseTitle
import com.makd.afinity.data.models.franchise.WikidataFranchiseFacts

object FranchiseGrouper {

    const val MIN_TITLES = 2

    private const val FRANCHISE_PROP = "P8345"
    private val SPIN_OFF_PROPS = setOf("P2512", "P4969")
    private val LIST_TYPES = setOf("Q13406463")
    private val BLOCKED_GROUPS = setOf("Q56070713", "Q26196748", "Q2405799", "Q906232")
    private val NAME_SUFFIXES = listOf(" in film", " film series")
    private val YEAR_SUFFIX = Regex("\\s*\\(\\d{4}\\)")
    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")
    private val STOP_WORDS =
        setOf(
            "the",
            "a",
            "an",
            "of",
            "and",
            "movie",
            "film",
            "series",
            "show",
            "season",
            "part",
            "presents",
            "chapter",
            "collection",
            "new",
            "return",
            "returns",
        )

    fun normalizeImdbId(raw: String): String {
        val trimmed = raw.trim().lowercase()
        return if (trimmed.startsWith("tt")) trimmed else "tt$trimmed"
    }

    fun group(
        titles: List<FranchiseTitle>,
        facts: WikidataFranchiseFacts,
        collectionTmdbIds: Set<String>,
    ): List<FranchiseGroup> {
        val byKey = LinkedHashMap<String, FranchiseTitle>()
        titles.forEach { byKey.putIfAbsent(it.key, it) }

        val entityTitles = LinkedHashMap<String, LinkedHashSet<String>>()
        val groupTitles = LinkedHashMap<String, LinkedHashSet<String>>()
        val groupProps = HashMap<String, MutableSet<String>>()
        for (key in byKey.keys) {
            val title = facts.titles[key] ?: continue
            title.entityId?.let { entityTitles.getOrPut(it) { LinkedHashSet() }.add(key) }
            for (ref in title.groups) {
                groupTitles.getOrPut(ref.entityId) { LinkedHashSet() }.add(key)
                groupProps.getOrPut(ref.entityId) { HashSet() }.add(ref.property)
            }
        }
        groupTitles.keys.removeAll { group ->
            group in BLOCKED_GROUPS ||
                facts.entities[group]?.types.orEmpty().any { it in LIST_TYPES }
        }

        val parentOf = HashMap<String, String>()
        for (group in groupTitles.keys) {
            facts.entities[group]
                ?.parents
                ?.firstOrNull { it != group && it in groupTitles }
                ?.let { parentOf[group] = it }
        }
        val merged = LinkedHashMap<String, LinkedHashSet<String>>()
        for ((group, keys) in groupTitles) {
            merged.getOrPut(rootOf(group, parentOf)) { LinkedHashSet() }.addAll(keys)
        }

        val bySet = LinkedHashMap<Set<String>, String>()
        for ((group, keys) in merged) {
            if (keys.size < MIN_TITLES) continue
            val current = bySet[keys]
            val isFranchise = FRANCHISE_PROP in groupProps[group].orEmpty()
            if (
                current == null || (isFranchise && FRANCHISE_PROP !in groupProps[current].orEmpty())
            ) {
                bySet[keys] = group
            }
        }

        val results = LinkedHashMap<String, Candidate>()
        val sets = bySet.keys.toList()
        for (keys in sets) {
            if (sets.any { it.size > keys.size && it.containsAll(keys) }) continue
            val group = bySet.getValue(keys)
            val label = facts.entities[group]?.label ?: continue
            results.getOrPut(label) { Candidate(group, LinkedHashSet()) }.keys.addAll(keys)
        }

        for (component in linkedComponents(byKey, facts, entityTitles)) {
            val keys = component.flatMapTo(LinkedHashSet()) { entityTitles.getValue(it) }
            val overlapping =
                results.values.filter { candidate -> candidate.keys.any { it in keys } }
            overlapping.forEach { it.keys.addAll(keys) }
            if (overlapping.isNotEmpty() || keys.size < MIN_TITLES) continue
            val oldest =
                keys
                    .map { byKey.getValue(it) }
                    .minWith(
                        compareBy<FranchiseTitle>({ it.year ?: Int.MAX_VALUE }, { it.name.length })
                    )
            val rootEntity = component.first { oldest.key in entityTitles.getValue(it) }
            val name = oldest.name.replace(YEAR_SUFFIX, "").trim()
            results.getOrPut(name) { Candidate(rootEntity, LinkedHashSet()) }.keys.addAll(keys)
        }

        val collectionSets =
            byKey.values
                .filter { it.tmdbCollectionId != null && it.tmdbCollectionId in collectionTmdbIds }
                .groupBy { it.tmdbCollectionId }
                .values
                .mapTo(HashSet()) { members -> members.mapTo(HashSet()) { it.key } }

        return results.mapNotNull { (label, candidate) ->
            if (candidate.keys in collectionSets) return@mapNotNull null
            FranchiseGroup(
                id = candidate.entityId,
                name = cleanName(label),
                titleKeys =
                    candidate.keys.sortedWith(
                        compareBy(
                            { byKey.getValue(it).year ?: Int.MAX_VALUE },
                            { byKey.getValue(it).name },
                        )
                    ),
                logo = facts.entities[candidate.entityId]?.logo,
            )
        }
    }

    private class Candidate(val entityId: String, val keys: LinkedHashSet<String>)

    private fun rootOf(group: String, parentOf: Map<String, String>): String {
        val seen = HashSet<String>()
        var current = group
        while (seen.add(current)) {
            current = parentOf[current] ?: return current
        }
        return current
    }

    private fun linkedComponents(
        byKey: Map<String, FranchiseTitle>,
        facts: WikidataFranchiseFacts,
        entityTitles: Map<String, Set<String>>,
    ): List<List<String>> {
        val parent = HashMap<String, String>()
        entityTitles.keys.forEach { parent[it] = it }
        fun find(entity: String): String {
            var current = entity
            while (parent.getValue(current) != current) {
                parent[current] = parent.getValue(parent.getValue(current))
                current = parent.getValue(current)
            }
            return current
        }
        fun nameOf(entity: String): String =
            byKey.getValue(entityTitles.getValue(entity).first()).name

        for (key in byKey.keys) {
            val title = facts.titles[key] ?: continue
            val source = title.entityId ?: continue
            for (link in title.links) {
                val target = link.entityId
                if (target == source || target !in entityTitles) continue
                val accepted =
                    link.property in SPIN_OFF_PROPS ||
                        tokens(nameOf(source)).any { it in tokens(nameOf(target)) }
                if (accepted) parent[find(source)] = find(target)
            }
        }
        return entityTitles.keys.groupBy { find(it) }.values.filter { it.size > 1 }
    }

    private fun tokens(name: String): Set<String> =
        name.lowercase().replace(YEAR_SUFFIX, " ").split(WORD_SPLIT).filterTo(HashSet()) {
            it.length >= 3 && it !in STOP_WORDS
        }

    private fun cleanName(label: String): String {
        val suffix = NAME_SUFFIXES.firstOrNull { label.endsWith(it, ignoreCase = true) }
        return if (suffix == null) label else label.dropLast(suffix.length).trim()
    }
}
