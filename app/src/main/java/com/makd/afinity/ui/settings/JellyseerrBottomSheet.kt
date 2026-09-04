package com.makd.afinity.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.DiscoveredServicesSection
import com.makd.afinity.ui.components.LoadingButton
import com.makd.afinity.ui.jellyseerr.JellyseerrLoginViewModel
import com.makd.afinity.util.isInsecurePublicUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JellyseerrLoginContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JellyseerrLoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val discoveredServices by viewModel.discoveredServices.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val autofillManager = LocalAutofillManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    val busy = uiState.isLoading || uiState.isQuickConnecting

    LaunchedEffect(Unit) { viewModel.discoverLocalServers() }

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            viewModel.resetLoginSuccess()
            onDismiss()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.jellyseerr_connect_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            val instanceTitle =
                uiState.publicSettings?.applicationTitle?.takeIf { it.isNotBlank() }
            Text(
                text =
                    if (instanceTitle != null) {
                        stringResource(
                            R.string.jellyseerr_connect_subtitle_found_fmt,
                            instanceTitle,
                        )
                    } else {
                        stringResource(R.string.jellyseerr_connect_subtitle)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DiscoveredServicesSection(
            services = discoveredServices,
            onSelect = { viewModel.updateServerUrl(it.url) },
        )

        InsecureConnectionBannerJellyseerr(serverUrl = uiState.serverUrl)

        AfinityTextField(
            value = uiState.serverUrl,
            onValueChange = viewModel::updateServerUrl,
            label = stringResource(R.string.label_server_url),
            placeholder = stringResource(R.string.jellyseerr_placeholder_url),
            leadingIcon = painterResource(id = R.drawable.ic_link_rotated),
            supportingText = uiState.serverUrlError,
            isError = uiState.serverUrlError != null,
            enabled = !busy,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions =
                KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth(),
        )

        val settings = uiState.publicSettings
        val showLoginMethodChoice =
            settings == null || (settings.localLogin && settings.mediaServerLogin)

        if (showLoginMethodChoice) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.label_login_method),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.useJellyfinAuth,
                        onClick = { viewModel.setUseJellyfinAuth(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        enabled = !busy,
                        colors =
                            SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    ) {
                        Text(stringResource(R.string.login_method_jellyfin))
                    }

                    SegmentedButton(
                        selected = !uiState.useJellyfinAuth,
                        onClick = { viewModel.setUseJellyfinAuth(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = !busy,
                        colors =
                            SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    ) {
                        Text(stringResource(R.string.login_method_local))
                    }
                }
            }
        }

        if (uiState.useJellyfinAuth && uiState.quickConnectAvailable) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.jellyseerr_quick_connect_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LoadingButton(
                    loading = uiState.isQuickConnecting,
                    text = stringResource(R.string.jellyseerr_quick_connect_button),
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.loginWithQuickConnect()
                    },
                    enabled = uiState.serverUrl.isNotBlank() && !uiState.isLoading,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.jellyseerr_login_divider_or),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AfinityTextField(
                value = uiState.email,
                onValueChange = viewModel::updateEmail,
                label =
                    if (uiState.useJellyfinAuth) stringResource(R.string.label_jellyfin_username)
                    else stringResource(R.string.label_seerr_email),
                placeholder =
                    if (uiState.useJellyfinAuth) stringResource(R.string.placeholder_username)
                    else stringResource(R.string.placeholder_email_example),
                leadingIcon = painterResource(id = R.drawable.ic_user),
                supportingText =
                    when {
                        uiState.emailError != null -> uiState.emailError
                        uiState.useJellyfinAuth ->
                            stringResource(R.string.jellyseerr_username_from_jellyfin)

                        else -> stringResource(R.string.jellyseerr_email_hint)
                    },
                isError = uiState.emailError != null,
                enabled = !busy && !uiState.useJellyfinAuth,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            if (uiState.useJellyfinAuth) KeyboardType.Text else KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions =
                    KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        contentType =
                            if (uiState.useJellyfinAuth) ContentType.Username
                            else ContentType.EmailAddress
                    },
            )

            AfinityTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                label =
                    if (uiState.useJellyfinAuth) stringResource(R.string.label_jellyfin_password)
                    else stringResource(R.string.label_seerr_password),
                leadingIcon = painterResource(id = R.drawable.ic_lock_filled),
                supportingText = uiState.passwordError,
                isError = uiState.passwordError != null,
                enabled = !busy,
                visualTransformation =
                    if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter =
                                painterResource(
                                    id =
                                        if (passwordVisible) {
                                            R.drawable.ic_visibility_off
                                        } else {
                                            R.drawable.ic_visibility
                                        }
                                ),
                            contentDescription =
                                if (passwordVisible) {
                                    stringResource(R.string.cd_hide_password)
                                } else {
                                    stringResource(R.string.cd_show_password)
                                },
                        )
                    }
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            autofillManager?.commit()
                            focusManager.clearFocus()
                            if (!busy) {
                                viewModel.login()
                            }
                        }
                    ),
                modifier =
                    Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            )
        }

        AnimatedVisibility(
            visible = uiState.error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(12.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_exclamation_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = uiState.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LoadingButton(
            loading = uiState.isLoading,
            text = stringResource(R.string.btn_login),
            onClick = {
                autofillManager?.commit()
                focusManager.clearFocus()
                viewModel.login()
            },
            enabled =
                !uiState.isQuickConnecting &&
                    uiState.serverUrl.isNotBlank() &&
                    uiState.email.isNotBlank() &&
                    (uiState.useJellyfinAuth || uiState.password.isNotBlank()),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyseerrBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    viewModel: JellyseerrLoginViewModel = hiltViewModel(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 0.dp,
    ) {
        JellyseerrLoginContent(onDismiss = onDismiss, viewModel = viewModel)
    }
}

@Composable
private fun InsecureConnectionBannerJellyseerr(serverUrl: String) {
    val showWarning by remember(serverUrl) { derivedStateOf { isInsecurePublicUrl(serverUrl) } }

    AnimatedVisibility(
        visible = showWarning,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_info),
                    contentDescription = stringResource(R.string.cd_security_warning),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text =
                        "Warning: Connecting over HTTP sends your password in plain text. HTTPS is highly recommended.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
