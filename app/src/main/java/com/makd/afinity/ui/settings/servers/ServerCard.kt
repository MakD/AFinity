package com.makd.afinity.ui.settings.servers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R

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
                                AddressType.LOCAL ->
                                    Triple(
                                        R.drawable.ic_wifi,
                                        LocalColor,
                                        stringResource(R.string.address_type_local),
                                    )
                                AddressType.TAILSCALE ->
                                    Triple(
                                        R.drawable.ic_security,
                                        TailscaleColor,
                                        stringResource(R.string.address_type_tailscale),
                                    )
                                AddressType.REMOTE ->
                                    Triple(
                                        R.drawable.ic_link,
                                        RemoteColor,
                                        stringResource(R.string.address_type_remote),
                                    )
                            }
                        val mutedColor =
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        Icon(
                            painter = painterResource(id = connIcon),
                            contentDescription = null,
                            tint = if (serverWithCount.isActiveServer) connColor else mutedColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = connText,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (serverWithCount.isActiveServer)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else mutedColor,
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dots_vertical),
                            contentDescription = stringResource(R.string.cd_server_options),
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
                                Text(
                                    stringResource(R.string.title_edit_server),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
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
                                    stringResource(R.string.cd_delete_server),
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
                        label = stringResource(R.string.service_label_seerr),
                        activeColor = JellyseerrColor,
                        addressType =
                            serverWithCount.jellyseerrConnectionType ?: AddressType.REMOTE,
                        isActive = status.jellyseerrConfigured,
                    )
                    ServiceChip(
                        label = stringResource(R.string.service_label_abs),
                        activeColor = AudiobookshelfColor,
                        addressType =
                            serverWithCount.audiobookshelfConnectionType ?: AddressType.REMOTE,
                        isActive = status.audiobookshelfConfigured,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    LedIndicator(
                        activeColor = tmdbColor,
                        label = stringResource(R.string.service_label_tmdb),
                        isActive = status.tmdbConfigured,
                    )
                    LedIndicator(
                        activeColor = mdblistColor,
                        label = stringResource(R.string.service_label_mdblist),
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
        border = BorderStroke(1.dp, borderColor),
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
