package com.makd.afinity.util

object ItemIds {

    fun normalize(raw: String): String? =
        raw.trim().replace("-", "").lowercase().takeIf { it.length == 32 }

    fun canonical(raw: String): String? =
        normalize(raw)?.let {
            buildString {
                append(it, 0, 8)
                append('-')
                append(it, 8, 12)
                append('-')
                append(it, 12, 16)
                append('-')
                append(it, 16, 20)
                append('-')
                append(it, 20, 32)
            }
        }
}
