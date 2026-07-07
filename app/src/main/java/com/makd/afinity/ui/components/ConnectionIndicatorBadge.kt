package com.makd.afinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.server.ConnectionType

@Composable
fun connectionIndicatorColor(connectionType: ConnectionType): Color =
    when (connectionType) {
        ConnectionType.LOCAL -> Color(0xFF4CAF50)
        ConnectionType.TAILSCALE -> Color(0xFF2196F3)
        ConnectionType.REMOTE -> Color(0xFFFF9800)
        ConnectionType.OFFLINE -> Color(0xFFF44336)
    }

private fun connectionIndicatorIcon(connectionType: ConnectionType): Int =
    when (connectionType) {
        ConnectionType.LOCAL -> R.drawable.ic_wifi
        ConnectionType.TAILSCALE -> R.drawable.ic_security
        ConnectionType.REMOTE -> R.drawable.ic_link
        ConnectionType.OFFLINE -> R.drawable.ic_cloud_off
    }

@Composable
private fun connectionIndicatorContentDescription(connectionType: ConnectionType): String =
    when (connectionType) {
        ConnectionType.LOCAL -> stringResource(R.string.cd_local_connection)
        ConnectionType.TAILSCALE -> stringResource(R.string.cd_tailscale_connection)
        ConnectionType.REMOTE -> stringResource(R.string.cd_remote_connection)
        ConnectionType.OFFLINE -> stringResource(R.string.cd_offline_mode)
    }

@Composable
fun ConnectionIndicatorBadge(
    connectionType: ConnectionType,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    iconSize: Dp = 10.dp,
    ringPadding: Dp = 1.5.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .drawBehind { drawCircle(color = Color.Black, blendMode = BlendMode.Clear) }
                .padding(ringPadding)
                .background(
                    color = connectionIndicatorColor(connectionType),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = connectionIndicatorIcon(connectionType)),
            contentDescription = connectionIndicatorContentDescription(connectionType),
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}