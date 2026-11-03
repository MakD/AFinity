package com.makd.afinity.ui.music.components

import androidx.compose.runtime.Immutable
import com.makd.afinity.data.models.music.AfinityTrack
import java.util.UUID

@Immutable
class TrackAdminActions(
    val deletableIds: Set<UUID>,
    val onMenuOpened: (AfinityTrack) -> Unit,
    val onEditMetadata: (AfinityTrack) -> Unit,
    val onEditImages: (AfinityTrack) -> Unit,
    val onEditLyrics: (AfinityTrack) -> Unit,
    val onRefresh: (AfinityTrack) -> Unit,
    val onDelete: (AfinityTrack) -> Unit,
)
