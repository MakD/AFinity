package com.makd.afinity.ui.admin.users

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminDiscardDialog
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.components.ReportUnsavedChanges
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsSwitchItem

private data class PolicyToggle(
    val key: String,
    @param:StringRes val label: Int,
    @param:StringRes val summary: Int? = null,
)

private val ACCESS_TOGGLES =
    listOf(
        PolicyToggle(PolicyKeys.IS_HIDDEN, R.string.perm_hidden, R.string.perm_hidden_summary),
        PolicyToggle(PolicyKeys.ENABLE_REMOTE_ACCESS, R.string.perm_remote_access),
    )

private val PLAYBACK_TOGGLES =
    listOf(
        PolicyToggle(PolicyKeys.ENABLE_MEDIA_PLAYBACK, R.string.perm_media_playback),
        PolicyToggle(PolicyKeys.ENABLE_AUDIO_TRANSCODING, R.string.perm_audio_transcoding),
        PolicyToggle(PolicyKeys.ENABLE_VIDEO_TRANSCODING, R.string.perm_video_transcoding),
        PolicyToggle(
            PolicyKeys.ENABLE_REMUXING,
            R.string.perm_remuxing,
            R.string.perm_remuxing_summary,
        ),
    )

private val MEDIA_TOGGLES =
    listOf(
        PolicyToggle(PolicyKeys.ENABLE_DOWNLOADING, R.string.perm_downloads),
        PolicyToggle(
            PolicyKeys.ENABLE_DELETION,
            R.string.perm_deletion,
            R.string.perm_deletion_summary,
        ),
        PolicyToggle(PolicyKeys.ENABLE_COLLECTIONS, R.string.perm_collections),
        PolicyToggle(PolicyKeys.ENABLE_SUBTITLES, R.string.perm_subtitles),
    )

private val LIVE_TV_TOGGLES =
    listOf(
        PolicyToggle(PolicyKeys.ENABLE_LIVE_TV_ACCESS, R.string.perm_live_tv_access),
        PolicyToggle(PolicyKeys.ENABLE_LIVE_TV_MANAGEMENT, R.string.perm_live_tv_manage),
    )

private val REMOTE_CONTROL_TOGGLES =
    listOf(
        PolicyToggle(PolicyKeys.ENABLE_CONTROL_OTHERS, R.string.perm_control_others),
        PolicyToggle(PolicyKeys.ENABLE_SHARED_DEVICES, R.string.perm_shared_devices),
    )

@Composable
fun UserPermissionsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserPermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    UserPermissionsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onSetFlag = viewModel::setFlag,
        onSetLibraryEnabled = viewModel::setLibraryEnabled,
        onSetDeviceEnabled = viewModel::setDeviceEnabled,
        onMaxSessionsChange = viewModel::setMaxSessionsText,
        onLockoutChange = viewModel::setLockoutText,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
private fun UserPermissionsContent(
    state: UserPermissionsUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onSetFlag: (key: String, enabled: Boolean) -> Unit,
    onSetLibraryEnabled: (libraryId: String, enabled: Boolean) -> Unit,
    onSetDeviceEnabled: (deviceId: String, enabled: Boolean) -> Unit,
    onMaxSessionsChange: (String) -> Unit,
    onLockoutChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose: () -> Unit = { if (state.isDirty) showDiscard = true else onBackClick() }

    BackHandler(enabled = state.isDirty) { showDiscard = true }
    ReportUnsavedChanges(state.isDirty)

    if (showDiscard) {
        AdminDiscardDialog(
            message = stringResource(R.string.user_permissions_discard_message),
            onDiscard = {
                showDiscard = false
                onBackClick()
            },
            onDismiss = { showDiscard = false },
        )
    }

    AdminScaffold(
        title = state.userName.ifEmpty { stringResource(R.string.title_user_permissions) },
        onBackClick = requestClose,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (state.isDirty) SaveButton(saving = state.isSaving, onSave = onSave)
        },
    ) { contentPadding ->
        AdminLoadContent(
            data = state.policy,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) {
            val editable = !state.isSaving
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SettingsGroup(title = stringResource(R.string.section_perm_access)) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.perm_administrator),
                        subtitle =
                            when {
                                state.isSelf -> stringResource(R.string.perm_administrator_self)
                                state.isOnlyAdmin ->
                                    stringResource(R.string.perm_administrator_last)
                                else -> stringResource(R.string.perm_administrator_summary)
                            },
                        checked = state.flag(PolicyKeys.IS_ADMINISTRATOR),
                        onCheckedChange = { onSetFlag(PolicyKeys.IS_ADMINISTRATOR, it) },
                        enabled = editable && state.canChangeAdministrator,
                    )
                    ToggleRows(ACCESS_TOGGLES, state, editable, onSetFlag, leadingDivider = true)
                }

                SettingsGroup(title = stringResource(R.string.section_perm_libraries)) {
                    val allLibraries = state.flag(PolicyKeys.ENABLE_ALL_FOLDERS)
                    SettingsSwitchItem(
                        title = stringResource(R.string.perm_all_libraries),
                        checked = allLibraries,
                        onCheckedChange = { onSetFlag(PolicyKeys.ENABLE_ALL_FOLDERS, it) },
                        enabled = editable,
                    )
                    if (!allLibraries) {
                        val enabledFolders = state.enabledFolders
                        state.libraries.forEach { library ->
                            SettingsDivider()
                            SettingsSwitchItem(
                                title = library.name,
                                checked = library.id in enabledFolders,
                                onCheckedChange = { onSetLibraryEnabled(library.id, it) },
                                enabled = editable,
                            )
                        }
                    }
                }

                SettingsGroup(title = stringResource(R.string.section_perm_playback)) {
                    ToggleRows(PLAYBACK_TOGGLES, state, editable, onSetFlag)
                }
                SettingsGroup(title = stringResource(R.string.section_perm_media)) {
                    ToggleRows(MEDIA_TOGGLES, state, editable, onSetFlag)
                }
                SettingsGroup(title = stringResource(R.string.section_perm_live_tv)) {
                    ToggleRows(LIVE_TV_TOGGLES, state, editable, onSetFlag)
                }
                SettingsGroup(title = stringResource(R.string.section_perm_remote_control)) {
                    ToggleRows(REMOTE_CONTROL_TOGGLES, state, editable, onSetFlag)
                }

                if (!state.flag(PolicyKeys.IS_ADMINISTRATOR)) {
                    SettingsGroup(title = stringResource(R.string.section_perm_devices)) {
                        val allDevices = state.flag(PolicyKeys.ENABLE_ALL_DEVICES)
                        SettingsSwitchItem(
                            title = stringResource(R.string.perm_all_devices),
                            subtitle = stringResource(R.string.perm_all_devices_summary),
                            checked = allDevices,
                            onCheckedChange = { onSetFlag(PolicyKeys.ENABLE_ALL_DEVICES, it) },
                            enabled = editable,
                        )
                        if (!allDevices) {
                            val enabledDevices = state.enabledDevices
                            state.devices.forEach { device ->
                                SettingsDivider()
                                SettingsSwitchItem(
                                    title = device.name,
                                    subtitle = device.appName,
                                    checked = device.id in enabledDevices,
                                    onCheckedChange = { onSetDeviceEnabled(device.id, it) },
                                    enabled = editable,
                                )
                            }
                        }
                    }
                }

                SettingsGroup(title = stringResource(R.string.section_perm_limits)) {
                    AfinityTextField(
                        value = state.maxSessionsText,
                        onValueChange = onMaxSessionsChange,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        label = stringResource(R.string.perm_max_sessions_label),
                        enabled = editable,
                        isError = !state.isMaxSessionsValid,
                        supportingText = stringResource(R.string.perm_max_sessions_hint),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    AfinityTextField(
                        value = state.lockoutText,
                        onValueChange = onLockoutChange,
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        label = stringResource(R.string.perm_lockout_label),
                        enabled = editable,
                        isError = !state.isLockoutValid,
                        supportingText = stringResource(R.string.perm_lockout_hint),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRows(
    toggles: List<PolicyToggle>,
    state: UserPermissionsUiState,
    enabled: Boolean,
    onSetFlag: (key: String, enabled: Boolean) -> Unit,
    leadingDivider: Boolean = false,
) {
    toggles.forEachIndexed { index, toggle ->
        if (index > 0 || leadingDivider) SettingsDivider()
        SettingsSwitchItem(
            title = stringResource(toggle.label),
            subtitle = toggle.summary?.let { stringResource(it) },
            checked = state.flag(toggle.key),
            onCheckedChange = { onSetFlag(toggle.key, it) },
            enabled = enabled,
        )
    }
}

@Composable
private fun SaveButton(saving: Boolean, onSave: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = { if (!saving) onSave() },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                if (saving) stringResource(R.string.admin_saving)
                else stringResource(R.string.admin_save_changes)
            )
        }
    }
}
