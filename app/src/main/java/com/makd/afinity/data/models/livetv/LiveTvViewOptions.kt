package com.makd.afinity.data.models.livetv

enum class ChannelSortOrder(val value: String) {
    NUMBER("number"),
    NAME("name"),
    RECENTLY_WATCHED("recently_watched");

    companion object {
        fun fromValue(value: String): ChannelSortOrder =
            entries.find { it.value == value } ?: NUMBER
    }
}

enum class GuideIndicator(val value: String) {
    LIVE("live"),
    NEW("new"),
    PREMIERE("premiere"),
    REPEAT("repeat"),
    HD("hd");

    companion object {
        fun fromValue(value: String): GuideIndicator? = entries.find { it.value == value }
    }
}

data class LiveTvViewOptions(
    val channelSort: ChannelSortOrder = ChannelSortOrder.NUMBER,
    val favoritesFirst: Boolean = true,
    val favoritesOnly: Boolean = false,
    val guideCategories: Set<ProgramCategory> = ProgramCategory.entries.toSet(),
    val colorCodedBackgrounds: Boolean = false,
    val indicators: Set<GuideIndicator> = DEFAULT_INDICATORS,
) {
    companion object {
        val DEFAULT_INDICATORS =
            setOf(GuideIndicator.LIVE, GuideIndicator.NEW, GuideIndicator.PREMIERE)
    }
}
