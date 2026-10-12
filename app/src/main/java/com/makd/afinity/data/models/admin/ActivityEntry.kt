package com.makd.afinity.data.models.admin

import java.time.LocalDateTime
import java.util.UUID

data class ActivityEntry(
    val id: Long,
    val title: String,
    val detail: String?,
    val type: String,
    val date: LocalDateTime,
    val isError: Boolean,
    val userId: UUID?,
)

data class ActivityToday(
    val plays: Int,
    val people: Int,
    val signIns: Int,
    val failedSignIns: Int,
    val isPartial: Boolean,
)

enum class ActivityFilter(val hasUserId: Boolean?, val type: String?) {
    ALL(hasUserId = null, type = null),
    PLAYBACK(hasUserId = null, type = "Playback"),
    PEOPLE(hasUserId = true, type = null),
    SIGN_INS(hasUserId = null, type = "AuthenticationSucceeded"),
    FAILED_SIGN_INS(hasUserId = null, type = "AuthenticationFailed"),
}
