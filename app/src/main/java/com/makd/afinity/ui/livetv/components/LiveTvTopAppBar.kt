package com.makd.afinity.ui.livetv.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AppBarProfile
import com.makd.afinity.ui.components.afinityTopAppBarTitleStyle
import com.makd.afinity.ui.main.MainUiState

@Composable
fun LiveTvTopAppBar(
    title: String,
    navController: NavController,
    mainUiState: MainUiState,
    onMenuClick: (() -> Unit)? = null,
) {
    AfinityTopAppBar(
        title = {
            Text(
                text = title,
                style = afinityTopAppBarTitleStyle(),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        onMenuClick = onMenuClick,
        onSearchClick = { navController.navigate(Destination.createSearchRoute()) },
        profile =
            AppBarProfile(
                onClick = { navController.navigate(Destination.createSettingsRoute()) },
                name = mainUiState.userName,
                imageUrl = mainUiState.userProfileImageUrl,
            ),
        backgroundOpacity = { 1f },
    )
}
