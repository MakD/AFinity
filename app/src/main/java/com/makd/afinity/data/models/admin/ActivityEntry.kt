package com.makd.afinity.data.models.admin

import java.time.LocalDateTime

data class ActivityEntry(
    val id: Long,
    val title: String,
    val detail: String?,
    val type: String,
    val date: LocalDateTime,
    val isError: Boolean,
)

enum class ActivityFilter(val hasUserId: Boolean?, val type: String?) {
    ALL(hasUserId = null, type = null),
    ALERTS(hasUserId = false, type = null),
    SIGN_INS(hasUserId = null, type = "Authentication"),
    PLAYBACK(hasUserId = null, type = "Playback"),
}
