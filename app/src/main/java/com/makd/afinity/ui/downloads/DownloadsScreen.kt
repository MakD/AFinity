package com.makd.afinity.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.main.MainUiState

@Composable
fun DownloadsScreen(
    onItemClick: (AfinityItem) -> Unit,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    mainUiState: MainUiState,
    navController: NavController
) {
    // TODO: Implement the UI for the Downloads screen, similar to FavoritesScreen
}
