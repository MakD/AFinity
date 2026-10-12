package com.makd.afinity.ui.admin.libraries

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ServerFolder
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.SettingsItem

private val FOLDER_LIST_MAX_HEIGHT = 320.dp

@Composable
internal fun ServerFolderPickerDialog(
    loadFolders: suspend (path: String?) -> Result<List<ServerFolder>>,
    isDuplicate: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var visited by remember { mutableStateOf(emptyList<String>()) }
    var pathText by rememberSaveable { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<ServerFolder>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val current = visited.lastOrNull()
    val duplicate = pathText.isNotBlank() && isDuplicate(pathText)

    LaunchedEffect(current) {
        folders = null
        failed = false
        loadFolders(current).onSuccess { folders = it }.onFailure { failed = true }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.library_folder_add),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AfinityTextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.library_folder_path_label),
                    isError = duplicate,
                    supportingText =
                        if (duplicate) stringResource(R.string.library_folder_duplicate)
                        else stringResource(R.string.library_folder_browse_hint),
                    trailingIcon = {
                        IconButton(
                            onClick = { visited = listOf(pathText.trim()) },
                            enabled = pathText.isNotBlank(),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_right),
                                contentDescription = stringResource(R.string.library_folder_open),
                            )
                        }
                    },
                )
                FolderList(
                    folders = folders,
                    failed = failed,
                    canGoUp = visited.isNotEmpty(),
                    onGoUp = {
                        visited = visited.dropLast(1)
                        pathText = visited.lastOrNull().orEmpty()
                    },
                    onOpen = { folder ->
                        visited = visited + folder.path
                        pathText = folder.path
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pathText.trim()) },
                enabled = pathText.isNotBlank() && !duplicate,
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun FolderList(
    folders: List<ServerFolder>?,
    failed: Boolean,
    canGoUp: Boolean,
    onGoUp: () -> Unit,
    onOpen: (ServerFolder) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = FOLDER_LIST_MAX_HEIGHT)) {
        if (canGoUp) {
            item(key = "up") {
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_chevron_left),
                    title = stringResource(R.string.library_folder_up),
                    onClick = onGoUp,
                    trailing = {},
                )
            }
        }
        when {
            failed -> item(key = "failed") { FolderListNote(R.string.library_folder_unreadable) }
            folders == null ->
                item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            folders.isEmpty() ->
                item(key = "empty") { FolderListNote(R.string.library_folder_empty) }
            else ->
                items(count = folders.size) { index ->
                    val folder = folders[index]
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_folder),
                        title = folder.name,
                        onClick = { onOpen(folder) },
                    )
                }
        }
    }
}

@Composable
private fun FolderListNote(@StringRes message: Int) {
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}
