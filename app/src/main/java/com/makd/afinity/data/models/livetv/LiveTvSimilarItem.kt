package com.makd.afinity.data.models.livetv

import com.makd.afinity.data.models.media.AfinityItem
import java.util.UUID

sealed interface LiveTvSimilarItem {
    val id: UUID

    data class Program(val program: AfinityProgram) : LiveTvSimilarItem {
        override val id: UUID
            get() = program.id
    }

    data class Library(val item: AfinityItem) : LiveTvSimilarItem {
        override val id: UUID
            get() = item.id
    }
}
