package com.makd.afinity.ui.audiobookshelf

internal fun String.withAbsWidth(px: Int): String {
    if (startsWith("file://")) return this
    val sep = if ('?' in this) "&" else "?"
    return "${this}${sep}width=$px"
}
