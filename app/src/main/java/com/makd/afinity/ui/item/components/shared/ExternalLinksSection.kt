package com.makd.afinity.ui.item.components.shared

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem

@Composable
fun ExternalLinksSection(item: AfinityItem) {
    val context = LocalContext.current
    val defaultLinkName = stringResource(R.string.external_link_default_name)
    val externalLinks = remember(item) { getExternalLinks(item, defaultLinkName) }

    if (externalLinks.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.external_links_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(externalLinks) { link ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                    context.startActivity(intent)
                                }
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = link.iconRes),
                            contentDescription = link.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(16.dp)
                        )
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

private fun getExternalLinks(item: AfinityItem, defaultName: String): List<ExternalLink> {
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
                name = externalUrl.name ?: defaultName,
                url = url,
                iconRes = iconRes
            )
        )
    }

    return links
}