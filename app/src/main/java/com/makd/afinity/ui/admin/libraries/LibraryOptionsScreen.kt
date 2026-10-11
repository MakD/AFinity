package com.makd.afinity.ui.admin.libraries

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.makd.afinity.ui.admin.config.ConfigFormEditorScreen

@Composable
fun LibraryOptionsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryOptionsViewModel = hiltViewModel(),
) {
    ConfigFormEditorScreen(
        editor = viewModel.editor,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
