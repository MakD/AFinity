package com.makd.afinity.data.models.admin

enum class LibraryRemovalStage {
    WAITING_FOR_SCAN,
    REMOVING,
    CHECKING,
    CLEANING_UP,
}

data class LibraryRemoval(val name: String, val stage: LibraryRemovalStage)

enum class LibraryRemovalOutcome {
    REMOVED,
    CLEANED_UP,
    LEFTOVER_REMAINS,
    CANCELLED,
    FAILED,
}

data class LibraryRemovalResult(val name: String, val outcome: LibraryRemovalOutcome)

data class GhostLibrary(val name: String)
