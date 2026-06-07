package com.makd.afinity.util

import java.util.UUID

fun parseDashlessUuid(noDashesStr: String): UUID {
    require(noDashesStr.length == 32) { "Invalid UUID string length: must be 32 characters" }

    val hyphenatedStr = buildString(36) {
        append(noDashesStr)
        insert(8, "-")
        insert(13, "-")
        insert(18, "-")
        insert(23, "-")
    }

    return UUID.fromString(hyphenatedStr)
}