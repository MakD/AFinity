package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.logoImageUrlWithTransparency
import com.makd.afinity.data.models.extensions.showLogoImageUrl
import com.makd.afinity.data.models.extensions.withLogoFormat
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.navigation.LocalSkipServerImageResize
import com.makd.afinity.ui.components.AsyncImage

@Composable
fun ColumnScope.MediaLogoHeader(item: AfinityItem, isLandscape: Boolean) {
    val webpLogo = !LocalSkipServerImageResize.current
    val logoUrl =
        if (item is AfinitySeason) {
            item.images.showLogoImageUrl?.withLogoFormat(webp = webpLogo)
        } else item.images.logoImageUrlWithTransparency(webp = webpLogo)
    val name = if (item is AfinitySeason) item.seriesName else item.name

    MediaLogoHeader(logoUrl = logoUrl, name = name, isLandscape = isLandscape)
}

@Composable
fun ColumnScope.MediaLogoHeader(logoUrl: String?, name: String, isLandscape: Boolean) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }

    if (logoUrl != null) {
        AsyncImage(
            imageUrl = logoUrl,
            contentDescription = stringResource(R.string.cd_logo_fmt, name),
            targetWidth = if (isLandscape) 300.dp else screenWidthDp * 0.8f,
            targetHeight = if (isLandscape) 150.dp else 120.dp,
            modifier =
                (if (isLandscape) Modifier.widthIn(max = 300.dp) else Modifier.fillMaxWidth(0.8f))
                    .height(if (isLandscape) 150.dp else 120.dp)
                    .align(if (isLandscape) Alignment.Start else Alignment.CenterHorizontally),
            contentScale = ContentScale.Fit,
            alignment = if (isLandscape) Alignment.CenterStart else Alignment.Center,
        )
    } else {
        Text(
            text = name,
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (!isLandscape) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
