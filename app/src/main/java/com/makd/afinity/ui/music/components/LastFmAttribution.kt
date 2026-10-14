package com.makd.afinity.ui.music.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import java.net.URLEncoder

@Composable
fun LastFmAttribution(artistName: String?, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val url =
        artistName
            ?.takeIf { it.isNotBlank() }
            ?.let { "https://www.last.fm/music/" + URLEncoder.encode(it, "UTF-8") }
            ?: "https://www.last.fm"
    Text(
        text = stringResource(R.string.lastfm_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .clickable { runCatching { uriHandler.openUri(url) } }
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
