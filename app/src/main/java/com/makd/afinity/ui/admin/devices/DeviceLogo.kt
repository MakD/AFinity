package com.makd.afinity.ui.admin.devices

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.makd.afinity.R
import com.makd.afinity.ui.components.AsyncImage

internal const val OWN_APP_NAME = "AFinity"

@Composable
internal fun DeviceLogo(
    appName: String,
    deviceName: String,
    iconUrl: String?,
    logoUrl: String?,
    tint: Color,
    logoSize: Dp,
    iconSize: Dp,
    iconCorner: Dp,
    modifier: Modifier = Modifier,
) {
    if (appName.equals(OWN_APP_NAME, ignoreCase = true)) {
        Icon(
            painter = painterResource(id = R.drawable.ic_afinity_logo),
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(logoSize),
        )
        return
    }

    val imageUrl = iconUrl ?: logoUrl
    val isLogo = iconUrl == null
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }

    if (imageUrl != null && !imageFailed) {
        AsyncImage(
            imageUrl = imageUrl,
            contentDescription = null,
            modifier =
                if (isLogo) modifier.size(logoSize)
                else modifier.size(iconSize).clip(RoundedCornerShape(iconCorner)),
            contentScale = ContentScale.Fit,
            colorFilter = if (isLogo) ColorFilter.tint(tint) else null,
            onError = { imageFailed = true },
        )
    } else {
        Icon(
            painter = painterResource(id = deviceKind(appName, deviceName).iconRes()),
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(logoSize),
        )
    }
}

internal fun DeviceKind.iconRes(): Int =
    when (this) {
        DeviceKind.PHONE -> R.drawable.ic_device_mobile
        DeviceKind.TABLET -> R.drawable.ic_device_tablet
        DeviceKind.TV -> R.drawable.ic_tv
        DeviceKind.BROWSER -> R.drawable.ic_world
        DeviceKind.MUSIC -> R.drawable.ic_headphones
        DeviceKind.SERVICE -> R.drawable.ic_server
        DeviceKind.OTHER -> R.drawable.ic_devices
    }
