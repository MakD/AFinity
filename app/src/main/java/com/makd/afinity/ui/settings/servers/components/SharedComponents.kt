package com.makd.afinity.ui.settings.servers.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.ui.components.EmptyState
import com.makd.afinity.ui.settings.servers.AudiobookshelfColor
import com.makd.afinity.ui.settings.servers.JellyseerrColor
import com.makd.afinity.ui.settings.servers.ServerWithUserCount
import com.makd.afinity.ui.settings.servers.UserServiceInfo

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
internal fun DetailRow(
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
internal fun LoadingState() {
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
            stringResource(R.string.state_loading_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun StatChip(
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

@Composable
internal fun ActiveConnectionCard(
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
                        text = stringResource(R.string.server_active_connection),
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
                        text =
                            stringResource(R.string.server_backup_routes_fmt, totalAddresses - 1),
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
                    contentDescription = stringResource(R.string.cd_manage_addresses),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun ManageAddressesView(
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
                    Modifier.background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        CircleShape,
                    ),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.cd_back),
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
internal fun SelectableAddressRow(
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
                        text = stringResource(R.string.server_primary_connection),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_remove),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ServiceAddressItem(
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
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun AddAddressField(placeholder: String, onAdd: (String) -> Unit) {
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
                contentDescription = stringResource(R.string.cd_add_address),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
internal fun ServiceStatusIcon(
    modifier: Modifier = Modifier,
    @DrawableRes activeIconRes: Int,
    @DrawableRes inactiveIconRes: Int = activeIconRes,
    contentDescription: String,
    isActive: Boolean,
    haloColor: Color? = null,
) {
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
            painter = painterResource(id = if (isActive) activeIconRes else inactiveIconRes),
            contentDescription = contentDescription,
            tint =
                if (isActive) Color.Unspecified
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun UserServiceRow(userInfo: UserServiceInfo) {
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
                    contentDescription = stringResource(R.string.cd_jellyseerr),
                    isActive = userInfo.serviceStatus.jellyseerrConfigured,
                    haloColor = JellyseerrColor,
                )
                ServiceStatusIcon(
                    activeIconRes = R.drawable.ic_audiobookshelf_colored,
                    inactiveIconRes = R.drawable.ic_audiobookshelf_light,
                    contentDescription = stringResource(R.string.cd_audiobookshelf),
                    isActive = userInfo.serviceStatus.audiobookshelfConfigured,
                    haloColor = AudiobookshelfColor,
                )
                ServiceStatusIcon(
                    activeIconRes = R.drawable.ic_tmdb,
                    contentDescription = stringResource(R.string.cd_tmdb),
                    isActive = userInfo.serviceStatus.tmdbConfigured,
                )
                ServiceStatusIcon(
                    activeIconRes = R.drawable.ic_mdblist,
                    contentDescription = stringResource(R.string.cd_mdblist),
                    isActive = userInfo.serviceStatus.mdbListConfigured,
                )
            }
        }
    }
}

@Composable
internal fun EmptyServersState(modifier: Modifier = Modifier) {
    EmptyState(
        icon = painterResource(id = R.drawable.ic_server),
        title = stringResource(R.string.empty_servers_title),
        message = stringResource(R.string.empty_servers_message),
        modifier = modifier,
    )
}

@Composable
internal fun DeleteServerConfirmationDialog(
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
