@file:UnstableApi

package com.makd.afinity.ui.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    val selectedLetter by viewModel.selectedLetter.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                        text = stringResource(R.string.livetv_title),
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
                            painter = painterResource(id = R.drawable.ic_live_tv_nav),
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            text = stringResource(R.string.livetv_error_not_available_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = stringResource(R.string.livetv_error_not_available_message),
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
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            NavigationChip(
                                selected = pagerState.currentPage == 0,
                                label = stringResource(R.string.livetv_tab_programs),
                                iconResId = R.drawable.ic_tv_prog,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                }
                            )
                        }
                        item {
                            NavigationChip(
                                selected = pagerState.currentPage == 1,
                                label = stringResource(R.string.livetv_tab_guide),
                                iconResId = R.drawable.ic_epg,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                }
                            )
                        }
                        item {
                            NavigationChip(
                                selected = pagerState.currentPage == 2,
                                label = stringResource(R.string.livetv_tab_channels),
                                iconResId = R.drawable.ic_channels,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                                }
                            )
                        }
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
                                        context = context,
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
                                        context = context,
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
                                        context = context,
                                        channelId = channel.id,
                                        channelName = channel.name
                                    )
                                },
                                onFavoriteClick = { channel ->
                                    viewModel.toggleFavorite(channel.id)
                                },
                                selectedLetter = selectedLetter,
                                onLetterSelected = viewModel::onLetterSelected,
                                onClearFilter = viewModel::clearLetterFilter
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationChip(
    selected: Boolean,
    label: String,
    iconResId: Int,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        shape = CircleShape,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            selectedBorderColor = Color.Transparent
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}