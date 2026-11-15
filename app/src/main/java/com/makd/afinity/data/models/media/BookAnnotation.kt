package com.makd.afinity.data.models.media

enum class BookAnnotationKind {
    BOOKMARK,
    HIGHLIGHT,
}

enum class HighlightColor(val argb: Int) {
    YELLOW(0xFFFFD54F.toInt()),
    GREEN(0xFF81C784.toInt()),
    BLUE(0xFF64B5F6.toInt()),
    PINK(0xFFF06292.toInt());

    companion object {
        fun fromName(name: String?): HighlightColor? = entries.firstOrNull { it.name == name }
    }
}

data class BookAnnotation(
    val id: String,
    val kind: BookAnnotationKind,
    val locatorJson: String?,
    val page: Int?,
    val progression: Double,
    val label: String?,
    val text: String?,
    val color: HighlightColor?,
    val createdAt: Long,
)
