package com.makd.afinity.ui.settings.servers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.settings.servers.components.DeleteServerConfirmationDialog
import com.makd.afinity.ui.settings.servers.components.EmptyServersState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagementScreen(
    onBackClick: () -> Unit,
    onAddServerClick: () -> Unit,
    onEditServerClick: (serverId: String) -> Unit,
    isDualPane: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: ServerManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val playerOffset = LocalPlayerOffset.current

    LaunchedEffect(Unit) { viewModel.loadServers() }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val inlineDualPaneServer = if (isDualPane) state.detailServer else null

    AnimatedContent(
        targetState = inlineDualPaneServer,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
            } else {
                (slideInHorizontally { -it } + fadeIn()) togetherWith
                    (slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "ServerDetailInline",
    ) { inlineServer ->
        if (inlineServer != null) {
            ServerDetailContent(
                serverWithCount = inlineServer,
                stats = state.detailStats,
                statsLoading = state.statsLoading,
                onDismiss = { viewModel.hideServerDetail() },
                onDeleteAddress = { viewModel.deleteAddress(it) },
                onSetPrimary = { viewModel.setPrimaryAddress(inlineServer.server.id, it) },
                onDeleteJellyseerrAddress = { viewModel.deleteJellyseerrAddress(it) },
                onDeleteAudiobookshelfAddress = { viewModel.deleteAudiobookshelfAddress(it) },
                onAddJellyseerrAddress = { address ->
                    viewModel.addJellyseerrAddress(inlineServer.server.id, address)
                },
                onAddAudiobookshelfAddress = { address ->
                    viewModel.addAudiobookshelfAddress(inlineServer.server.id, address)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ServerListPane(
                state = state,
                isOffline = isOffline,
                snackbarHostState = snackbarHostState,
                playerOffset = playerOffset,
                showDetailDialog = !isDualPane,
                onBackClick = onBackClick,
                onAddServerClick = onAddServerClick,
                onEditServerClick = onEditServerClick,
                viewModel = viewModel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerListPane(
    state: ServerManagementState,
    isOffline: Boolean,
    snackbarHostState: SnackbarHostState,
    playerOffset: androidx.compose.ui.unit.Dp,
    showDetailDialog: Boolean,
    onBackClick: () -> Unit,
    onAddServerClick: () -> Unit,
    onEditServerClick: (serverId: String) -> Unit,
    viewModel: ServerManagementViewModel,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.server_management_title),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!isOffline) onAddServerClick() },
                modifier = Modifier.padding(bottom = playerOffset),
                containerColor =
                    if (isOffline) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.primary,
                contentColor =
                    if (isOffline) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = stringResource(R.string.cd_add_server),
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState, snackbar = { AFinitySnackbar(it) }) },
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        val customPadding =
            PaddingValues(
                top = paddingValues.calculateTopPadding(),
                start = paddingValues.calculateStartPadding(layoutDirection),
                end = paddingValues.calculateEndPadding(layoutDirection),
                bottom = max(paddingValues.calculateBottomPadding(), playerOffset),
            )
        if (state.isLoading && state.servers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(customPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.servers.isEmpty()) {
            EmptyServersState(modifier = Modifier.fillMaxSize().padding(customPadding))
        } else {
            val activeServer = state.servers.firstOrNull { it.isActiveServer }
            val savedServers = state.servers.filter { !it.isActiveServer }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = customPadding.calculateTopPadding() + 16.dp,
                        start = customPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = customPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        bottom = customPadding.calculateBottomPadding() + 100.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (activeServer != null) {
                    item(key = "header_active") {
                        ServerSectionHeader(text = stringResource(R.string.server_section_active))
                    }
                    item(key = activeServer.server.id) {
                        ServerCard(
                            serverWithCount = activeServer,
                            onClick = { viewModel.showServerDetail(activeServer) },
                            onEditClick = { onEditServerClick(activeServer.server.id) },
                            onDeleteClick = { viewModel.showDeleteConfirmation(activeServer) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (savedServers.isNotEmpty()) {
                    item(key = "header_saved") {
                        ServerSectionHeader(
                            text = stringResource(R.string.server_section_saved),
                            modifier =
                                if (activeServer != null) Modifier.padding(top = 8.dp)
                                else Modifier,
                        )
                    }
                    items(items = savedServers, key = { it.server.id }) { serverWithCount ->
                        ServerCard(
                            serverWithCount = serverWithCount,
                            onClick = { viewModel.showServerDetail(serverWithCount) },
                            onEditClick = { onEditServerClick(serverWithCount.server.id) },
                            onDeleteClick = { viewModel.showDeleteConfirmation(serverWithCount) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        state.serverToDelete?.let { serverToDelete ->
            DeleteServerConfirmationDialog(
                serverWithCount = serverToDelete,
                onConfirm = { viewModel.deleteServer(serverToDelete.server.id) },
                onDismiss = { viewModel.hideDeleteConfirmation() },
            )
        }

        if (showDetailDialog) {
            state.detailServer?.let { detailServer ->
                ServerDetailDialog(
                    serverWithCount = detailServer,
                    stats = state.detailStats,
                    statsLoading = state.statsLoading,
                    onDismiss = { viewModel.hideServerDetail() },
                    onDeleteAddress = { viewModel.deleteAddress(it) },
                    onSetPrimary = { viewModel.setPrimaryAddress(detailServer.server.id, it) },
                    onDeleteJellyseerrAddress = { viewModel.deleteJellyseerrAddress(it) },
                    onDeleteAudiobookshelfAddress = { viewModel.deleteAudiobookshelfAddress(it) },
                    onAddJellyseerrAddress = { address ->
                        viewModel.addJellyseerrAddress(detailServer.server.id, address)
                    },
                    onAddAudiobookshelfAddress = { address ->
                        viewModel.addAudiobookshelfAddress(detailServer.server.id, address)
                    },
                )
            }
        }
    }
}

@Composable
private fun ServerSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}
