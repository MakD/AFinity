package com.makd.afinity.ui.item.components.shared

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityItem
import kotlin.math.pow

private const val LOGO_BASE_HEIGHT_DP = 16f
private const val LOGO_REFERENCE_ASPECT = 1.4f
private const val LOGO_ASPECT_COMPENSATION = 0.35f
private const val LOGO_MAX_HEIGHT_DP = 20f

@Composable
fun ExternalLinksSection(item: AfinityItem) {
    ExternalLinksSection(externalUrls = item.externalUrls)
}

@Composable
fun ExternalLinksSection(externalUrls: List<AfinityExternalUrl>?) {
    val context = LocalContext.current
    val defaultLinkName = stringResource(R.string.external_link_default_name)
    val externalLinks = remember(externalUrls) { getExternalLinks(externalUrls, defaultLinkName) }

    if (externalLinks.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(externalLinks, key = { it.url }) { link ->
                val painter = painterResource(id = link.iconRes)
                val intrinsic = painter.intrinsicSize
                val aspect =
                    if (intrinsic.isSpecified && intrinsic.height > 0f) {
                        intrinsic.width / intrinsic.height
                    } else 1f
                val narrowBoost =
                    (LOGO_REFERENCE_ASPECT / aspect)
                        .coerceAtLeast(1f)
                        .pow(LOGO_ASPECT_COMPENSATION)
                val logoHeight =
                    (LOGO_BASE_HEIGHT_DP * narrowBoost).coerceAtMost(LOGO_MAX_HEIGHT_DP).dp

                Box(
                    modifier =
                        Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                    context.startActivity(intent)
                                },
                            )
                            .padding(top = 8.dp, bottom = 8.dp, end = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painter,
                        contentDescription = link.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(logoHeight),
                    )
                }
            }
        }
    }
}

private data class ExternalLink(val name: String, val url: String, val iconRes: Int)

private fun getExternalLinks(
    externalUrls: List<AfinityExternalUrl>?,
    defaultName: String,
): List<ExternalLink> {
    val links = mutableListOf<ExternalLink>()

    if (externalUrls == null) return emptyList()

    externalUrls.forEach { externalUrl ->
        val url = externalUrl.url ?: return@forEach
        val lowerUrl = url.lowercase()

        val iconRes =
            when {
                "anidb" in lowerUrl -> R.drawable.ic_anidb
                "musicbrainz" in lowerUrl -> R.drawable.ic_musicbrainz_logo
                "theaudiodb" in lowerUrl -> R.drawable.ic_audiodb
                "audiodb" in lowerUrl -> R.drawable.ic_audiodb
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
            ExternalLink(name = externalUrl.name ?: defaultName, url = url, iconRes = iconRes)
        )
    }

    val unique = links.distinctBy { it.url }
    val preferred =
        unique
            .filter { it.iconRes != R.drawable.ic_link }
            .groupBy { it.iconRes }
            .values
            .mapNotNull { group -> group.minByOrNull { linkPrecedence(it.url.lowercase()) }?.url }
            .toSet()

    return unique.filter { it.iconRes == R.drawable.ic_link || it.url in preferred }
}

private fun linkPrecedence(lowerUrl: String): Int =
    when {
        "/release/" in lowerUrl -> 0
        "/album/" in lowerUrl -> 0
        "/release-group/" in lowerUrl -> 1
        "/artist/" in lowerUrl -> 2
        else -> 0
    }
