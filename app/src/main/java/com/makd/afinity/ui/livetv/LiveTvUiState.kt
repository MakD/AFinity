package com.makd.afinity.ui.livetv

import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.ui.livetv.models.LiveTvCategory
import com.makd.afinity.ui.livetv.models.ProgramWithChannel
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val hasLiveTvAccess: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val selectedTab: LiveTvTab = LiveTvTab.HOME,
    val categorizedPrograms: Map<LiveTvCategory, List<ProgramWithChannel>> = emptyMap(),
    val isCategoriesLoading: Boolean = false,
    val epgChannels: List<AfinityChannel> = emptyList(),
    val epgPrograms: Map<UUID, List<AfinityProgram>> = emptyMap(),
    val epgStartTime: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    val epgVisibleHours: Int = 3,
    val isEpgLoading: Boolean = false,
    val channels: List<AfinityChannel> = emptyList(),
)

enum class LiveTvTab {
    HOME,
    GUIDE,
    CHANNELS,
}
