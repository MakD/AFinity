package com.makd.afinity.ui.item.components.shared

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem

@Composable
fun ExternalLinksSection(item: AfinityItem) {
    val context = LocalContext.current
    val externalLinks = getExternalLinks(item)

    if (externalLinks.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "External Links",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(externalLinks) { link ->
                    Card(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(48.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = link.iconRes),
                                contentDescription = link.name,
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ExternalLink(
    val name: String,
    val url: String,
    val iconRes: Int
)

private fun getExternalLinks(item: AfinityItem): List<ExternalLink> {
    val links = mutableListOf<ExternalLink>()

    val externalUrls = item.externalUrls ?: return emptyList()

    externalUrls.forEach { externalUrl ->
        val url = externalUrl.url ?: return@forEach
        val lowerUrl = url.lowercase()

        val iconRes = when {
            "imdb" in lowerUrl -> R.drawable.ic_imdb_logo
            "themoviedb.org/collection" in lowerUrl -> R.drawable.ic_tmdb_collection
            "themoviedb.org/movie" in lowerUrl -> R.drawable.ic_tmdb
            "themoviedb.org" in lowerUrl -> R.drawable.ic_tmdb
            "tvdb" in lowerUrl -> R.drawable.ic_tvdb
            "trakt" in lowerUrl -> R.drawable.ic_trakt
            "tvmaze" in lowerUrl -> R.drawable.ic_tvmaze
            else -> R.drawable.ic_link
        }

        links.add(
            ExternalLink(
                name = externalUrl.name ?: "External Link",
                url = url,
                iconRes = iconRes
            )
        )
    }

    return links
}