package com.makd.afinity.data.models.wikidata

import kotlinx.serialization.Serializable

@Serializable
data class SparqlResponse(val results: SparqlResults = SparqlResults())

@Serializable
data class SparqlResults(val bindings: List<Map<String, SparqlValue>> = emptyList())

@Serializable data class SparqlValue(val value: String? = null)