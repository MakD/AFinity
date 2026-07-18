package com.makd.afinity.ui.settings.servers.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.Permissions
import com.makd.afinity.data.models.jellyseerr.hasPermission
import com.makd.afinity.data.models.jellyseerr.isAdmin
import com.makd.afinity.ui.settings.servers.JellyseerrStats
import com.makd.afinity.ui.settings.servers.ServerWithUserCount
import com.makd.afinity.ui.settings.servers.components.AddAddressField
import com.makd.afinity.ui.settings.servers.components.DetailRow
import com.makd.afinity.ui.settings.servers.components.LoadingState
import com.makd.afinity.ui.settings.servers.components.SectionHeader
import com.makd.afinity.ui.settings.servers.components.ServiceAddressItem
import com.makd.afinity.ui.settings.servers.components.StatChip
import java.util.UUID

@Composable
internal fun JellyseerrTabContent(
    serverWithCount: ServerWithUserCount,
    jellyseerrStats: JellyseerrStats?,
    statsLoading: Boolean,
) {
    if (statsLoading) {
        LoadingState()
    } else if (jellyseerrStats != null) {
        jellyseerrStats.user?.let { user ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(stringResource(R.string.section_user_profile_header))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DetailRow(
                            label = stringResource(R.string.label_name),
                            value =
                                user.displayName
                                    ?: user.username
                                    ?: stringResource(R.string.user_unknown),
                        )
                        DetailRow(
                            label = stringResource(R.string.label_role),
                            value =
                                if (user.isAdmin()) stringResource(R.string.role_admin)
                                else stringResource(R.string.role_user),
                            valueColor =
                                if (user.isAdmin()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        )

                        val reqPerm = stringResource(R.string.permission_request)
                        val autoPerm = stringResource(R.string.permission_auto_approve)
                        val fourKPerm = stringResource(R.string.meta_res_4k)
                        val managePerm = stringResource(R.string.permission_manage)

                        val permissions = buildList {
                            if (user.hasPermission(Permissions.REQUEST)) add(reqPerm)
                            if (user.hasPermission(Permissions.AUTO_APPROVE)) add(autoPerm)
                            if (user.hasPermission(Permissions.REQUEST_4K)) add(fourKPerm)
                            if (user.hasPermission(Permissions.MANAGE_REQUESTS)) add(managePerm)
                        }
                        if (permissions.isNotEmpty()) {
                            DetailRow(
                                label = stringResource(R.string.label_permissions),
                                value = permissions.joinToString(", "),
                            )
                        }
                        DetailRow(
                            label = stringResource(R.string.label_movie_quota),
                            value =
                                if (user.movieQuotaLimit != null && user.movieQuotaLimit > 0)
                                    stringResource(
                                        R.string.quota_days_fmt,
                                        user.movieQuotaLimit,
                                        user.movieQuotaDays ?: 7,
                                    )
                                else stringResource(R.string.label_unlimited),
                        )
                        DetailRow(
                            label = stringResource(R.string.label_tv_quota),
                            value =
                                if (user.tvQuotaLimit != null && user.tvQuotaLimit > 0)
                                    stringResource(
                                        R.string.quota_days_fmt,
                                        user.tvQuotaLimit,
                                        user.tvQuotaDays ?: 7,
                                    )
                                else stringResource(R.string.label_unlimited),
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.section_request_stats))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = stringResource(R.string.stat_total),
                    value = jellyseerrStats.totalRequests.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label = stringResource(R.string.stat_pending),
                    value = jellyseerrStats.pendingRequests.toString(),
                    modifier = Modifier.weight(1f),
                    valueColor = Color(0xFFF59E0B),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatChip(
                    label = stringResource(R.string.stat_approved),
                    value = jellyseerrStats.approvedRequests.toString(),
                    modifier = Modifier.weight(1f),
                    valueColor = Color(0xFF10B981),
                )
                StatChip(
                    label = stringResource(R.string.stat_available),
                    value = jellyseerrStats.availableRequests.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun JellyseerrManageAddresses(
    serverWithCount: ServerWithUserCount,
    onDeleteAddress: (UUID) -> Unit,
    onAddAddress: (String) -> Unit,
) {
    if (serverWithCount.jellyseerrAddresses.isNotEmpty()) {
        serverWithCount.jellyseerrAddresses.forEach { address ->
            ServiceAddressItem(
                address = address.address,
                onDelete = { onDeleteAddress(address.id) },
            )
        }
    } else {
        Text(
            text = stringResource(R.string.server_no_alternate_addresses),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
    AddAddressField(
        placeholder = stringResource(R.string.jellyseerr_placeholder_url),
        onAdd = onAddAddress,
    )
}
