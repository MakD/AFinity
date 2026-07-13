package com.makd.afinity.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.makd.afinity.R
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.LibraryFeature
import com.makd.afinity.data.models.media.LibraryFilterOptions
import com.makd.afinity.data.models.media.LibraryFilters
import com.makd.afinity.data.models.media.SeriesStatusFilter
import com.makd.afinity.data.models.media.VideoTypeFilter
import com.makd.afinity.ui.components.filter.FilterAccordionSection
import com.makd.afinity.ui.components.filter.SearchableChipMultiSelect

data class LibraryFilterCapabilities(
    val genres: Boolean,
    val ratings: Boolean,
    val tags: Boolean,
    val years: Boolean,
    val videoType: Boolean,
    val seriesStatus: Boolean,
    val features: Boolean = true,
)

fun libraryFilterCapabilities(type: CollectionType?): LibraryFilterCapabilities =
    when (type) {
        CollectionType.Movies ->
            LibraryFilterCapabilities(
                genres = true,
                ratings = true,
                tags = true,
                years = true,
                videoType = true,
                seriesStatus = false,
            )
        CollectionType.TvShows ->
            LibraryFilterCapabilities(
                genres = true,
                ratings = true,
                tags = true,
                years = true,
                videoType = false,
                seriesStatus = true,
            )
        CollectionType.BoxSets ->
            LibraryFilterCapabilities(
                genres = true,
                ratings = false,
                tags = false,
                years = false,
                videoType = true,
                seriesStatus = false,
            )
        else ->
            LibraryFilterCapabilities(
                genres = true,
                ratings = true,
                tags = true,
                years = true,
                videoType = false,
                seriesStatus = false,
            )
    }

@Composable
fun LibraryFilterBottomSheet(
    filters: LibraryFilters,
    options: LibraryFilterOptions,
    capabilities: LibraryFilterCapabilities,
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember { mutableStateOf(filters) }
    var genreQuery by remember { mutableStateOf("") }
    var tagQuery by remember { mutableStateOf("") }

    var expandedSections by remember {
        mutableStateOf(
            buildSet {
                add("status")
                if (working.seriesStatuses.isNotEmpty()) add("series_status")
                if (working.features.isNotEmpty()) add("features")
                if (working.genres.isNotEmpty()) add("genres")
                if (working.officialRatings.isNotEmpty()) add("ratings")
                if (working.tags.isNotEmpty()) add("tags")
                if (working.videoTypes.isNotEmpty()) add("video_type")
                if (working.years.isNotEmpty()) add("years")
            }
        )
    }
    fun toggleSection(key: String) {
        expandedSections =
            if (expandedSections.contains(key)) expandedSections - key else expandedSections + key
    }

    val anyLabel = stringResource(R.string.discover_filter_any)

    Dialog(
        onDismissRequest = {
            onApply(working)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier.fillMaxWidth().widthIn(max = 480.dp).fillMaxHeight(0.9f).padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.discover_filter_title),
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                        )
                        TextButton(onClick = { working = LibraryFilters() }) {
                            Text(stringResource(R.string.discover_filter_clear_all))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val statusSummary =
                        buildList {
                                if (working.played) add(stringResource(R.string.filter_played))
                                if (working.unplayed) add(stringResource(R.string.filter_unplayed))
                                if (working.resumable) add(stringResource(R.string.filter_resumable))
                                if (working.favorites) add(stringResource(R.string.filter_favorites))
                                if (working.watchlist) add(stringResource(R.string.filter_watchlist))
                            }
                            .let { if (it.isEmpty()) anyLabel else it.joinToString(", ") }

                    FilterAccordionSection(
                        title = stringResource(R.string.library_filter_status),
                        summary = statusSummary,
                        expanded = expandedSections.contains("status"),
                        onToggle = { toggleSection("status") },
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ToggleChip(stringResource(R.string.filter_played), working.played) {
                                working = working.copy(played = it, unplayed = false)
                            }
                            ToggleChip(stringResource(R.string.filter_unplayed), working.unplayed) {
                                working = working.copy(unplayed = it, played = false)
                            }
                            ToggleChip(
                                stringResource(R.string.filter_resumable),
                                working.resumable,
                            ) {
                                working = working.copy(resumable = it)
                            }
                            ToggleChip(
                                stringResource(R.string.filter_favorites),
                                working.favorites,
                            ) {
                                working = working.copy(favorites = it)
                            }
                            ToggleChip(
                                stringResource(R.string.filter_watchlist),
                                working.watchlist,
                            ) {
                                working = working.copy(watchlist = it)
                            }
                        }
                    }

                    if (capabilities.seriesStatus) {
                        val seriesStatusLabels =
                            listOf(
                                SeriesStatusFilter.CONTINUING to
                                    stringResource(R.string.series_status_continuing),
                                SeriesStatusFilter.ENDED to
                                    stringResource(R.string.series_status_ended),
                                SeriesStatusFilter.UNRELEASED to
                                    stringResource(R.string.series_status_unreleased),
                            )
                        val seriesStatusSummary =
                            seriesStatusLabels
                                .filter { working.seriesStatuses.contains(it.first) }
                                .joinToString(", ") { it.second }
                                .ifEmpty { anyLabel }

                        FilterAccordionSection(
                            title = stringResource(R.string.library_filter_series_status),
                            summary = seriesStatusSummary,
                            expanded = expandedSections.contains("series_status"),
                            onToggle = { toggleSection("series_status") },
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                seriesStatusLabels.forEach { (status, label) ->
                                    ToggleChip(
                                        label,
                                        working.seriesStatuses.contains(status),
                                    ) { selected ->
                                        working =
                                            working.copy(
                                                seriesStatuses =
                                                    if (selected) working.seriesStatuses + status
                                                    else working.seriesStatuses - status
                                            )
                                    }
                                }
                            }
                        }
                    }

                    if (capabilities.features) {
                        val featureLabels =
                            listOf(
                                LibraryFeature.SUBTITLES to
                                    stringResource(R.string.feature_subtitles),
                                LibraryFeature.TRAILER to stringResource(R.string.feature_trailer),
                                LibraryFeature.SPECIAL_FEATURE to
                                    stringResource(R.string.feature_special),
                                LibraryFeature.THEME_SONG to
                                    stringResource(R.string.feature_theme_song),
                                LibraryFeature.THEME_VIDEO to
                                    stringResource(R.string.feature_theme_video),
                            )
                        val featuresSummary =
                            featureLabels
                                .filter { working.features.contains(it.first) }
                                .joinToString(", ") { it.second }
                                .ifEmpty { anyLabel }

                        FilterAccordionSection(
                            title = stringResource(R.string.library_filter_features),
                            summary = featuresSummary,
                            expanded = expandedSections.contains("features"),
                            onToggle = { toggleSection("features") },
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                featureLabels.forEach { (feature, label) ->
                                    ToggleChip(
                                        label,
                                        working.features.contains(feature),
                                    ) { selected ->
                                        working =
                                            working.copy(
                                                features =
                                                    if (selected) working.features + feature
                                                    else working.features - feature
                                            )
                                    }
                                }
                            }
                        }
                    }

                    if (capabilities.genres && options.genres.isNotEmpty()) {
                        val selectedGenres = working.genres.toList()
                        val genreSuggestions =
                            options.genres.filter {
                                !working.genres.contains(it) &&
                                    (genreQuery.isBlank() ||
                                        it.contains(genreQuery, ignoreCase = true))
                            }

                        FilterAccordionSection(
                            title = stringResource(R.string.library_filter_genres),
                            summary =
                                if (selectedGenres.isEmpty()) anyLabel
                                else selectedGenres.joinToString(", "),
                            expanded = expandedSections.contains("genres"),
                            onToggle = { toggleSection("genres") },
                        ) {
                            SearchableChipMultiSelect(
                                label = null,
                                placeholder = stringResource(R.string.library_filter_genres_hint),
                                query = genreQuery,
                                onQueryChange = { genreQuery = it },
                                suggestions = genreSuggestions,
                                suggestionLabel = { it },
                                onSuggestionSelected = { genre ->
                                    working = working.copy(genres = working.genres + genre)
                                    genreQuery = ""
                                },
                                selected = selectedGenres,
                                selectedLabel = { it },
                                onRemoveSelected = { genre ->
                                    working = working.copy(genres = working.genres - genre)
                                },
                            )
                        }
                    }

                    if (capabilities.ratings && options.officialRatings.isNotEmpty()) {
                        FilterAccordionSection(
                            title = stringResource(R.string.library_filter_ratings),
                            summary =
                                if (working.officialRatings.isEmpty()) anyLabel
                                else working.officialRatings.joinToString(", "),
                            expanded = expandedSections.contains("ratings"),
                            onToggle = { toggleSection("ratings") },
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                options.officialRatings.forEach { rating ->
                                    ToggleChip(
                                        rating,
                                        working.officialRatings.contains(rating),
                                    ) { selected ->
                                        working =
                                            working.copy(
                                                officialRatings =
                                                    if (selected) working.officialRatings + rating
                                                    else working.officialRatings - rating
                                            )
                                    }
                                }
                            }
                        }
                    }

                    if (capabilities.tags && options.tags.isNotEmpty()) {
                        val selectedTags = working.tags.toList()
                        val tagSuggestions =
                            options.tags.filter {
                                !working.tags.contains(it) &&
                                    (tagQuery.isBlank() ||
                                        it.contains(tagQuery, ignoreCase = true))
                            }

                        FilterAccordionSection(
                            title = stringResource(R.string.library_filter_tags),
                            summary =
                                if (selectedTags.isEmpty()) anyLabel
                                else selectedTags.joinToString(", "),
                            expanded = expandedSections.contains("tags"),
                            onToggle = { toggleSection("tags") },
                        ) {
                            SearchableChipMultiSelect(
                                label = null,
                                placeholder = stringResource(R.string.library_filter_tags_hint),
                                query = tagQuery,
                                onQueryChange = { tagQuery = it },
                                suggestions = tagSuggestions,
                                suggestionLabel = { it },
                                onSuggestionSelected = { tag ->
                                    working = working.copy(tags = working.tags + tag)
                                    tagQuery = ""
                                },
                                selected = selectedTags,
                                selectedLabel = { it },
                                onRemoveSelected = { tag ->
                                    working = working.copy(tags = working.tags - tag)
                                },
                            )
                        }
                    }

                    if (capabilities.videoType) {
                        val videoTypeLabels =
                            listOf(
                                VideoTypeFilter.BLU_RAY to
                                    stringResource(R.string.video_type_bluray),
                                VideoTypeFilter.DVD to stringResource(R.string.video_type_dvd),
                                VideoTypeFilter.HD to stringResource(R.string.video_type_hd),
                                VideoTypeFilter.UHD_4K to stringResource(R.string.video_type_4k),
                                VideoTypeFilter.SD to stringResource(R.string.video_type_sd),
                                VideoTypeFilter.THREE_D to stringResource(R.string.video_type_3d),
                            )
                        val videoTypeSummary =
                            videoTypeLabels
                                .filter { working.videoTypes.contains(it.first) }
                                .joinToString(", ") { it.second }
                                .ifEmpty { anyLabel }

                        FilterAccordionSection(
                            title = stringResource(R.string.library_filter_video_type),
                            summary = videoTypeSummary,
                            expanded = expandedSections.contains("video_type"),
                            onToggle = { toggleSection("video_type") },
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                videoTypeLabels.forEach { (type, label) ->
                                    ToggleChip(
                                        label,
                                        working.videoTypes.contains(type),
                                    ) { selected ->
                                        working =
                                            working.copy(
                                                videoTypes =
                                                    if (selected) working.videoTypes + type
                                                    else working.videoTypes - type
                                            )
                                    }
                                }
                            }
                        }
                    }

                    if (capabilities.years && options.years.isNotEmpty()) {
                        FilterAccordionSection(
                            title = stringResource(R.string.library_filter_years),
                            summary =
                                if (working.years.isEmpty()) anyLabel
                                else working.years.sortedDescending().joinToString(", "),
                            expanded = expandedSections.contains("years"),
                            onToggle = { toggleSection("years") },
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                options.years.forEach { year ->
                                    ToggleChip(
                                        year.toString(),
                                        working.years.contains(year),
                                    ) { selected ->
                                        working =
                                            working.copy(
                                                years =
                                                    if (selected) working.years + year
                                                    else working.years - year
                                            )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                TextButton(
                    onClick = {
                        onApply(working)
                        onDismiss()
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(!selected) },
        label = { Text(label) },
    )
}