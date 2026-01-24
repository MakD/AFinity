@file:UnstableApi

package com.makd.afinity.ui.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.livetv.tabs.LiveTvChannelsTab
import com.makd.afinity.ui.livetv.tabs.LiveTvGuideTab
import com.makd.afinity.ui.livetv.tabs.LiveTvHomeTab
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.player.PlayerLauncher
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    navController: NavController,
    mainUiState: MainUiState,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        val tab = when (pagerState.currentPage) {
            0 -> LiveTvTab.HOME
            1 -> LiveTvTab.GUIDE
            2 -> LiveTvTab.CHANNELS
            else -> LiveTvTab.HOME
        }
        viewModel.selectTab(tab)
    }

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = "Live TV",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                onSearchClick = {
                    navController.navigate(Destination.createSearchRoute())
                },
                onProfileClick = {
                    navController.navigate(Destination.createSettingsRoute())
                },
                userProfileImageUrl = mainUiState.userProfileImageUrl,
                backgroundOpacity = 1f
            )
        },
        modifier = modifier
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            !uiState.hasLiveTvAccess -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_live_tv),
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "Live TV not available",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Configure Live TV on your Jellyfin server",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            text = { Text("Home") }
                        )
                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            text = { Text("TV Guide") }
                        )
                        Tab(
                            selected = pagerState.currentPage == 2,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
                                }
                            },
                            text = { Text("Channels") }
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> LiveTvHomeTab(
                                uiState = uiState,
                                onProgramClick = { programWithChannel ->
                                    PlayerLauncher.launchLiveChannel(
                                        context = navController.context,
                                        channelId = programWithChannel.channel.id,
                                        channelName = programWithChannel.channel.name
                                    )
                                },
                                widthSizeClass = widthSizeClass
                            )

                            1 -> LiveTvGuideTab(
                                uiState = uiState,
                                onChannelClick = { channel ->
                                    PlayerLauncher.launchLiveChannel(
                                        context = navController.context,
                                        channelId = channel.id,
                                        channelName = channel.name
                                    )
                                },
                                onJumpToNow = viewModel::jumpToNow,
                                onNavigateTime = viewModel::navigateEpgTime,
                                widthSizeClass = widthSizeClass
                            )

                            2 -> LiveTvChannelsTab(
                                uiState = uiState,
                                onChannelClick = { channel ->
                                    PlayerLauncher.launchLiveChannel(
                                        context = navController.context,
                                        channelId = channel.id,
                                        channelName = channel.name
                                    )
                                },
                                onFavoriteClick = { channel ->
                                    viewModel.toggleFavorite(channel.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}