package com.makd.afinity.ui.livetv

import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.ui.livetv.models.LiveTvCategory

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val hasLiveTvAccess: Boolean = false,
    val accessCheckFailed: Boolean = false,
    val categorizedPrograms: Map<LiveTvCategory, List<AfinityProgram>> = emptyMap(),
    val isCategoriesLoading: Boolean = true,
    val programsFailed: Boolean = false,
)
