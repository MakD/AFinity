package com.makd.afinity.ui.settings.servers

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.Permissions
import com.makd.afinity.data.models.jellyseerr.hasPermission
import com.makd.afinity.data.models.jellyseerr.isAdmin
import java.util.UUID

private val JellyseerrColor = Color(0xFFA88AFA)
private val AudiobookshelfColor = Color(0xFFCD9E46)
private val tmdbColor = Color(0xFF3CBEC9)
private val mdblistColor = Color(0xFF4283C9)
private val LocalColor = Color(0xFF4CAF50)
private val TailscaleColor = Color(0xFF2196F3)
private val RemoteColor = Color(0xFFFF9800)

private enum class DialogView {
    STATS,
    MANAGE_ADDRESSES,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagementScreen(
    onBackClick: () -> Unit,
    onAddServerClick: () -> Unit,
    onEditServerClick: (serverId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadServers() }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

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
                onClick = onAddServerClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = stringResource(R.string.cd_add_server),
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        if (state.isLoading && state.servers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.servers.isEmpty()) {
            EmptyServersState(modifier = Modifier.fillMaxSize().padding(paddingValues))
        } else {
            LazyColumn(
                contentPadding =
                    PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            ) {
                items(items = state.servers, key = { it.server.id }) { serverWithCount ->
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

        state.serverToDelete?.let { serverToDelete ->
            DeleteServerConfirmationDialog(
                serverWithCount = serverToDelete,
                onConfirm = { viewModel.deleteServer(serverToDelete.server.id) },
                onDismiss = { viewModel.hideDeleteConfirmation() },
            )
        }

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

@Composable
private fun ServiceStatusIcon(
    modifier: Modifier = Modifier,
    @DrawableRes activeIconRes: Int,
    @DrawableRes inactiveIconRes: Int = activeIconRes,
    contentDescription: String,
    isActive: Boolean,
    haloColor: Color? = null,
) {
    val currentIconRes = if (isActive) activeIconRes else inactiveIconRes
    val activeHalo =
        haloColor?.copy(alpha = 0.15f) ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val inactiveHalo = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)

    Box(
        modifier =
            modifier
                .size(32.dp)
                .background(
                    color = if (isActive) activeHalo else inactiveHalo,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = currentIconRes),
            contentDescription = contentDescription,
            tint =
                if (isActive) Color.Unspecified
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
fun ServerCard(
    serverWithCount: ServerWithUserCount,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = serverWithCount.currentUserServiceStatus
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(44.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_server),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = serverWithCount.server.name,
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        val (connIcon, connColor, connText) =
                            when (serverWithCount.currentConnectionType) {
                                AddressType.LOCAL -> Triple(R.drawable.ic_wifi, LocalColor, "Local")
                                AddressType.TAILSCALE ->
                                    Triple(R.drawable.ic_security, TailscaleColor, "Tailscale")
                                AddressType.REMOTE ->
                                    Triple(R.drawable.ic_link, RemoteColor, "Remote")
                            }
                        Icon(
                            painter = painterResource(id = connIcon),
                            contentDescription = null,
                            tint = connColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = connText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dots_vertical),
                            contentDescription = "Server Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Edit Server", style = MaterialTheme.typography.bodyMedium)
                            },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(id = R.drawable.ic_edit),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete Server",
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.error
                                        ),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(id = R.drawable.ic_delete),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ServiceChip(
                        label = "Seerr",
                        activeColor = JellyseerrColor,
                        addressType =
                            serverWithCount.jellyseerrConnectionType ?: AddressType.REMOTE,
                        isActive = status.jellyseerrConfigured,
                    )

                    ServiceChip(
                        label = "ABS",
                        activeColor = AudiobookshelfColor,
                        addressType =
                            serverWithCount.audiobookshelfConnectionType ?: AddressType.REMOTE,
                        isActive = status.audiobookshelfConfigured,
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    LedIndicator(
                        activeColor = tmdbColor,
                        label = "TMDB",
                        isActive = status.tmdbConfigured,
                    )
                    LedIndicator(
                        activeColor = mdblistColor,
                        label = "MDBList",
                        isActive = status.mdbListConfigured,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = serverWithCount.userCount.toString(),
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceChip(
    label: String,
    activeColor: Color,
    addressType: AddressType,
    isActive: Boolean,
) {
    val connIcon =
        when (addressType) {
            AddressType.LOCAL -> R.drawable.ic_wifi
            AddressType.TAILSCALE -> R.drawable.ic_security
            AddressType.REMOTE -> R.drawable.ic_link
        }

    val contentColor =
        if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val bgColor =
        if (isActive) activeColor.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f)
    val borderColor =
        if (isActive) activeColor.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                text = label.uppercase(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                color = contentColor,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = painterResource(id = connIcon),
                contentDescription = addressType.name,
                tint = contentColor.copy(alpha = if (isActive) 0.8f else 0.5f),
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun LedIndicator(activeColor: Color, label: String, isActive: Boolean) {
    val dotColor =
        if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val textColor =
        if (isActive) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
        Box(modifier = Modifier.size(6.dp).background(color = dotColor, shape = CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                ),
            color = textColor,
        )
    }
}

private enum class DetailTab(
    val label: String,
    @DrawableRes val activeIconRes: Int,
    @DrawableRes val inactiveIconRes: Int,
    val isBrandColored: Boolean,
) {
    JELLYFIN("Jellyfin", R.drawable.ic_jellyfin, R.drawable.ic_jellyfin_light, true),
    JELLYSEERR("Seerr", R.drawable.ic_seerr_logo_colored, R.drawable.ic_seerr_logo, true),
    AUDIOBOOKSHELF(
        "ABS",
        R.drawable.ic_audiobookshelf_colored,
        R.drawable.ic_audiobookshelf_light,
        true,
    ),
}

@Composable
private fun SegmentedTabBar(
    tabs: List<DetailTab>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedTabIndex
                val animatedBgColor by
                    animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        label = "tabBgColor",
                    )
                val animatedContentColor by
                    animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tabContentColor",
                    )

                Box(
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(animatedBgColor)
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val currentIcon = if (isSelected) tab.activeIconRes else tab.inactiveIconRes

                        Icon(
                            painter = painterResource(id = currentIcon),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint =
                                if (tab.isBrandColored && isSelected) Color.Unspecified
                                else animatedContentColor,
                        )
                        Text(
                            text = tab.label,
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = animatedContentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerDetailDialog(
    serverWithCount: ServerWithUserCount,
    stats: ServerDetailStats?,
    statsLoading: Boolean,
    onDismiss: () -> Unit,
    onDeleteAddress: (UUID) -> Unit,
    onSetPrimary: (String) -> Unit,
    onDeleteJellyseerrAddress: (UUID) -> Unit,
    onDeleteAudiobookshelfAddress: (UUID) -> Unit,
    onAddJellyseerrAddress: (String) -> Unit,
    onAddAudiobookshelfAddress: (String) -> Unit,
) {
    val status = serverWithCount.currentUserServiceStatus
    val tabs = buildList {
        add(DetailTab.JELLYFIN)
        if (status.jellyseerrConfigured) add(DetailTab.JELLYSEERR)
        if (status.audiobookshelfConfigured) add(DetailTab.AUDIOBOOKSHELF)
    }

    var selectedTabIndex by remember(serverWithCount.server.id) { mutableIntStateOf(0) }
    var dialogView by remember(serverWithCount.server.id) { mutableStateOf(DialogView.STATS) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(750.dp).padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            AnimatedContent(
                targetState = dialogView,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState == DialogView.MANAGE_ADDRESSES) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "DialogTransition",
            ) { viewState ->
                when (viewState) {
                    DialogView.STATS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(
                                            start = 24.dp,
                                            end = 16.dp,
                                            top = 24.dp,
                                            bottom = 16.dp,
                                        ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier.size(48.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                RoundedCornerShape(14.dp),
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_server),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = serverWithCount.server.name,
                                        style =
                                            MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (serverWithCount.server.version != null) {
                                        Text(
                                            text = "v${serverWithCount.server.version}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier =
                                        Modifier.background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                            CircleShape,
                                        ),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_close),
                                        contentDescription = stringResource(R.string.action_close),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            if (tabs.size > 1) {
                                SegmentedTabBar(
                                    tabs = tabs,
                                    selectedTabIndex = selectedTabIndex,
                                    onTabSelected = { selectedTabIndex = it },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Crossfade(
                                targetState =
                                    tabs.getOrNull(selectedTabIndex) ?: DetailTab.JELLYFIN,
                                animationSpec = tween(300),
                                modifier = Modifier.weight(1f),
                                label = "TabContentCrossfade",
                            ) { currentTab ->
                                Column(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 24.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    when (currentTab) {
                                        DetailTab.JELLYFIN ->
                                            JellyfinTabContent(
                                                serverWithCount = serverWithCount,
                                                jellyfinStats = stats?.jellyfinStats,
                                                statsLoading = statsLoading,
                                                onManageClick = {
                                                    dialogView = DialogView.MANAGE_ADDRESSES
                                                },
                                            )
                                        DetailTab.JELLYSEERR ->
                                            JellyseerrTabContent(
                                                serverWithCount = serverWithCount,
                                                jellyseerrStats = stats?.jellyseerrStats,
                                                statsLoading = statsLoading,
                                                onManageClick = {
                                                    dialogView = DialogView.MANAGE_ADDRESSES
                                                },
                                            )
                                        DetailTab.AUDIOBOOKSHELF ->
                                            AudiobookshelfTabContent(
                                                serverWithCount = serverWithCount,
                                                absStats = stats?.audiobookshelfStats,
                                                statsLoading = statsLoading,
                                                onManageClick = {
                                                    dialogView = DialogView.MANAGE_ADDRESSES
                                                },
                                            )
                                    }
                                }
                            }
                        }
                    }
                    DialogView.MANAGE_ADDRESSES -> {
                        val currentTab = tabs.getOrNull(selectedTabIndex) ?: DetailTab.JELLYFIN
                        ManageAddressesView(
                            title = "Manage Connections",
                            onBack = { dialogView = DialogView.STATS },
                        ) {
                            when (currentTab) {
                                DetailTab.JELLYFIN -> {
                                    val primaryAddress = serverWithCount.server.address
                                    val allAddresses = buildList {
                                        add(primaryAddress)
                                        serverWithCount.addresses
                                            .map { it.address }
                                            .filter { it != primaryAddress }
                                            .forEach { add(it) }
                                    }
                                    JellyfinManageAddresses(
                                        allAddresses,
                                        primaryAddress,
                                        serverWithCount,
                                        onDeleteAddress,
                                        onSetPrimary,
                                    )
                                }
                                DetailTab.JELLYSEERR ->
                                    JellyseerrManageAddresses(
                                        serverWithCount,
                                        onDeleteJellyseerrAddress,
                                        onAddJellyseerrAddress,
                                    )
                                DetailTab.AUDIOBOOKSHELF ->
                                    AudiobookshelfManageAddresses(
                                        serverWithCount,
                                        onDeleteAudiobookshelfAddress,
                                        onAddAudiobookshelfAddress,
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageAddressesView(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_left),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ActiveConnectionCard(
    activeAddress: String,
    totalAddresses: Int,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_link),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Active Connection",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = activeAddress,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (totalAddresses > 1) {
                    Text(
                        text = "+ ${totalAddresses - 1} backup route(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            FilledIconButton(
                onClick = onManageClick,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = "Manage Addresses",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun JellyfinTabContent(
    serverWithCount: ServerWithUserCount,
    jellyfinStats: JellyfinStats?,
    statsLoading: Boolean,
    onManageClick: () -> Unit,
) {
    val primaryAddress = serverWithCount.server.address
    val allAddressesCount = serverWithCount.addresses.size + 1

    if (statsLoading) {
        LoadingState()
    } else if (jellyfinStats != null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Library Overview")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = "Movies",
                    value = jellyfinStats.movieCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Series",
                    value = jellyfinStats.seriesCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = "Episodes",
                    value = jellyfinStats.episodeCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Collections",
                    value = jellyfinStats.boxsetCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (serverWithCount.userServices.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Active Services")
            val currentUser =
                serverWithCount.userServices.find {
                    it.serviceStatus == serverWithCount.currentUserServiceStatus
                } ?: serverWithCount.userServices.first()
            UserServiceRow(userInfo = currentUser)

            val otherCount = serverWithCount.userServices.size - 1
            if (otherCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "+$otherCount more user${if (otherCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Network")
        ActiveConnectionCard(
            activeAddress = primaryAddress,
            totalAddresses = allAddressesCount,
            onManageClick = onManageClick,
        )
    }
}

@Composable
private fun JellyfinManageAddresses(
    allAddresses: List<String>,
    primaryAddress: String,
    serverWithCount: ServerWithUserCount,
    onDeleteAddress: (UUID) -> Unit,
    onSetPrimary: (String) -> Unit,
) {
    allAddresses.forEach { address ->
        SelectableAddressRow(
            address = address,
            isPrimary = address == primaryAddress,
            onSelect = { if (address != primaryAddress) onSetPrimary(address) },
            onDelete =
                if (address != primaryAddress) {
                    val serverAddr = serverWithCount.addresses.find { it.address == address }
                    serverAddr?.let { { onDeleteAddress(it.id) } }
                } else null,
        )
    }
}

@Composable
private fun JellyseerrTabContent(
    serverWithCount: ServerWithUserCount,
    jellyseerrStats: JellyseerrStats?,
    statsLoading: Boolean,
    onManageClick: () -> Unit,
) {
    if (statsLoading) {
        LoadingState()
    } else if (jellyseerrStats != null) {
        jellyseerrStats.user?.let { user ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("User Profile")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DetailRow(
                            label = "Name",
                            value = user.displayName ?: user.username ?: "Unknown",
                        )
                        DetailRow(
                            label = "Role",
                            value = if (user.isAdmin()) "Admin" else "User",
                            valueColor =
                                if (user.isAdmin()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        )
                        val permissions = buildList {
                            if (user.hasPermission(Permissions.REQUEST)) add("Request")
                            if (user.hasPermission(Permissions.AUTO_APPROVE)) add("Auto-Approve")
                            if (user.hasPermission(Permissions.REQUEST_4K)) add("4K")
                            if (user.hasPermission(Permissions.MANAGE_REQUESTS)) add("Manage")
                        }
                        if (permissions.isNotEmpty()) {
                            DetailRow(label = "Permissions", value = permissions.joinToString(", "))
                        }
                        DetailRow(
                            label = "Movie quota",
                            value =
                                if (user.movieQuotaLimit != null && user.movieQuotaLimit > 0)
                                    "${user.movieQuotaLimit} / ${user.movieQuotaDays ?: 7} days"
                                else "Unlimited",
                        )
                        DetailRow(
                            label = "TV quota",
                            value =
                                if (user.tvQuotaLimit != null && user.tvQuotaLimit > 0)
                                    "${user.tvQuotaLimit} / ${user.tvQuotaDays ?: 7} days"
                                else "Unlimited",
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Request Stats")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = "Total",
                    value = jellyseerrStats.totalRequests.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Pending",
                    value = jellyseerrStats.pendingRequests.toString(),
                    modifier = Modifier.weight(1f),
                    valueColor = Color(0xFFF59E0B),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = "Approved",
                    value = jellyseerrStats.approvedRequests.toString(),
                    modifier = Modifier.weight(1f),
                    valueColor = Color(0xFF10B981),
                )
                StatChip(
                    label = "Available",
                    value = jellyseerrStats.availableRequests.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Network")
        val activeAddress =
            serverWithCount.jellyseerrAddresses.firstOrNull()?.address ?: "Default (Proxy)"
        val total = serverWithCount.jellyseerrAddresses.size
        ActiveConnectionCard(
            activeAddress = activeAddress,
            totalAddresses = total,
            onManageClick = onManageClick,
        )
    }
}

@Composable
private fun JellyseerrManageAddresses(
    serverWithCount: ServerWithUserCount,
    onDeleteAddress: (UUID) -> Unit,
    onAddAddress: (String) -> Unit,
) {
    if (serverWithCount.jellyseerrAddresses.isNotEmpty()) {
        serverWithCount.jellyseerrAddresses.forEach { address ->
            ServiceAddressItem(
                address = address.address,
                onDelete = { onDeleteAddress(address.id) },
            )
        }
    } else {
        Text(
            text = "No alternate addresses configured.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
    AddAddressField(placeholder = "https://seerr.example.com", onAdd = onAddAddress)
}

@Composable
private fun AudiobookshelfTabContent(
    serverWithCount: ServerWithUserCount,
    absStats: AudiobookshelfStats?,
    statsLoading: Boolean,
    onManageClick: () -> Unit,
) {
    if (statsLoading) {
        LoadingState()
    } else if (absStats != null) {
        val hours = (absStats.totalListeningTimeSeconds / 3600).toInt()
        val minutes = ((absStats.totalListeningTimeSeconds % 3600) / 60).toInt()

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "TOTAL LISTENING TIME",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$hours",
                        style =
                            MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black
                            ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "h",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp, end = 8.dp),
                    )
                    Text(
                        text = "$minutes",
                        style =
                            MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black
                            ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "m",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatChip(
                label = "Today",
                value = formatListeningDuration(absStats.todaySeconds),
                modifier = Modifier.weight(1f),
            )
            StatChip(
                label = "This Week",
                value = formatListeningDuration(absStats.weekSeconds),
                modifier = Modifier.weight(1f),
            )
            StatChip(
                label = "This Month",
                value = formatListeningDuration(absStats.monthSeconds),
                modifier = Modifier.weight(1f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Activity")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = "Current Streak",
                    value = "${absStats.currentStreak}d",
                    iconRes = R.drawable.ic_bolt,
                    iconTint = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Best Streak",
                    value = "${absStats.longestStreak}d",
                    iconRes = R.drawable.ic_star,
                    iconTint = Color(0xFFFFC107),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = "Finished",
                    value = absStats.finishedCount.toString(),
                    iconRes = R.drawable.ic_circle_check,
                    iconTint = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Days Active",
                    value = absStats.activeDays.toString(),
                    iconRes = R.drawable.ic_calendar,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Library")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = "Items",
                    value = absStats.totalItems.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Content",
                    value = "%.0f hrs".format(absStats.totalDurationHours),
                    modifier = Modifier.weight(1f),
                )
            }

            val audiobookCount =
                absStats.libraries
                    .filter { it.mediaType == "book" }
                    .sumOf { it.stats?.totalItems ?: 0 }
            val podcastCount =
                absStats.libraries
                    .filter { it.mediaType == "podcast" }
                    .sumOf { it.stats?.totalItems ?: 0 }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = "Audiobooks",
                    value = audiobookCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Podcasts",
                    value = podcastCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = "In Progress",
                    value = absStats.inProgressCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = "Libraries",
                    value = absStats.libraries.size.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Network")
        val activeAddress =
            serverWithCount.audiobookshelfAddresses.firstOrNull()?.address ?: "Default (Proxy)"
        val total = serverWithCount.audiobookshelfAddresses.size
        ActiveConnectionCard(
            activeAddress = activeAddress,
            totalAddresses = total,
            onManageClick = onManageClick,
        )
    }
}

@Composable
private fun AudiobookshelfManageAddresses(
    serverWithCount: ServerWithUserCount,
    onDeleteAddress: (UUID) -> Unit,
    onAddAddress: (String) -> Unit,
) {
    if (serverWithCount.audiobookshelfAddresses.isNotEmpty()) {
        serverWithCount.audiobookshelfAddresses.forEach { address ->
            ServiceAddressItem(
                address = address.address,
                onDelete = { onDeleteAddress(address.id) },
            )
        }
    } else {
        Text(
            text = "No alternate addresses configured.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
    AddAddressField(placeholder = "https://abs.example.com", onAdd = onAddAddress)
}

@Composable
private fun LoadingState() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "Loading data...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    iconTint: Color? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconRes != null) {
                    Box(
                        modifier =
                            Modifier.size(24.dp)
                                .background(
                                    (iconTint ?: MaterialTheme.colorScheme.primary).copy(
                                        alpha = 0.15f
                                    ),
                                    CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = iconTint ?: MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = valueColor,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatListeningDuration(seconds: Double): String {
    val h = (seconds / 3600).toInt()
    val m = ((seconds % 3600) / 60).toInt()
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        m > 0 -> "${m}m"
        seconds > 0 -> "<1m"
        else -> "0m"
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun UserServiceRow(userInfo: UserServiceInfo) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = userInfo.userName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServiceStatusIcon(
                    activeIconRes = R.drawable.ic_seerr_logo_colored,
                    inactiveIconRes = R.drawable.ic_seerr_logo,
                    contentDescription = "Jellyseerr",
                    isActive = userInfo.serviceStatus.jellyseerrConfigured,
                    haloColor = JellyseerrColor,
                )
                ServiceStatusIcon(
                    activeIconRes = R.drawable.ic_audiobookshelf_colored,
                    inactiveIconRes = R.drawable.ic_audiobookshelf_light,
                    contentDescription = "Audiobookshelf",
                    isActive = userInfo.serviceStatus.audiobookshelfConfigured,
                    haloColor = AudiobookshelfColor,
                )
                ServiceStatusIcon(
                    activeIconRes = R.drawable.ic_tmdb,
                    contentDescription = "TMDB",
                    isActive = userInfo.serviceStatus.tmdbConfigured,
                )
                ServiceStatusIcon(
                    activeIconRes = R.drawable.ic_mdblist,
                    contentDescription = "MDBList",
                    isActive = userInfo.serviceStatus.mdbListConfigured,
                )
            }
        }
    }
}

@Composable
private fun SelectableAddressRow(
    address: String,
    isPrimary: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        color =
            if (isPrimary) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isPrimary,
                onClick = onSelect,
                colors =
                    RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(32.dp).padding(end = 8.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = address,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal
                        ),
                    color =
                        if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isPrimary) {
                    Text(
                        text = "Primary Connection",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Remove",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceAddressItem(
    address: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddAddressField(placeholder: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(text = placeholder, style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        val trimmed = text.trim()
                        if (trimmed.isNotBlank()) {
                            onAdd(trimmed)
                            text = ""
                        }
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = {
                val trimmed = text.trim()
                if (trimmed.isNotBlank()) {
                    onAdd(trimmed)
                    text = ""
                }
            },
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = "Add address",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun EmptyServersState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_server),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Text(
                text = stringResource(R.string.empty_servers_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.empty_servers_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeleteServerConfirmationDialog(
    serverWithCount: ServerWithUserCount,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.dialog_delete_server_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text =
                        stringResource(
                            R.string.dialog_delete_server_message_fmt,
                            serverWithCount.server.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (serverWithCount.userCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_exclamation_circle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text =
                                    stringResource(
                                        R.string.dialog_delete_server_warning_fmt,
                                        serverWithCount.userCount,
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
