package com.makd.afinity.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.makd.afinity.R
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User

enum class LoginMethod {
    PASSWORD,
    QUICK_CONNECT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass
) {
    val loginState by viewModel.loginState.collectAsStateWithLifecycle(
        initialValue = LoginState(
            uiState = LoginUiState(),
            serverUrl = "",
            publicUsers = emptyList(),
            hasServerUrl = false,
            isConnectedToServer = false
        )
    )
    val savedServers by viewModel.savedServers.collectAsStateWithLifecycle()
    val savedUsers by viewModel.savedUsers.collectAsStateWithLifecycle()

    var selectedLoginMethod by remember { mutableStateOf(LoginMethod.PASSWORD) }

    LaunchedEffect(Unit) {
        viewModel.discoverServers()
    }

    LaunchedEffect(loginState.uiState.isLoggedIn) {
        if (loginState.uiState.isLoggedIn) {
            onLoginSuccess()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 550.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                LoginHeader(
                    isConnected = loginState.uiState.isConnectedToServer,
                    serverUrl = loginState.serverUrl
                )

                Spacer(modifier = Modifier.height(48.dp))

                AnimatedContent(
                    targetState = loginState.uiState.isConnectedToServer,
                    transitionSpec = {
                        (slideInVertically { height -> height } + fadeIn()).togetherWith(
                            slideOutVertically { height -> -height } + fadeOut()
                        ) using SizeTransform(clip = false)
                    },
                    label = "LoginStateTransition"
                ) { isConnected ->
                    if (!isConnected) {
                        ServerConnectionContent(
                            serverUrl = loginState.serverUrl,
                            savedServers = savedServers,
                            discoveredServers = loginState.uiState.discoveredServers,
                            isConnecting = loginState.uiState.isConnecting,
                            isDiscovering = loginState.uiState.isDiscovering,
                            showAddServerInput = loginState.uiState.showAddServerInput,
                            onUrlChange = viewModel::setServerUrl,
                            onConnectToServer = viewModel::connectToServer,
                            onSavedServerSelect = viewModel::selectServer,
                            onDiscoveredServerSelect = { server ->
                                val url = server.address.ifBlank {
                                    "http://${server.name}:8096"
                                }
                                viewModel.setServerUrl(url)
                                viewModel.connectToServer()
                            },
                            onDiscoverServers = viewModel::discoverServers,
                            onAddNewServer = viewModel::showAddNewServer,
                            onCancelAddServer = viewModel::cancelAddServer
                        )
                    } else {
                        UserLoginContent(
                            uiState = loginState.uiState,
                            savedUsers = savedUsers,
                            publicUsers = loginState.publicUsers,
                            serverUrl = loginState.serverUrl,
                            selectedMethod = selectedLoginMethod,
                            onMethodChange = { selectedLoginMethod = it },
                            onUsernameChange = viewModel::updateUsername,
                            onPasswordChange = viewModel::updatePassword,
                            onUserSelect = viewModel::selectUser,
                            onSavedUserLogin = viewModel::loginWithSavedUser,
                            onLogin = viewModel::login,
                            onQuickConnectStart = viewModel::startQuickConnect,
                            onQuickConnectCancel = viewModel::cancelQuickConnect,
                            onChangeServer = { viewModel.setServerUrl("") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                AnimatedVisibility(
                    visible = loginState.uiState.error != null,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_info),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = loginState.uiState.error ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginHeader(
    isConnected: Boolean,
    serverUrl: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!isConnected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.login_connect_to_jellyfin),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_server),
                        contentDescription = stringResource(R.string.cd_server_icon),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = serverUrl.removePrefix("http://").removePrefix("https://"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = stringResource(R.string.login_welcome_back),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ServerConnectionContent(
    serverUrl: String,
    savedServers: List<Server>,
    discoveredServers: List<Server>,
    isConnecting: Boolean,
    isDiscovering: Boolean,
    showAddServerInput: Boolean,
    onUrlChange: (String) -> Unit,
    onConnectToServer: () -> Unit,
    onSavedServerSelect: (Server) -> Unit,
    onDiscoveredServerSelect: (Server) -> Unit,
    onDiscoverServers: () -> Unit,
    onAddNewServer: () -> Unit,
    onCancelAddServer: () -> Unit
) {
    val showSavedServers = savedServers.isNotEmpty() && !showAddServerInput

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {

        if (showSavedServers) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.login_saved_servers),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onAddNewServer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_plus),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.login_add_new_server))
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(savedServers) { server ->
                        SavedServerCard(
                            server = server,
                            isSelected = false,
                            isConnecting = false,
                            onClick = { onSavedServerSelect(server) }
                        )
                    }
                }
            }
        } else {
            Column {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onUrlChange,
                    placeholder = { Text(stringResource(R.string.login_server_url_placeholder)) },
                    label = { Text(stringResource(R.string.login_server_url_label)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_link_rotated),
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConnectToServer() }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onConnectToServer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = serverUrl.isNotBlank() && !isConnecting,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text(
                            stringResource(R.string.login_btn_connect),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                if (savedServers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onCancelAddServer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.login_btn_cancel))
                    }
                }
            }
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.login_local_network),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onDiscoverServers) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_refresh),
                                contentDescription = stringResource(R.string.login_refresh)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (discoveredServers.isNotEmpty()) {
                discoveredServers.forEach { server ->
                    Surface(
                        onClick = { onDiscoveredServerSelect(server) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_server),
                                contentDescription = stringResource(R.string.cd_server_icon),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    server.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    server.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (!isDiscovering) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.login_no_servers_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedServerCard(
    server: Server,
    isSelected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = server.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    server.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun UserLoginContent(
    uiState: LoginUiState,
    savedUsers: List<User>,
    publicUsers: List<User>,
    serverUrl: String,
    selectedMethod: LoginMethod,
    onMethodChange: (LoginMethod) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUserSelect: (User) -> Unit,
    onSavedUserLogin: (User) -> Unit,
    onLogin: () -> Unit,
    onQuickConnectStart: () -> Unit,
    onQuickConnectCancel: () -> Unit,
    onChangeServer: () -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }

    Column {
        TextButton(
            onClick = onChangeServer,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.login_switch_server))
        }

        Spacer(modifier = Modifier.height(16.dp))

        val allUsers = (savedUsers + publicUsers).distinctBy { it.id }

        if (allUsers.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(allUsers) { user ->
                    val isSelected = uiState.selectedUser?.id == user.id
                    UserAvatarItem(
                        user = user,
                        isSelected = isSelected,
                        serverUrl = serverUrl,
                        onClick = {
                            if (savedUsers.any { it.id == user.id }) {
                                onSavedUserLogin(user)
                            } else {
                                onUserSelect(user)
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        SecondaryTabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color.Transparent,
            divider = {},
            indicator = { }
        ) {
            Tab(
                selected = tabIndex == 0,
                onClick = {
                    tabIndex = 0
                    onMethodChange(LoginMethod.PASSWORD)
                },
                text = { Text(stringResource(R.string.login_method_password)) }
            )
            Tab(
                selected = tabIndex == 1,
                onClick = {
                    tabIndex = 1
                    onMethodChange(LoginMethod.QUICK_CONNECT)
                },
                text = { Text(stringResource(R.string.login_method_quick_connect)) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(targetState = tabIndex, label = "AuthMethod") { index ->
            if (index == 0) {
                PasswordForm(
                    uiState = uiState,
                    onUsernameChange = onUsernameChange,
                    onPasswordChange = onPasswordChange,
                    onLogin = onLogin
                )
            } else {
                QuickConnectView(
                    uiState = uiState,
                    onStart = onQuickConnectStart,
                    onCancel = onQuickConnectCancel
                )
            }
        }
    }
}

@Composable
private fun UserAvatarItem(
    user: User,
    isSelected: Boolean,
    serverUrl: String,
    onClick: () -> Unit
) {
    val imageUrl = user.primaryImageTag?.let {
        "$serverUrl/Users/${user.id}/Images/Primary?tag=$it"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(if (isSelected) 84.dp else 72.dp)
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_user_avatar),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = user.name.first().uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = user.name,
            style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PasswordForm(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.login_username_label)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_user),
                    contentDescription = null
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.login_password_label)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lock_filled),
                    contentDescription = null
                )
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        painter = if (passwordVisible) painterResource(id = R.drawable.ic_visibility_off) else painterResource(
                            id = R.drawable.ic_visibility
                        ),
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onLogin()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !uiState.isLoggingIn && uiState.username.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (uiState.isLoggingIn) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    stringResource(R.string.login_btn_sign_in),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun QuickConnectView(
    uiState: LoginUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        if (uiState.quickConnectCode == null && !uiState.isLoggingIn) {
            Icon(
                painter = painterResource(id = R.drawable.ic_qrcode),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.login_quick_connect_description),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.login_btn_generate_code))
            }
        } else if (uiState.quickConnectCode != null) {
            Text(
                text = uiState.quickConnectCode,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.login_quick_connect_instructions),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onCancel) {
                Icon(painter = painterResource(id = R.drawable.ic_close), contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.login_btn_cancel))
            }
        } else {
            CircularProgressIndicator()
        }
    }
}