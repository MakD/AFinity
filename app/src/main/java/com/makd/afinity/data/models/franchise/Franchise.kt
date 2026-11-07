package com.makd.afinity.data.models.franchise

import com.makd.afinity.data.models.media.AfinityItem
import kotlinx.serialization.Serializable

@Serializable
data class Franchise(
    val id: String,
    val name: String,
    val itemIds: List<String>,
    val movieCount: Int,
    val seriesCount: Int,
    val logoUrl: String?,
)

data class FranchiseCard(
    val franchise: Franchise,
    val backdropItem: AfinityItem?,
    val logoItem: AfinityItem?,
)
