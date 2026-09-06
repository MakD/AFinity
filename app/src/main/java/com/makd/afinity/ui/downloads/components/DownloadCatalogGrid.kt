package com.makd.afinity.ui.downloads.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.downloads.DownloadCatalogEntry
import com.makd.afinity.ui.downloads.DownloadCategory
import com.makd.afinity.ui.downloads.DownloadCategoryUsage
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun downloadCategoryLabel(category: DownloadCategory): String =
    when (category) {
        DownloadCategory.VIDEO -> stringResource(R.string.section_videos)
        DownloadCategory.MUSIC -> stringResource(R.string.section_download_music)
        DownloadCategory.AUDIOBOOK -> stringResource(R.string.section_download_audiobooks)
        DownloadCategory.PODCAST -> stringResource(R.string.section_download_podcasts)
    }

private fun aspectRatioFor(category: DownloadCategory): Float =
    if (category == DownloadCategory.VIDEO) CardDimensions.ASPECT_RATIO_PORTRAIT
    else CardDimensions.ASPECT_RATIO_SQUARE

fun LazyGridScope.downloadCatalogSections(
    catalog: List<DownloadCatalogEntry>,
    formatSize: (Long) -> String,
    selectionMode: Boolean,
    selectedKeys: Set<String>,
    onEntryClick: (DownloadCatalogEntry) -> Unit,
    onEntryLongClick: (DownloadCatalogEntry) -> Unit,
) {
    val byCategory = catalog.groupBy { it.category }

    DownloadCategory.entries.forEach { category ->
        val inCategory = byCategory[category].orEmpty()
        if (inCategory.isEmpty()) return@forEach

        item(key = "header_${category.name}", span = { GridItemSpan(maxLineSpan) }) {
            DownloadSectionHeader(downloadCategoryLabel(category))
        }

        items(count = inCategory.size, key = { index -> inCategory[index].key }) { index ->
            val entry = inCategory[index]
            DownloadPosterCard(
                entry = entry,
                formatSize = formatSize,
                selectionMode = selectionMode,
                selected = entry.key in selectedKeys,
                onClick = { onEntryClick(entry) },
                onLongClick = { onEntryLongClick(entry) },
            )
        }
    }
}

@Composable
fun DownloadSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadPosterCard(
    entry: DownloadCatalogEntry,
    formatSize: (Long) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(aspectRatioFor(entry.category))
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                imageUrl = entry.imageUrl,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (selected) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp),
                            )
                )
            }

            if (!entry.isAvailable) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.66f))
                )
                CardBadge(
                    text = stringResource(R.string.storage_unavailable_badge),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }

            if (selectionMode) {
                SelectionIndicator(
                    selected = selected,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            } else if (entry.isGroup) {
                CardBadge(
                    text =
                        if (entry.category == DownloadCategory.MUSIC) {
                            pluralStringResource(
                                R.plurals.download_count_tracks,
                                entry.childCount,
                                entry.childCount,
                            )
                        } else {
                            pluralStringResource(
                                R.plurals.download_count_episodes,
                                entry.childCount,
                                entry.childCount,
                            )
                        },
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }

            CardBadge(
                text = formatSize(entry.sizeBytes),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }

        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = CardDimensions.CardTextSpacing),
        )

        entry.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.Black.copy(alpha = 0.45f)
                )
                .border(
                    width = 2.dp,
                    color =
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CardBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = Color.Black.copy(alpha = 0.7f),
    content: Color = Color.White,
) {
    Surface(color = container, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun downloadCategoryColor(category: DownloadCategory): Color =
    when (category) {
        DownloadCategory.VIDEO -> MaterialTheme.colorScheme.primary
        DownloadCategory.MUSIC -> MaterialTheme.colorScheme.tertiary
        DownloadCategory.AUDIOBOOK -> MaterialTheme.colorScheme.secondary
        DownloadCategory.PODCAST -> MaterialTheme.colorScheme.primaryContainer
    }

@Composable
fun DownloadStorageStrip(
    categories: List<DownloadCategoryUsage>,
    selectedCategory: DownloadCategory?,
    deviceUsedBytes: Long,
    deviceTotalBytes: Long,
    freeBytes: Long,
    formatSize: (Long) -> String,
    onSelectCategory: (DownloadCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadsBytes = categories.sumOf { it.bytes }
    val fractionOf: (Long) -> Float = { bytes ->
        if (deviceTotalBytes > 0L) (bytes.toFloat() / deviceTotalBytes).coerceIn(0f, 1f) else 0f
    }
    val downloadsFraction = fractionOf(downloadsBytes)
    val otherFraction =
        fractionOf((deviceUsedBytes - downloadsBytes).coerceAtLeast(0L))
            .coerceAtMost(1f - downloadsFraction)
    val remainder = (1f - downloadsFraction - otherFraction).coerceAtLeast(0f)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        stringResource(
                            R.string.downloads_storage_used_fmt,
                            formatSize(downloadsBytes),
                        ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text =
                        stringResource(R.string.downloads_storage_free_fmt, formatSize(freeBytes)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                categories.forEach { usage ->
                    val fraction = fractionOf(usage.bytes)
                    if (fraction <= 0f) return@forEach
                    val dimmed = selectedCategory != null && selectedCategory != usage.category
                    Box(
                        modifier =
                            Modifier.weight(fraction)
                                .fillMaxHeight()
                                .background(
                                    downloadCategoryColor(usage.category)
                                        .copy(alpha = if (dimmed) 0.3f else 1f)
                                )
                                .clickable {
                                    onSelectCategory(
                                        usage.category.takeIf { it != selectedCategory }
                                    )
                                }
                    )
                }
                if (otherFraction > 0f) {
                    Box(
                        modifier =
                            Modifier.weight(otherFraction)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                if (remainder > 0f) {
                    Spacer(modifier = Modifier.weight(remainder))
                }
            }

            if (categories.isNotEmpty()) {
                DownloadCategoryLegend(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    formatSize = formatSize,
                    onSelectCategory = onSelectCategory,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadCategoryLegend(
    categories: List<DownloadCategoryUsage>,
    selectedCategory: DownloadCategory?,
    formatSize: (Long) -> String,
    onSelectCategory: (DownloadCategory?) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { usage ->
            val selected = selectedCategory == usage.category
            val color = downloadCategoryColor(usage.category)
            Row(
                modifier =
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) color.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable {
                            onSelectCategory(usage.category.takeIf { !selected })
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(
                    text = downloadCategoryLabel(usage.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatSize(usage.bytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
