package com.makd.afinity.data.models.admin

data class DeleteSummary(
    val path: String?,
    val childCount: Int,
    val recursiveItemCount: Int,
)
