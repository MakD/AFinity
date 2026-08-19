package com.makd.afinity.data.network

import com.makd.afinity.data.models.wikidata.AwardResult
import com.makd.afinity.data.models.wikidata.SparqlResponse
import com.makd.afinity.data.models.wikidata.SparqlValue
import com.makd.afinity.data.models.wikidata.WikidataAward
import com.makd.afinity.data.models.wikidata.WikidataAwards
import com.makd.afinity.data.models.wikidata.WikidataSubjectType

object WikidataAwardParser {

    private val QID_PATTERN = Regex("^Q\\d+$")

    private data class GroupKey(val name: String, val result: AwardResult, val year: Int?)

    fun parse(response: SparqlResponse, subjectType: WikidataSubjectType): WikidataAwards {
        val isPerson = subjectType == WikidataSubjectType.PERSON
        val detailKey = if (isPerson) "workLabel" else "personLabel"

        val grouped = LinkedHashMap<GroupKey, MutableList<String>>()

        for (row in response.results.bindings) {
            val name = row.literal("awardLabel") ?: continue
            val result = row.literal("result")?.toAwardResult() ?: continue
            if (QID_PATTERN.matches(name)) continue

            val year = row.literal("year")?.toIntOrNull()
            val details = grouped.getOrPut(GroupKey(name, result, year)) { mutableListOf() }

            val detail = row.literal(detailKey) ?: continue
            if (QID_PATTERN.matches(detail)) continue
            if (detail !in details) details.add(detail)
        }

        val wonKeys =
            grouped.keys
                .filter { it.result == AwardResult.WON }
                .map { it.name to it.year }
                .toSet()

        for ((key, details) in grouped) {
            if (key.result != AwardResult.NOMINATED) continue
            if ((key.name to key.year) !in wonKeys) continue
            val wonDetails = grouped[key.copy(result = AwardResult.WON)] ?: continue
            details.forEach { if (it !in wonDetails) wonDetails.add(it) }
        }

        val awards =
            grouped
                .filterNot { (key, _) ->
                    key.result == AwardResult.NOMINATED && (key.name to key.year) in wonKeys
                }
                .map { (key, details) ->
                    WikidataAward(
                        name = key.name,
                        result = key.result,
                        year = key.year,
                        recipients = if (isPerson) emptyList() else details.toList(),
                        works = if (isPerson) details.toList() else emptyList(),
                    )
                }
                .sortedWith(
                    compareByDescending<WikidataAward> { it.year ?: 0 }
                        .thenBy { if (it.result == AwardResult.WON) 0 else 1 }
                        .thenBy { it.name }
                )

        return WikidataAwards(awards = awards, confirmed = true)
    }

    private fun Map<String, SparqlValue>.literal(key: String): String? =
        this[key]?.value?.takeIf { it.isNotBlank() }

    private fun String.toAwardResult(): AwardResult? =
        when (this) {
            WikidataAwardQueries.RESULT_WON -> AwardResult.WON
            WikidataAwardQueries.RESULT_NOMINATED -> AwardResult.NOMINATED
            else -> null
        }
}