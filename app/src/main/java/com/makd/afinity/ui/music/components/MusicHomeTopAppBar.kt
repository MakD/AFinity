package com.makd.afinity.ui.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar

@Composable
fun MusicHomeTopAppBar(navController: NavController, isLandscape: Boolean, topBarOpacity: Float) {
    AfinityTopAppBar(
        title = {
            IconButton(
                onClick = {
                    navController.navigate(Destination.HOME.route) {
                        popUpTo(Destination.HOME.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.size(42.dp),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_home),
                        contentDescription = stringResource(R.string.cd_music_go_to_home),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        backgroundOpacity = { if (isLandscape) 0f else topBarOpacity },
    )
}