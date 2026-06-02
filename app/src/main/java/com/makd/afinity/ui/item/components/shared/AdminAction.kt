package com.makd.afinity.ui.item.components.shared

sealed interface AdminAction {
    data object EditMetadata : AdminAction
    data object Identify : AdminAction
    data object EditImages : AdminAction
    data object Refresh : AdminAction
}