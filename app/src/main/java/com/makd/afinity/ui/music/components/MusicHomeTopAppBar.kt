package com.makd.afinity.ui.music.components

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar

@Composable
fun MusicHomeTopAppBar(navController: NavController, isLandscape: Boolean, topBarOpacity: Float) {
    AfinityTopAppBar(
        title = {},
        onHomeClick = {
            navController.navigate(Destination.HOME.route) {
                popUpTo(Destination.HOME.route) { inclusive = false }
                launchSingleTop = true
            }
        },
        backgroundOpacity = { if (isLandscape) 0f else topBarOpacity },
    )
}