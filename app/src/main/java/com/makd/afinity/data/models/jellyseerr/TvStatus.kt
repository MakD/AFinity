package com.makd.afinity.data.models.jellyseerr

enum class TvStatus(val value: Int) {
    RETURNING_SERIES(0),
    PLANNED(1),
    IN_PRODUCTION(2),
    ENDED(3),
    CANCELLED(4),
    PILOT(5),
}