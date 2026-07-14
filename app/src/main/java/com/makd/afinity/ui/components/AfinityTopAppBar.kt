package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.server.ConnectionType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AfinityTopAppBarViewModel @Inject constructor(offlineModeManager: OfflineModeManager) :
    ViewModel() {
    val connectionType = offlineModeManager.connectionType
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfinityTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: (() -> Unit)? = null,
    onRandomClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    userName: String? = null,
    userProfileImageUrl: String? = null,
    backgroundOpacity: () -> Float = { 0f },
    actions: @Composable (RowScope.() -> Unit) = {},
    viewModel: AfinityTopAppBarViewModel = hiltViewModel(),
    isFetchingRandom: Boolean = false,
) {
    val connectionType by viewModel.connectionType.collectAsStateWithLifecycle()
    val surfaceColor = MaterialTheme.colorScheme.surface

    TopAppBar(
        title = title,
        navigationIcon = {
            val leadingOnClick = onMenuClick ?: onHomeClick
            if (leadingOnClick != null) {
                val isMenu = onMenuClick != null
                IconButton(onClick = leadingOnClick, modifier = Modifier.size(42.dp)) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    id = if (isMenu) R.drawable.ic_menu else R.drawable.ic_home
                                ),
                            contentDescription =
                                stringResource(
                                    if (isMenu) R.string.cd_open_navigation_menu
                                    else R.string.cd_go_to_home
                                ),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        },
        actions = {
            val showDice = onRandomClick != null && connectionType != ConnectionType.OFFLINE
            if (onSearchClick != null || showDice) {
                Row(
                    modifier =
                        Modifier.height(42.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.3f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onRandomClick != null && connectionType != ConnectionType.OFFLINE) {
                        IconButton(
                            onClick = onRandomClick,
                            enabled = !isFetchingRandom,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_dice5),
                                contentDescription = stringResource(R.string.cd_random_item),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (showDice && onSearchClick != null) {
                        Box(
                            modifier =
                                Modifier.height(20.dp)
                                    .width(1.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                        )
                    }
                    if (onSearchClick != null) {
                        IconButton(onClick = onSearchClick, modifier = Modifier.size(42.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = stringResource(R.string.cd_search_icon),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (onProfileClick != null) {
                Box(
                    modifier =
                        Modifier.size(42.dp).graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                ) {
                    IconButton(onClick = onProfileClick, modifier = Modifier.size(42.dp)) {
                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                    .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (userProfileImageUrl != null) {
                                AsyncImage(
                                    imageUrl = userProfileImageUrl,
                                    contentDescription = stringResource(R.string.cd_profile_icon),
                                    targetWidth = 48.dp,
                                    targetHeight = 48.dp,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else if (!userName.isNullOrBlank()) {
                                Text(
                                    text = userName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_user_circle),
                                    contentDescription = stringResource(R.string.cd_profile_icon),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }

                    ConnectionIndicatorBadge(
                        connectionType = connectionType,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            actions()
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        modifier =
            modifier.drawBehind {
                drawRect(color = surfaceColor.copy(alpha = backgroundOpacity()))
            },
    )
}
