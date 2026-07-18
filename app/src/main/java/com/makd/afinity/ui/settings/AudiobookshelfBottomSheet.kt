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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.audiobookshelf.login.AudiobookshelfLoginViewModel
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.LoadingButton
import com.makd.afinity.util.isInsecurePublicUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudiobookshelfLoginContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudiobookshelfLoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    @Suppress("UNUSED_VARIABLE") val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onDismiss()
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
                text = stringResource(R.string.pref_audiobookshelf),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(R.string.audiobookshelf_connect),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        InsecureConnectionBannerAbs(serverUrl = uiState.serverUrl)

        AfinityTextField(
            value = uiState.serverUrl,
            onValueChange = viewModel::updateServerUrl,
            label = stringResource(R.string.label_server_url),
            placeholder = stringResource(R.string.abs_server_url_placeholder),
            leadingIcon = painterResource(id = R.drawable.ic_link_rotated),
            supportingText = uiState.serverUrlError,
            isError = uiState.serverUrlError != null,
            enabled = !uiState.isLoggingIn,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions =
                KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AfinityTextField(
                value = uiState.username,
                onValueChange = viewModel::updateUsername,
                label = stringResource(R.string.placeholder_username),
                placeholder = stringResource(R.string.placeholder_username),
                leadingIcon = painterResource(id = R.drawable.ic_user),
                enabled = !uiState.isLoggingIn,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions =
                    KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )

            AfinityTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                label = stringResource(R.string.login_password_label),
                leadingIcon = painterResource(id = R.drawable.ic_lock_filled),
                enabled = !uiState.isLoggingIn,
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
                            focusManager.clearFocus()
                            if (!uiState.isLoggingIn) {
                                viewModel.login()
                            }
                        }
                    ),
                modifier = Modifier.fillMaxWidth(),
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
            loading = uiState.isLoggingIn,
            text = stringResource(R.string.btn_login),
            onClick = {
                focusManager.clearFocus()
                viewModel.login()
            },
            enabled = uiState.serverUrl.isNotBlank() && uiState.username.isNotBlank(),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    viewModel: AudiobookshelfLoginViewModel = hiltViewModel(),
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
        AudiobookshelfLoginContent(onDismiss = onDismiss, viewModel = viewModel)
    }
}

@Composable
private fun InsecureConnectionBannerAbs(serverUrl: String) {
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
