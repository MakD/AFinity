package com.makd.afinity.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.settings.SettingsViewModel
import com.makd.afinity.ui.settings.servers.AudiobookshelfColor
import com.makd.afinity.ui.settings.servers.CancelColor
import com.makd.afinity.ui.settings.servers.JellyseerrColor
import com.makd.afinity.ui.settings.servers.LocalColor
import com.makd.afinity.ui.settings.servers.RemoteColor
import com.makd.afinity.ui.settings.servers.SaveColor
import com.makd.afinity.ui.settings.servers.ServerManagementViewModel
import com.makd.afinity.ui.settings.servers.ServerWithUserCount
import com.makd.afinity.ui.settings.servers.TailscaleColor
import com.makd.afinity.ui.settings.servers.components.UnverifiedAddressDialog
import com.makd.afinity.ui.settings.servers.mdblistColor
import com.makd.afinity.ui.settings.servers.tmdbColor
import com.makd.afinity.util.isLocalAddress
import com.makd.afinity.util.isTailscaleAddress

private val ContentMaxWidth = 560.dp
private val ListPaneWidth = 380.dp
private val ExpandedThreshold = 720.dp

private enum class EditorKind {
    SEERR,
    ABS,
    JELLYFIN,
    RATINGS,
}

private fun addressTypeColor(url: String): Color =
    when {
        isLocalAddress(url) -> LocalColor
        isTailscaleAddress(url) -> TailscaleColor
        else -> RemoteColor
    }

private fun addressTypeDots(urls: List<String>): List<Color> =
    urls.map(::addressTypeColor).distinct()

private fun maskKey(key: String): String =
    if (key.length <= 3) "*".repeat(key.length)
    else "*".repeat((key.length - 3).coerceAtMost(12)) + key.takeLast(3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesHubScreen(
    onFinish: () -> Unit,
    onNavigateToServerManagement: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes finishLabel: Int = R.string.services_hub_finish_go_to_app,
    showRatings: Boolean = false,
    showAppBar: Boolean = false,
    viewModel: ServicesHubViewModel = hiltViewModel(),
) {
    val serverName by viewModel.serverName.collectAsStateWithLifecycle()
    val seerrConnected by viewModel.isJellyseerrConnected.collectAsStateWithLifecycle()
    val absConnected by viewModel.isAudiobookshelfConnected.collectAsStateWithLifecycle()
    val remoteHost by viewModel.remoteConfiguredHost.collectAsStateWithLifecycle()
    val remoteVerifying by viewModel.remoteVerifying.collectAsStateWithLifecycle()
    val remoteError by viewModel.remoteError.collectAsStateWithLifecycle()

    val smViewModel: ServerManagementViewModel = hiltViewModel()
    val smState by smViewModel.state.collectAsStateWithLifecycle()
    val currentServer = smState.servers.firstOrNull { it.isActiveServer }

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val tmdbKey by settingsViewModel.tmdbApiKey.collectAsStateWithLifecycle()
    val mdbKey by settingsViewModel.mdbListApiKey.collectAsStateWithLifecycle()
    val omdbKey by settingsViewModel.omdbApiKey.collectAsStateWithLifecycle()
    val ratingsCount = listOf(tmdbKey, mdbKey, omdbKey).count { it.isNotBlank() }

    val dimDot = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val seerrDots =
        if (seerrConnected)
            addressTypeDots(currentServer?.jellyseerrAddresses.orEmpty().map { it.address })
        else emptyList()
    val absDots =
        if (absConnected)
            addressTypeDots(currentServer?.audiobookshelfAddresses.orEmpty().map { it.address })
        else emptyList()
    val remoteDots =
        currentServer?.let { s ->
            addressTypeDots((listOf(s.server.address) + s.addresses.map { it.address }).distinct())
        } ?: emptyList()
    val ratingsDots =
        listOf(
            if (tmdbKey.isNotBlank()) tmdbColor else dimDot,
            if (mdbKey.isNotBlank()) mdblistColor else dimDot,
            if (omdbKey.isNotBlank()) MaterialTheme.colorScheme.onSurfaceVariant else dimDot,
        )

    var showSeerrSheet by remember { mutableStateOf(false) }
    var showAbsSheet by remember { mutableStateOf(false) }
    var showSeerrDisconnect by remember { mutableStateOf(false) }
    var showAbsDisconnect by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EditorKind?>(null) }
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val seerrSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val absSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val onSeerrTile = {
        if (seerrConnected) selected = EditorKind.SEERR else showSeerrSheet = true
    }
    val onAbsTile = { if (absConnected) selected = EditorKind.ABS else showAbsSheet = true }
    val onRemoteTile = { selected = EditorKind.JELLYFIN }
    val onRatingsTile = { selected = EditorKind.RATINGS }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        val expanded = maxWidth >= ExpandedThreshold
        val contentInsets =
            if (showAppBar)
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            else WindowInsets.safeDrawing

        Column(modifier = Modifier.fillMaxSize()) {
            if (showAppBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.services_hub_title),
                            style =
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onFinish) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_left),
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (expanded) {
                    Row(modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets)) {
                        HubList(
                            modifier = Modifier.width(ListPaneWidth).fillMaxHeight(),
                            serverName = serverName,
                            finishLabel = finishLabel,
                            showRatings = showRatings,
                            showWelcome = !showAppBar,
                            showFinish = !showAppBar,
                            seerrConnected = seerrConnected,
                            absConnected = absConnected,
                            remoteAdded = remoteHost != null,
                            ratingsCount = ratingsCount,
                            seerrDots = seerrDots,
                            absDots = absDots,
                            remoteDots = remoteDots,
                            ratingsDots = ratingsDots,
                            selected = selected,
                            onSeerr = onSeerrTile,
                            onAbs = onAbsTile,
                            onRemote = onRemoteTile,
                            onRatings = onRatingsTile,
                            onFinish = onFinish,
                            onServerManagement = onNavigateToServerManagement,
                            markDone = viewModel::markFirstRunDone,
                        )
                        VerticalDivider()
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val kind = selected
                            val server = currentServer
                            if (kind != null && (kind == EditorKind.RATINGS || server != null)) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    EditorHeader(
                                        title = stringResource(editorTitle(kind)),
                                        onClose = { selected = null },
                                    )
                                    EditorContent(
                                        kind = kind,
                                        server = server,
                                        smViewModel = smViewModel,
                                        settingsViewModel = settingsViewModel,
                                        onDisconnect = {
                                            selected = null
                                            if (kind == EditorKind.SEERR) showSeerrDisconnect = true
                                            else if (kind == EditorKind.ABS)
                                                showAbsDisconnect = true
                                        },
                                        onAddJellyfin = { url ->
                                            viewModel.verifyAndSaveRemoteAddress(url) {
                                                smViewModel.loadServers()
                                            }
                                        },
                                        remoteVerifying = remoteVerifying,
                                        remoteError = remoteError,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            } else {
                                EditorEmpty()
                            }
                        }
                    }
                } else {
                    HubList(
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(contentInsets),
                        serverName = serverName,
                        finishLabel = finishLabel,
                        showRatings = showRatings,
                        showWelcome = !showAppBar,
                        showFinish = !showAppBar,
                        seerrConnected = seerrConnected,
                        absConnected = absConnected,
                        remoteAdded = remoteHost != null,
                        ratingsCount = ratingsCount,
                        seerrDots = seerrDots,
                        absDots = absDots,
                        remoteDots = remoteDots,
                        ratingsDots = ratingsDots,
                        selected = null,
                        onSeerr = onSeerrTile,
                        onAbs = onAbsTile,
                        onRemote = onRemoteTile,
                        onRatings = onRatingsTile,
                        onFinish = onFinish,
                        onServerManagement = onNavigateToServerManagement,
                        markDone = viewModel::markFirstRunDone,
                    )

                    val kind = selected
                    val server = currentServer
                    if (kind != null && (kind == EditorKind.RATINGS || server != null)) {
                        ModalBottomSheet(
                            onDismissRequest = { selected = null },
                            sheetState = editorSheetState,
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            tonalElevation = 0.dp,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().imePadding()) {
                                Text(
                                    text = stringResource(editorTitle(kind)),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier =
                                        Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                                )
                                EditorContent(
                                    kind = kind,
                                    server = server,
                                    smViewModel = smViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onDisconnect = {
                                        selected = null
                                        if (kind == EditorKind.SEERR) showSeerrDisconnect = true
                                        else if (kind == EditorKind.ABS) showAbsDisconnect = true
                                    },
                                    onAddJellyfin = { url ->
                                        viewModel.verifyAndSaveRemoteAddress(url) {
                                            smViewModel.loadServers()
                                        }
                                    },
                                    remoteVerifying = remoteVerifying,
                                    remoteError = remoteError,
                                    modifier = Modifier.padding(bottom = 24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSeerrSheet) {
            com.makd.afinity.ui.settings.JellyseerrBottomSheet(
                onDismiss = { showSeerrSheet = false },
                sheetState = seerrSheetState,
            )
        }
        if (showAbsSheet) {
            com.makd.afinity.ui.settings.AudiobookshelfBottomSheet(
                onDismiss = { showAbsSheet = false },
                sheetState = absSheetState,
            )
        }
        smState.pendingAddress?.let { pending ->
            UnverifiedAddressDialog(
                url = pending.url,
                onConfirm = { smViewModel.confirmPendingAddress() },
                onDismiss = { smViewModel.dismissPendingAddress() },
            )
        }
        if (showSeerrDisconnect) {
            DisconnectDialog(
                serviceName = "Seerr",
                onConfirm = {
                    showSeerrDisconnect = false
                    viewModel.disconnectJellyseerr()
                },
                onDismiss = { showSeerrDisconnect = false },
            )
        }
        if (showAbsDisconnect) {
            DisconnectDialog(
                serviceName = "Audiobookshelf",
                onConfirm = {
                    showAbsDisconnect = false
                    viewModel.disconnectAudiobookshelf()
                },
                onDismiss = { showAbsDisconnect = false },
            )
        }
    }
}

@StringRes
private fun editorTitle(kind: EditorKind): Int =
    when (kind) {
        EditorKind.SEERR -> R.string.services_hub_editor_title_seerr
        EditorKind.ABS -> R.string.services_hub_editor_title_abs
        EditorKind.JELLYFIN -> R.string.services_hub_editor_title_jellyfin
        EditorKind.RATINGS -> R.string.services_hub_editor_title_ratings
    }

@Composable
private fun HubList(
    modifier: Modifier,
    serverName: String?,
    @StringRes finishLabel: Int,
    showRatings: Boolean,
    showWelcome: Boolean,
    showFinish: Boolean,
    seerrConnected: Boolean,
    absConnected: Boolean,
    remoteAdded: Boolean,
    ratingsCount: Int,
    seerrDots: List<Color>,
    absDots: List<Color>,
    remoteDots: List<Color>,
    ratingsDots: List<Color>,
    selected: EditorKind?,
    onSeerr: () -> Unit,
    onAbs: () -> Unit,
    onRemote: () -> Unit,
    onRatings: () -> Unit,
    onFinish: () -> Unit,
    onServerManagement: () -> Unit,
    markDone: () -> Unit,
) {
    Column(modifier = modifier) {
        Column(
            modifier =
                Modifier.weight(1f)
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
        ) {
            if (showWelcome) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text =
                        serverName?.let {
                            stringResource(R.string.services_hub_welcome_named, it)
                        } ?: stringResource(R.string.services_hub_welcome),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.services_hub_welcome_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            } else {
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServiceTile(
                    iconRes = R.drawable.ic_seerr_logo,
                    name = "Seerr",
                    connected = seerrConnected,
                    accent = JellyseerrColor,
                    statusText =
                        stringResource(
                            if (seerrConnected) R.string.services_hub_status_connected
                            else R.string.services_hub_status_not_set_up
                        ),
                    isSelected = selected == EditorKind.SEERR,
                    onClick = onSeerr,
                    modifier = Modifier.weight(1f),
                    trailingDots = seerrDots,
                )
                ServiceTile(
                    iconRes = R.drawable.ic_audiobookshelf_light,
                    name = "Audiobookshelf",
                    connected = absConnected,
                    accent = AudiobookshelfColor,
                    statusText =
                        stringResource(
                            if (absConnected) R.string.services_hub_status_connected
                            else R.string.services_hub_status_not_set_up
                        ),
                    isSelected = selected == EditorKind.ABS,
                    onClick = onAbs,
                    modifier = Modifier.weight(1f),
                    trailingDots = absDots,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServiceTile(
                    iconRes = R.drawable.ic_world,
                    name = stringResource(R.string.services_hub_tile_remote),
                    connected = remoteAdded,
                    accent = MaterialTheme.colorScheme.primary,
                    statusText =
                        stringResource(
                            if (remoteAdded) R.string.services_hub_status_added
                            else R.string.services_hub_status_not_set_up
                        ),
                    isSelected = selected == EditorKind.JELLYFIN,
                    onClick = onRemote,
                    modifier = Modifier.weight(1f),
                    trailingDots = remoteDots,
                )
                if (showRatings) {
                    ServiceTile(
                        iconRes = R.drawable.ic_visibility,
                        name = stringResource(R.string.services_hub_tile_ratings),
                        connected = ratingsCount > 0,
                        accent = tmdbColor,
                        statusText =
                            if (ratingsCount > 0)
                                stringResource(
                                    R.string.services_hub_status_ratings_count,
                                    ratingsCount,
                                )
                            else stringResource(R.string.services_hub_status_not_set_up),
                        isSelected = selected == EditorKind.RATINGS,
                        onClick = onRatings,
                        modifier = Modifier.weight(1f),
                        trailingDots = ratingsDots,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = {
                    markDone()
                    onServerManagement()
                },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(stringResource(R.string.services_hub_more_servers))
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showFinish) {
            Row(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = {
                        markDone()
                        onFinish()
                    }
                ) {
                    Text(stringResource(finishLabel))
                }
            }
        }
    }
}

@Composable
private fun EditorHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.action_close),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EditorEmpty() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.services_hub_editor_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditorContent(
    kind: EditorKind,
    server: ServerWithUserCount?,
    smViewModel: ServerManagementViewModel,
    settingsViewModel: SettingsViewModel,
    onDisconnect: () -> Unit,
    onAddJellyfin: (String) -> Unit,
    remoteVerifying: Boolean,
    remoteError: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (kind) {
            EditorKind.SEERR ->
                if (server != null) {
                    AddressHint(R.string.services_hub_address_service_seerr)
                    server.jellyseerrAddresses.forEach { addr ->
                        AddressRow(
                            url = addr.address,
                            isPrimary = false,
                            onSetPrimary = null,
                            onDelete = { smViewModel.deleteJellyseerrAddress(addr.id) },
                        )
                    }
                    AddAddressBar(
                        verifying = false,
                        error = null,
                        onAdd = { smViewModel.addJellyseerrAddress(server.server.id, it) },
                    )
                    Spacer(Modifier.height(4.dp))
                    DisconnectButton(onDisconnect)
                }
            EditorKind.ABS ->
                if (server != null) {
                    AddressHint(R.string.services_hub_address_service_abs)
                    server.audiobookshelfAddresses.forEach { addr ->
                        AddressRow(
                            url = addr.address,
                            isPrimary = false,
                            onSetPrimary = null,
                            onDelete = { smViewModel.deleteAudiobookshelfAddress(addr.id) },
                        )
                    }
                    AddAddressBar(
                        verifying = false,
                        error = null,
                        onAdd = { smViewModel.addAudiobookshelfAddress(server.server.id, it) },
                    )
                    Spacer(Modifier.height(4.dp))
                    DisconnectButton(onDisconnect)
                }
            EditorKind.JELLYFIN ->
                if (server != null) {
                    AddressHint(R.string.services_hub_address_service_jellyfin)
                    val primary = server.server.address
                    AddressRow(
                        url = primary,
                        isPrimary = true,
                        onSetPrimary = null,
                        onDelete = null,
                    )
                    server.addresses
                        .filter { it.address != primary }
                        .forEach { addr ->
                            AddressRow(
                                url = addr.address,
                                isPrimary = false,
                                onSetPrimary = {
                                    smViewModel.setPrimaryAddress(server.server.id, addr.address)
                                },
                                onDelete = { smViewModel.deleteAddress(addr.id) },
                            )
                        }
                    AddAddressBar(
                        verifying = remoteVerifying,
                        error = remoteError,
                        onAdd = onAddJellyfin,
                    )
                }
            EditorKind.RATINGS -> RatingsKeys(settingsViewModel)
        }
    }
}

@Composable
private fun AddressHint(@StringRes serviceName: Int) {
    Text(
        text = stringResource(R.string.services_hub_address_hint, stringResource(serviceName)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AddressRow(
    url: String,
    isPrimary: Boolean,
    onSetPrimary: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val (typeColor, typeLabelRes) =
        when {
            isLocalAddress(url) -> LocalColor to R.string.services_hub_address_type_local
            isTailscaleAddress(url) ->
                TailscaleColor to R.string.services_hub_address_type_tailscale
            else -> RemoteColor to R.string.services_hub_address_type_remote
        }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier =
            Modifier.fillMaxWidth().let {
                if (onSetPrimary != null) it.clickable(onClick = onSetPrimary) else it
            },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(8.dp).background(typeColor, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = url.substringAfter("://"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(typeLabelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor,
                )
            }
            if (isPrimary) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = stringResource(R.string.services_hub_address_primary),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            } else if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddAddressBar(verifying: Boolean, error: String?, onAdd: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AfinityTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = stringResource(R.string.services_hub_add_address_placeholder),
                enabled = !verifying,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        onAdd(input.trim())
                        input = ""
                    }
                },
                enabled = input.isNotBlank() && !verifying,
                modifier = Modifier.height(56.dp),
            ) {
                if (verifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.services_hub_action_verify))
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DisconnectButton(onDisconnect: () -> Unit) {
    TextButton(
        onClick = onDisconnect,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(stringResource(R.string.action_disconnect))
    }
}

@Composable
private fun RatingsKeys(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tmdb by viewModel.tmdbApiKey.collectAsStateWithLifecycle()
    val mdb by viewModel.mdbListApiKey.collectAsStateWithLifecycle()
    val omdb by viewModel.omdbApiKey.collectAsStateWithLifecycle()
    var editingLabel by remember { mutableStateOf<String?>(null) }
    Text(
        text = stringResource(R.string.services_hub_ratings_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RatingKeyRow(
        label = "TMDB",
        labelColor = tmdbColor,
        currentKey = tmdb,
        validating = uiState.isTmdbKeyValidating,
        error = uiState.tmdbKeyValidationError,
        editing = editingLabel == "TMDB",
        onEditingChange = { editingLabel = if (it) "TMDB" else null },
        onSave = { viewModel.validateAndSaveTmdbKey(it) { editingLabel = null } },
    )
    RatingKeyRow(
        label = "MDBList",
        labelColor = mdblistColor,
        currentKey = mdb,
        validating = uiState.isMdbListKeyValidating,
        error = uiState.mdbListKeyValidationError,
        editing = editingLabel == "MDBList",
        onEditingChange = { editingLabel = if (it) "MDBList" else null },
        onSave = { viewModel.validateAndSaveMdbListKey(it) { editingLabel = null } },
    )
    RatingKeyRow(
        label = "OMDb",
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        currentKey = omdb,
        validating = uiState.isOmdbKeyValidating,
        error = uiState.omdbKeyValidationError,
        editing = editingLabel == "OMDb",
        onEditingChange = { editingLabel = if (it) "OMDb" else null },
        onSave = { viewModel.validateAndSaveOmdbKey(it) { editingLabel = null } },
    )
}

@Composable
private fun RatingKeyRow(
    label: String,
    labelColor: Color,
    currentKey: String,
    validating: Boolean,
    error: String?,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSave: (String) -> Unit,
) {
    var input by remember(currentKey) { mutableStateOf(currentKey) }

    LaunchedEffect(editing) { if (!editing) input = currentKey }

    if (currentKey.isNotBlank() && !editing && !validating) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().clickable { onEditingChange(true) },
        ) {
            Row(
                modifier = Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                    Text(
                        text = maskKey(currentKey),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { onEditingChange(true) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.services_hub_action_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    } else {
        AfinityTextField(
            value = input,
            onValueChange = {
                input = it
                if (!editing) onEditingChange(true)
            },
            label = label,
            labelColor = labelColor,
            isError = error != null,
            enabled = !validating,
            supportingText = error,
            trailingIcon = {
                if (validating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else if (editing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onEditingChange(false) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_cancel_save),
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = CancelColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = { onSave(input.trim()) },
                            enabled = input.trim() != currentKey,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_save),
                                contentDescription = stringResource(R.string.action_save),
                                tint =
                                    if (input.trim() != currentKey) SaveColor
                                    else SaveColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ServiceTile(
    iconRes: Int,
    name: String,
    connected: Boolean,
    accent: Color,
    statusText: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingDots: List<Color> = emptyList(),
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color =
            when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                connected -> accent.copy(alpha = 0.13f)
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.height(120.dp).padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (connected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                if (trailingDots.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        trailingDots.forEach { dotColor ->
                            Box(modifier = Modifier.size(7.dp).background(dotColor, CircleShape))
                        }
                    }
                } else if (!connected) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier.size(7.dp)
                                .background(
                                    if (connected) accent
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.4f
                                        ),
                                    CircleShape,
                                )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (connected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisconnectDialog(serviceName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.services_hub_disconnect_title, serviceName)) },
        text = { Text(stringResource(R.string.services_hub_disconnect_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.action_disconnect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
