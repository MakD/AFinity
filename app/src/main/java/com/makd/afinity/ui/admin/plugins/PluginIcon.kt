package com.makd.afinity.ui.admin.plugins

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.theme.CardDimensions

@Composable
internal fun PluginCover(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    placeholderSize: Dp = 40.dp,
) {
    Card(
        modifier = modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
        shape = RoundedCornerShape(cornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (imageUrl != null) {
                AsyncImage(
                    imageUrl = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_puzzle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(placeholderSize),
                )
            }
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
        "Metadata" -> R.string.plugin_category_metadata
        "Notifications" -> R.string.plugin_category_notifications
        "Other" -> R.string.plugin_category_other
        else -> null
    }
