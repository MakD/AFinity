package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.Library
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.audiobookshelf.libraries.components.AudiobookCard
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun AudiobookshelfHomeTab(
    sections: List<PersonalizedSection>,
    libraries: List<Library>,
    serverUrl: String?,
    onItemClick: (LibraryItem) -> Unit,
    onBrowseSeries: () -> Unit,
    onBrowseLibrary: (Library) -> Unit,
    isLoading: Boolean,
    widthSizeClass: WindowWidthSizeClass,
) {
    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        sections.isEmpty() -> {
            val playerOffset = LocalPlayerOffset.current
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = playerOffset),
            ) {
                item(key = "library_shortcuts") {
                    LibraryShortcutsRow(
                        libraries = libraries,
                        onBrowseSeries = onBrowseSeries,
                        onBrowseLibrary = onBrowseLibrary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                item(key = "no_personalized_content") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.abs_no_personalized_content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        else -> {
            val cardWidth = widthSizeClass.portraitWidth
            val cardHeight = CardDimensions.calculateHeight(cardWidth, 1f)
            val fixedRowHeight = cardHeight + 8.dp + 20.dp + 18.dp
            val playerOffset = LocalPlayerOffset.current
            val columnState = rememberLazyListState()

            LazyColumn(
                state = columnState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = playerOffset),
            ) {
                item(key = "library_shortcuts") {
                    LibraryShortcutsRow(
                        libraries = libraries,
                        onBrowseSeries = onBrowseSeries,
                        onBrowseLibrary = onBrowseLibrary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                items(items = sections, key = { it.id }) { section ->
                    val rowListState = rememberLazyListState()
                    val currentFirstItemId = section.items.firstOrNull()?.id
                    var prevFirstItemId by remember { mutableStateOf(currentFirstItemId) }

                    if (prevFirstItemId != currentFirstItemId) {
                        if (prevFirstItemId != null && currentFirstItemId != null) {
                            rowListState.requestScrollToItem(0)
                        }
                        prevFirstItemId = currentFirstItemId
                    }

                    Column {
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                            Text(
                                text = section.label,
                                style =
                                    MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )

                            LazyRow(
                                state = rowListState,
                                modifier = Modifier.height(fixedRowHeight),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp),
                            ) {
                                items(
                                    items = section.items,
                                    key = { item -> "${section.id}_${item.id}" },
                                ) { item ->
                                    AudiobookCard(
                                        item = item,
                                        serverUrl = serverUrl,
                                        onClick = { onItemClick(item) },
                                        modifier = Modifier.width(cardWidth),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class ShortcutTarget {
    data object Series : ShortcutTarget()

    data class ForLibrary(val library: Library) : ShortcutTarget()
}

private data class LibraryShortcut(
    val key: String,
    val target: ShortcutTarget,
    val label: String,
    val iconRes: Int,
    val gradientStart: Color,
    val gradientEnd: Color,
)

private val LIBRARY_SHORTCUT_GRADIENTS =
    listOf(
        Color(0xFFB1AADA) to Color(0xFF5A5482),
        Color(0xFFE2AE95) to Color(0xFF965243),
        Color(0xFFA4C4D6) to Color(0xFF50787A),
        Color(0xFFAED1AE) to Color(0xFF4F7E70),
        Color(0xFFD4A5C9) to Color(0xFF7B4F8C),
    )

@Composable
private fun LibraryShortcutsRow(
    libraries: List<Library>,
    onBrowseSeries: () -> Unit,
    onBrowseLibrary: (Library) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val cardWidth =
        when {
            configuration.screenWidthDp < 600 -> 240.dp
            configuration.screenWidthDp < 840 -> 260.dp
            else -> 320.dp
        }

    val seriesLabel = stringResource(R.string.abs_tab_series)
    val shortcuts =
        remember(libraries, seriesLabel) {
            val targets: List<ShortcutTarget> =
                buildList {
                    add(ShortcutTarget.Series)
                    libraries.forEach { library -> add(ShortcutTarget.ForLibrary(library)) }
                }
            targets.mapIndexed { index, target ->
                val (start, end) =
                    LIBRARY_SHORTCUT_GRADIENTS[index % LIBRARY_SHORTCUT_GRADIENTS.size]
                when (target) {
                    is ShortcutTarget.Series ->
                        LibraryShortcut(
                            key = "series",
                            target = target,
                            label = seriesLabel,
                            iconRes = R.drawable.ic_collection,
                            gradientStart = start,
                            gradientEnd = end,
                        )
                    is ShortcutTarget.ForLibrary -> {
                        val iconRes =
                            when (target.library.mediaType.lowercase()) {
                                "podcast" -> R.drawable.ic_apple_podcast
                                "book" -> R.drawable.ic_book_audio
                                else -> R.drawable.ic_book
                            }
                        LibraryShortcut(
                            key = "library:${target.library.id}",
                            target = target,
                            label = target.library.name,
                            iconRes = iconRes,
                            gradientStart = start,
                            gradientEnd = end,
                        )
                    }
                }
            }
        }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.abs_action_browse),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        )
        LazyRow(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shortcuts, key = { it.key }) { shortcut ->
                LibraryShortcutCard(
                    label = shortcut.label,
                    iconRes = shortcut.iconRes,
                    gradientStart = shortcut.gradientStart,
                    gradientEnd = shortcut.gradientEnd,
                    cardWidth = cardWidth,
                    onClick = {
                        when (val target = shortcut.target) {
                            is ShortcutTarget.Series -> onBrowseSeries()
                            is ShortcutTarget.ForLibrary -> onBrowseLibrary(target.library)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryShortcutCard(
    label: String,
    iconRes: Int,
    gradientStart: Color,
    gradientEnd: Color,
    cardWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(cardWidth).aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        onClick = onClick,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(Brush.linearGradient(colors = listOf(gradientStart, gradientEnd)))
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier =
                    Modifier.fillMaxHeight(0.85f)
                        .aspectRatio(1f)
                        .align(Alignment.BottomEnd)
                        .offset(x = 20.dp, y = 20.dp)
                        .rotate(-15f),
            )

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = label,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                        ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
