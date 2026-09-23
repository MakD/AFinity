package com.makd.afinity.ui.item.components.shared

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.makd.afinity.R
import com.makd.afinity.data.models.tmdb.TmdbRegionProviders
import com.makd.afinity.data.models.tmdb.TmdbWatchProvider
import com.makd.afinity.ui.components.AsyncImage
import java.util.Locale
import timber.log.Timber

private const val STRIP_LOGO_LIMIT = 4

private enum class WatchKind {
    SUBSCRIPTION,
    FREE,
    ADS,
    RENT,
    BUY,
}

private data class WatchOption(val provider: TmdbWatchProvider, val kinds: List<WatchKind>)

private data class WatchGroups(val stream: List<WatchOption>, val purchase: List<WatchOption>)

private fun groupOptions(
    vararg sources: Pair<WatchKind, List<TmdbWatchProvider>>
): List<WatchOption> {
    val byProvider = LinkedHashMap<Int, Pair<TmdbWatchProvider, MutableList<WatchKind>>>()
    sources.forEach { (kind, providers) ->
        providers.forEach { provider ->
            byProvider
                .getOrPut(provider.provider_id) { provider to mutableListOf() }
                .second
                .add(kind)
        }
    }
    return byProvider.values
        .map { (provider, kinds) -> WatchOption(provider, kinds) }
        .sortedBy { it.provider.display_priority ?: Int.MAX_VALUE }
}

private fun TmdbRegionProviders.toGroups(): WatchGroups =
    WatchGroups(
        stream =
            groupOptions(
                WatchKind.SUBSCRIPTION to flatrate,
                WatchKind.FREE to free,
                WatchKind.ADS to ads,
            ),
        purchase = groupOptions(WatchKind.RENT to rent, WatchKind.BUY to buy),
    )

@Composable
private fun WatchOption.caption(): String? =
    when {
        WatchKind.SUBSCRIPTION in kinds -> null
        WatchKind.FREE in kinds -> stringResource(R.string.where_to_watch_free)
        WatchKind.ADS in kinds -> stringResource(R.string.where_to_watch_ads)
        WatchKind.RENT in kinds && WatchKind.BUY in kinds ->
            stringResource(R.string.where_to_watch_rent_and_buy)

        WatchKind.RENT in kinds -> stringResource(R.string.where_to_watch_rent)
        WatchKind.BUY in kinds -> stringResource(R.string.where_to_watch_buy)
        else -> null
    }

private fun openWatchLink(context: Context, link: String?) {
    if (link.isNullOrBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
    } catch (e: Exception) {
        Timber.w(e, "No activity to open watch provider link")
    }
}

private fun regionName(region: String): String =
    Locale("", region).displayCountry.ifBlank { region }

@Composable
fun WhereToWatchSection(providers: TmdbRegionProviders, modifier: Modifier = Modifier) {
    if (!providers.hasAny) return
    val context = LocalContext.current
    val groups = remember(providers) { providers.toGroups() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailSectionTitle(
                text = stringResource(R.string.where_to_watch_title),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = regionName(providers.region),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (groups.stream.isNotEmpty()) {
            ProviderTileRow(
                label = stringResource(R.string.where_to_watch_stream),
                options = groups.stream,
                onClick = { openWatchLink(context, providers.link) },
            )
        }
        if (groups.purchase.isNotEmpty()) {
            ProviderTileRow(
                label = stringResource(R.string.where_to_watch_rent_or_buy),
                options = groups.purchase,
                onClick = { openWatchLink(context, providers.link) },
            )
        }

        AttributionRow(onOpen = { openWatchLink(context, providers.link) })
    }
}

@Composable
private fun ProviderTileRow(label: String, options: List<WatchOption>, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(options, key = { it.provider.provider_id }) { option ->
                ProviderTile(option = option, onClick = onClick)
            }
        }
    }
}

@Composable
private fun ProviderTile(option: WatchOption, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(56.dp).clickable(role = Role.Button, onClick = onClick),
    ) {
        ProviderLogo(provider = option.provider, size = 40.dp, cornerRadius = 10.dp)
        Text(
            text = option.provider.provider_name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        option.caption()?.let { caption ->
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProviderLogo(provider: TmdbWatchProvider, size: Dp, cornerRadius: Dp) {
    AsyncImage(
        imageUrl = provider.logoUrl(),
        contentDescription = provider.provider_name,
        targetWidth = size,
        targetHeight = size,
        modifier = Modifier.size(size).clip(RoundedCornerShape(cornerRadius)),
    )
}

@Composable
private fun AttributionRow(onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.where_to_watch_attribution),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.where_to_watch_all_options),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.clickable(role = Role.Button, onClick = onOpen).padding(vertical = 4.dp),
        )
    }
}

@Composable
fun WhereToWatchStrip(providers: TmdbRegionProviders, modifier: Modifier = Modifier) {
    if (!providers.hasAny) return
    val groups = remember(providers) { providers.toGroups() }
    var showSheet by remember { mutableStateOf(false) }

    val leading = groups.stream.ifEmpty { groups.purchase }
    val shown = leading.take(STRIP_LOGO_LIMIT)
    val label =
        if (groups.stream.isNotEmpty()) stringResource(R.string.where_to_watch_stream_on)
        else stringResource(R.string.where_to_watch_rent_or_buy_on)
    val trailing =
        when {
            groups.stream.isNotEmpty() && groups.purchase.isNotEmpty() ->
                pluralStringResource(
                    R.plurals.where_to_watch_more_rent_or_buy,
                    groups.purchase.size,
                    groups.purchase.size,
                )

            leading.size > shown.size ->
                pluralStringResource(
                    R.plurals.where_to_watch_more,
                    leading.size - shown.size,
                    leading.size - shown.size,
                )

            else -> null
        }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button) { showSheet = true },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            shown.forEach { option ->
                ProviderLogo(provider = option.provider, size = 32.dp, cornerRadius = 8.dp)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (trailing != null) {
                Text(
                    text = trailing,
                    style =
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.where_to_watch_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (showSheet) {
        WhereToWatchSheet(
            providers = providers,
            groups = groups,
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun WhereToWatchSheet(
    providers: TmdbRegionProviders,
    groups: WatchGroups,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.where_to_watch_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = regionName(providers.region),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (groups.stream.isNotEmpty()) {
                SheetGroup(
                    label = stringResource(R.string.where_to_watch_stream),
                    options = groups.stream,
                    onClick = { openWatchLink(context, providers.link) },
                )
            }
            if (groups.purchase.isNotEmpty()) {
                SheetGroup(
                    label = stringResource(R.string.where_to_watch_rent_or_buy),
                    options = groups.purchase,
                    onClick = { openWatchLink(context, providers.link) },
                )
            }

            AttributionRow(onOpen = { openWatchLink(context, providers.link) })
        }
    }
}

@Composable
private fun SheetGroup(label: String, options: List<WatchOption>, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onClick)
                        .padding(vertical = 4.dp),
            ) {
                ProviderLogo(provider = option.provider, size = 40.dp, cornerRadius = 10.dp)
                Text(
                    text = option.provider.provider_name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                option.caption()?.let { caption ->
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
