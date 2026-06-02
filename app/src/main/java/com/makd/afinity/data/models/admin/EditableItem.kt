package com.makd.afinity.data.models.admin

data class EditableItem(
    val id: String,
    val name: String,
    val originalTitle: String?,
    val overview: String?,
    val productionYear: Int?,
    val premiereDate: String?,
    val officialRating: String?,
    val customRating: String?,
    val communityRating: Double?,
    val genres: List<String>,
    val tags: List<String>,
    val studios: List<String>,
    val people: List<EditablePerson>,
    val indexNumber: Int?,
    val parentIndexNumber: Int?,
    val status: String?,
    val displayOrder: String?,
    val lockData: Boolean,
    val lockedFields: List<String>,
    val type: String,
    val path: String?,
    val availableParentalRatings: List<String> = emptyList(),
)

data class EditablePerson(
    val id: String?,
    val name: String,
    val type: String,
    val role: String?,
)