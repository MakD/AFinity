package com.makd.afinity.ui.audiobookshelf.item

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.audiobookshelf.item.components.ChapterList
import com.makd.afinity.ui.audiobookshelf.item.components.EpisodeList
import com.makd.afinity.ui.audiobookshelf.item.components.ExpandableSynopsis
import com.makd.afinity.ui.audiobookshelf.item.components.IncludedInSeriesSection
import com.makd.afinity.ui.audiobookshelf.item.components.ItemHeader
import com.makd.afinity.ui.audiobookshelf.item.components.ItemHeaderContent
import com.makd.afinity.ui.audiobookshelf.item.components.ItemHeroBackground

@Composable
fun AudiobookshelfItemScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String, String?, Double?) -> Unit,
    onNavigateToSeries: (seriesId: String, libraryId: String, seriesName: String) -> Unit =
        { _, _, _ ->
        },
    viewModel: AudiobookshelfItemViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item by viewModel.item.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()

    val isPodcast = item?.mediaType?.lowercase() == "podcast"
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var chaptersExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            item != null -> {
                if (isLandscape) {
                    val coverUrl =
                        if (config?.serverUrl != null && item?.media?.coverPath != null) {
                            "${config?.serverUrl}/api/items/${item?.id}/cover"
                        } else null

                    ItemHeroBackground(coverUrl = coverUrl)

                    Row(
                        modifier =
                            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
                    ) {
                        Column(
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 24.dp)
                        ) {
                            ItemHeaderContent(
                                item = item!!,
                                progress = progress,
                                coverUrl = coverUrl,
                                onPlay = { onNavigateToPlayer(viewModel.itemId, null, null) },
                            )
                        }

                        LazyColumn(
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    ),
                            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                        ) {
                            item { Spacer(modifier = Modifier.statusBarsPadding()) }

                            if (uiState.seriesDetails.isNotEmpty()) {
                                item {
                                    IncludedInSeriesSection(
                                        seriesList = uiState.seriesDetails,
                                        serverUrl = config?.serverUrl,
                                        onSeriesClick = { seriesId, seriesName ->
                                            onNavigateToSeries(
                                                seriesId,
                                                item?.libraryId ?: "",
                                                seriesName,
                                            )
                                        },
                                        modifier = Modifier.padding(top = 16.dp),
                                    )
                                }
                            }

                            item?.media?.metadata?.description?.let { description ->
                                item {
                                    ExpandableSynopsis(
                                        description = description,
                                        modifier =
                                            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                    )
                                }
                            }

                            item?.media?.metadata?.narratorName?.let { narrator ->
                                item {
                                    NarratedByRow(
                                        narrator = narrator,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }

                            val showEpisodes = isPodcast && uiState.episodes.isNotEmpty()
                            val showChapters = !isPodcast && uiState.chapters.isNotEmpty()

                            if (showEpisodes || showChapters) {
                                item {
                                    CollapsibleSectionHeader(
                                        title = if (showEpisodes) "EPISODES" else "CHAPTERS",
                                        expanded = chaptersExpanded,
                                        onToggle = { chaptersExpanded = !chaptersExpanded },
                                        modifier = Modifier.padding(top = 16.dp),
                                    )
                                }

                                if (chaptersExpanded) {
                                    item {
                                        if (showEpisodes) {
                                            EpisodeList(
                                                episodes = uiState.episodes,
                                                onEpisodeClick = { /* Details */ },
                                                onEpisodePlay = { episode ->
                                                    onNavigateToPlayer(
                                                        viewModel.itemId,
                                                        episode.id,
                                                        null,
                                                    )
                                                },
                                                modifier =
                                                    Modifier.padding(top = 8.dp, bottom = 16.dp),
                                            )
                                        } else {
                                            ChapterList(
                                                chapters = uiState.chapters,
                                                currentPosition = progress?.currentTime,
                                                onChapterClick = { chapter ->
                                                    onNavigateToPlayer(
                                                        viewModel.itemId,
                                                        null,
                                                        chapter.start,
                                                    )
                                                },
                                                modifier = Modifier.padding(bottom = 16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                    ) {
                        item {
                            ItemHeader(
                                item = item!!,
                                progress = progress,
                                serverUrl = config?.serverUrl,
                                onPlay = { onNavigateToPlayer(viewModel.itemId, null, null) },
                            )
                        }

                        if (uiState.seriesDetails.isNotEmpty()) {
                            item {
                                IncludedInSeriesSection(
                                    seriesList = uiState.seriesDetails,
                                    serverUrl = config?.serverUrl,
                                    onSeriesClick = { seriesId, seriesName ->
                                        onNavigateToSeries(
                                            seriesId,
                                            item?.libraryId ?: "",
                                            seriesName,
                                        )
                                    },
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }

                        item?.media?.metadata?.description?.let { description ->
                            item {
                                ExpandableSynopsis(
                                    description = description,
                                    modifier =
                                        Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                )
                            }
                        }

                        item?.media?.metadata?.narratorName?.let { narrator ->
                            item {
                                NarratedByRow(
                                    narrator = narrator,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }

                        val showEpisodes = isPodcast && uiState.episodes.isNotEmpty()
                        val showChapters = !isPodcast && uiState.chapters.isNotEmpty()

                        if (showEpisodes || showChapters) {
                            item {
                                CollapsibleSectionHeader(
                                    title = if (showEpisodes) "EPISODES" else "CHAPTERS",
                                    expanded = chaptersExpanded,
                                    onToggle = { chaptersExpanded = !chaptersExpanded },
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }

                            if (chaptersExpanded) {
                                item {
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        if (showEpisodes) {
                                            EpisodeList(
                                                episodes = uiState.episodes,
                                                onEpisodeClick = { /* Details */ },
                                                onEpisodePlay = { episode ->
                                                    onNavigateToPlayer(
                                                        viewModel.itemId,
                                                        episode.id,
                                                        null,
                                                    )
                                                },
                                                modifier =
                                                    Modifier.padding(top = 8.dp, bottom = 16.dp),
                                            )
                                        } else {
                                            ChapterList(
                                                chapters = uiState.chapters,
                                                currentPosition = progress?.currentTime,
                                                onChapterClick = { chapter ->
                                                    onNavigateToPlayer(
                                                        viewModel.itemId,
                                                        null,
                                                        chapter.start,
                                                    )
                                                },
                                                modifier = Modifier.padding(bottom = 16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                        }
                    }
                }
            }

            uiState.error != null -> {
                Text(
                    text = "Failed to load item",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                ),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_left),
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        AnimatedVisibility(
            visible = uiState.error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Card(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(16.dp)
                        .padding(WindowInsets.navigationBars.asPaddingValues()),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
            ) {
                Text(
                    text = uiState.error ?: "",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onToggle() },
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Icon(
            painter =
                painterResource(
                    id =
                        if (expanded) R.drawable.ic_keyboard_arrow_up
                        else R.drawable.ic_keyboard_arrow_down
                ),
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun NarratedByRow(narrator: String, modifier: Modifier = Modifier) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(
                    style =
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                        )
                ) {
                    append("Narrated by: ")
                }

                withStyle(
                    style =
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                ) {
                    append(narrator)
                }
            },
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}
