package com.makd.afinity.ui.music.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.makd.afinity.ui.components.SortOptionsDialog

@Composable
fun MusicSortDialog(
    fields: List<MusicSortField>,
    currentField: MusicSortField,
    currentDescending: Boolean,
    onDismiss: () -> Unit,
    onSortSelected: (MusicSortField, Boolean) -> Unit,
) {
    SortOptionsDialog(
        options = fields,
        current = currentField,
        currentDescending = currentDescending,
        optionLabel = { stringResource(it.labelRes) },
        onDismiss = onDismiss,
        onSortSelected = onSortSelected,
    )
}
