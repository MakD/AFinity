package com.makd.afinity.ui.settings.servers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.ui.components.AfinityActionMenu
import com.makd.afinity.ui.components.DetailMenuAction
import com.makd.afinity.ui.components.connectionIndicatorColor
import com.makd.afinity.ui.components.connectionLabel
import com.makd.afinity.ui.components.connectionTypeIcon
import com.makd.afinity.ui.item.components.shared.AwardGold

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
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val connectionType = serverWithCount.currentConnectionType
                val isActive = serverWithCount.isActiveServer
                val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                val cardColor = MaterialTheme.colorScheme.surfaceContainerLow

                Box(modifier = Modifier.size(44.dp)) {
                    Box(
                        modifier =
                            Modifier.matchParentSize()
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
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .offset(x = 3.dp, y = 3.dp)
                                .size(12.dp)
                                .background(cardColor, CircleShape)
                                .padding(2.dp)
                                .background(
                                    if (isActive) connectionIndicatorColor(connectionType)
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
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
                        val labelColor =
                            if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else mutedColor
                        Icon(
                            painter = painterResource(id = connectionTypeIcon(connectionType)),
                            contentDescription = null,
                            tint =
                                if (isActive) connectionIndicatorColor(connectionType)
                                else mutedColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = connectionLabel(connectionType),
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            maxLines = 1,
                        )
                        val address = serverWithCount.currentConnectionUrl.substringAfter("://")
                        if (address.isNotBlank()) {
                            Text(
                                text = " · $address",
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (isActive)
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.7f,
                                        )
                                    else mutedColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
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
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    AfinityActionMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        actions =
                            listOf(
                                DetailMenuAction(
                                    label = stringResource(R.string.title_edit_server),
                                    iconRes = R.drawable.ic_edit,
                                    onClick = onEditClick,
                                ),
                                DetailMenuAction(
                                    label = stringResource(R.string.cd_delete_server),
                                    iconRes = R.drawable.ic_delete,
                                    onClick = onDeleteClick,
                                    destructive = true,
                                ),
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val lineHeight = with(LocalDensity.current) { 22.sp.toDp() }.coerceAtLeast(22.dp)
            val lineGap = 6.dp

            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(
                    modifier = Modifier.widthIn(min = 64.dp).width(IntrinsicSize.Max),
                    verticalArrangement = Arrangement.spacedBy(lineGap),
                ) {
                    ServiceChip(
                        label = stringResource(R.string.service_label_seerr),
                        activeColor = JellyseerrColor,
                        addressType =
                            serverWithCount.jellyseerrConnectionType ?: ConnectionType.REMOTE,
                        isActive = status.jellyseerrConfigured,
                        modifier = Modifier.fillMaxWidth().height(lineHeight),
                    )
                    ServiceChip(
                        label = stringResource(R.string.service_label_abs),
                        activeColor = AudiobookshelfColor,
                        addressType =
                            serverWithCount.audiobookshelfConnectionType ?: ConnectionType.REMOTE,
                        isActive = status.audiobookshelfConfigured,
                        modifier = Modifier.fillMaxWidth().height(lineHeight),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier =
                        Modifier.fillMaxHeight()
                            .width(1.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            ),
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(lineGap),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(lineHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                        LedIndicator(
                            activeColor = omdbColor,
                            label = stringResource(R.string.service_label_omdb),
                            isActive = status.omdbConfigured,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(lineHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LedIndicator(
                            activeColor = lastFmColor,
                            label = stringResource(R.string.service_label_lastfm),
                            isActive = status.lastFmConfigured,
                        )
                        LedIndicator(
                            activeColor = AwardGold,
                            label = stringResource(R.string.service_label_wikidata),
                            isActive = status.wikidataEnabled,
                        )
                        LedIndicator(
                            activeColor = MaterialTheme.colorScheme.primary,
                            label = stringResource(R.string.service_label_subtitledb),
                            isActive = status.subtitleDbEnabled,
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
    addressType: ConnectionType,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val connIcon = connectionTypeIcon(addressType)
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
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        ) {
            Text(
                text = label.uppercase(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                color = contentColor,
                maxLines = 1,
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
private fun RowScope.LedIndicator(activeColor: Color, label: String, isActive: Boolean) {
    val unlitColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val rimColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val textColor =
        if (isActive) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f).padding(end = 4.dp),
    ) {
        Box(
            modifier =
                Modifier.size(7.dp).drawBehind {
                    val dotRadius = size.minDimension / 2f
                    if (isActive) {
                        val glowRadius = dotRadius * 2f
                        drawCircle(
                            brush =
                                Brush.radialGradient(
                                    0.5f to activeColor.copy(alpha = 0.3f),
                                    0.75f to activeColor.copy(alpha = 0.1f),
                                    1f to Color.Transparent,
                                    center = center,
                                    radius = glowRadius,
                                ),
                            radius = glowRadius,
                        )
                        drawCircle(color = activeColor, radius = dotRadius)
                    } else {
                        drawCircle(color = unlitColor, radius = dotRadius)
                        drawCircle(
                            color = rimColor,
                            radius = dotRadius - 0.25.dp.toPx(),
                            style = Stroke(width = 0.5.dp.toPx()),
                        )
                    }
                },
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
