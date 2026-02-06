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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
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
import com.makd.afinity.ui.jellyseerr.JellyseerrLoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyseerrBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    viewModel: JellyseerrLoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            viewModel.resetLoginSuccess()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.jellyseerr_connect_title),
                    style =
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = stringResource(R.string.jellyseerr_connect_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = viewModel::updateServerUrl,
                label = { Text(stringResource(R.string.label_server_url)) },
                placeholder = { Text(stringResource(R.string.jellyseerr_placeholder_url)) },
                leadingIcon = {
                    Icon(
                        painterResource(id = R.drawable.ic_link_rotated),
                        contentDescription = null,
                    )
                },
                supportingText = uiState.serverUrlError?.let { { Text(it) } },
                isError = uiState.serverUrlError != null,
                enabled = !uiState.isLoading,
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions =
                    KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

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
                        enabled = !uiState.isLoading,
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
                        enabled = !uiState.isLoading,
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

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::updateEmail,
                    label = {
                        Text(
                            if (uiState.useJellyfinAuth)
                                stringResource(R.string.label_jellyfin_username)
                            else stringResource(R.string.label_seerr_email)
                        )
                    },
                    placeholder = {
                        Text(
                            if (uiState.useJellyfinAuth)
                                stringResource(R.string.placeholder_username)
                            else stringResource(R.string.placeholder_email_example)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user),
                            contentDescription = null,
                        )
                    },
                    supportingText = uiState.emailError?.let { { Text(it) } },
                    isError = uiState.emailError != null,
                    enabled = !uiState.isLoading,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                if (uiState.useJellyfinAuth) KeyboardType.Text
                                else KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    keyboardActions =
                        KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
                    label = {
                        Text(
                            if (uiState.useJellyfinAuth)
                                stringResource(R.string.label_jellyfin_password)
                            else stringResource(R.string.label_seerr_password)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lock_filled),
                            contentDescription = null,
                        )
                    },
                    supportingText = uiState.passwordError?.let { { Text(it) } },
                    isError = uiState.passwordError != null,
                    enabled = !uiState.isLoading,
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
                                if (!uiState.isLoading) {
                                    viewModel.login()
                                }
                            }
                        ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
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

            Button(
                onClick = viewModel::login,
                enabled =
                    !uiState.isLoading &&
                        uiState.serverUrl.isNotBlank() &&
                        uiState.email.isNotBlank() &&
                        uiState.password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.btn_login),
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
