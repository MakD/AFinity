package com.makd.afinity.ui.admin.plugins

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.components.AsyncImage

@Composable
internal fun PluginIcon(imageUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                imageUrl = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_view_module),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@StringRes
internal fun pluginCategoryLabel(category: String): Int? =
    when (category) {
        "Administration" -> R.string.plugin_category_administration
        "General" -> R.string.plugin_category_general
        "Anime" -> R.string.plugin_category_anime
        "Books" -> R.string.plugin_category_books
        "LiveTV" -> R.string.plugin_category_live_tv
        "MoviesAndShows" -> R.string.plugin_category_movies_and_shows
        "Music" -> R.string.plugin_category_music
        "Subtitles" -> R.string.plugin_category_subtitles
        "Other" -> R.string.plugin_category_other
        else -> null
    }
