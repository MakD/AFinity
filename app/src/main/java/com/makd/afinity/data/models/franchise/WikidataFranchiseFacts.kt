package com.makd.afinity.data.models.franchise

data class WikidataRef(val entityId: String, val property: String)

data class WikidataTitleFacts(
    val entityId: String?,
    val groups: List<WikidataRef>,
    val links: List<WikidataRef>,
)

data class WikidataEntityFacts(
    val label: String?,
    val types: Set<String>,
    val parents: List<String>,
    val logo: String?,
)

data class WikidataFranchiseFacts(
    val titles: Map<String, WikidataTitleFacts>,
    val entities: Map<String, WikidataEntityFacts>,
)

data class FranchiseTitle(
    val key: String,
    val itemId: String,
    val name: String,
    val year: Int?,
    val isSeries: Boolean,
    val tmdbCollectionId: String?,
)

data class FranchiseGroup(
    val id: String,
    val name: String,
    val titleKeys: List<String>,
    val logo: String?,
)
