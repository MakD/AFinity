package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.ChannelSortOrder
import com.makd.afinity.data.models.livetv.GuideIndicator
import com.makd.afinity.data.models.livetv.LiveTvViewOptions
import com.makd.afinity.data.models.livetv.ProgramCategory
import com.makd.afinity.ui.components.AfinityToggleChip
import com.makd.afinity.ui.components.MinimalSwitchTile
import com.makd.afinity.ui.components.RadioOptionRow
import com.makd.afinity.ui.components.filter.FilterAccordionSection
import com.makd.afinity.ui.components.filter.FilterDialog
import com.makd.afinity.ui.livetv.models.LiveTvCategory

@Composable
fun LiveTvFilterDialog(
    options: LiveTvViewOptions,
    showGuideOptions: Boolean,
    onApply: (LiveTvViewOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember { mutableStateOf(options) }
    var expandedSections by remember { mutableStateOf(setOf(SECTION_SORT)) }
    fun toggleSection(key: String) {
        expandedSections =
            if (key in expandedSections) expandedSections - key else expandedSections + key
    }

    val sortLabels =
        listOf(
            ChannelSortOrder.NUMBER to stringResource(R.string.livetv_sort_channel_number),
            ChannelSortOrder.NAME to stringResource(R.string.livetv_sort_name),
            ChannelSortOrder.RECENTLY_WATCHED to
                stringResource(R.string.livetv_sort_recently_watched),
        )
    val categoryLabels =
        LiveTvCategory.entries.mapNotNull { category ->
            category.programCategory?.let { it to stringResource(category.displayNameRes) }
        }
    val indicatorLabels =
        listOf(
            GuideIndicator.LIVE to stringResource(R.string.livetv_indicator_live),
            GuideIndicator.NEW to stringResource(R.string.livetv_indicator_new),
            GuideIndicator.PREMIERE to stringResource(R.string.livetv_indicator_premiere),
            GuideIndicator.REPEAT to stringResource(R.string.livetv_indicator_repeat),
            GuideIndicator.HD to stringResource(R.string.livetv_indicator_hd),
        )
    val anyLabel = stringResource(R.string.discover_filter_any)
    val allLabel = stringResource(R.string.livetv_filter_all)
    val noneLabel = stringResource(R.string.livetv_filter_none)

    FilterDialog(
        onDismiss = {
            onApply(working)
            onDismiss()
        },
        onClearAll = { working = working.cleared(showGuideOptions) },
        onApply = {
            onApply(working)
            onDismiss()
        },
        clearAllEnabled = working.activeCount(showGuideOptions) > 0,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        FilterAccordionSection(
            title = stringResource(R.string.livetv_filter_sort_channels),
            summary = sortLabels.first { it.first == working.channelSort }.second,
            expanded = SECTION_SORT in expandedSections,
            onToggle = { toggleSection(SECTION_SORT) },
        ) {
            Column {
                sortLabels.forEach { (order, label) ->
                    RadioOptionRow(
                        label = label,
                        selected = working.channelSort == order,
                        onClick = { working = working.copy(channelSort = order) },
                    )
                }
            }
        }

        FilterAccordionSection(
            title = stringResource(R.string.filter_favorites),
            summary =
                when {
                    working.favoritesOnly -> stringResource(R.string.livetv_favorites_only)
                    working.favoritesFirst -> stringResource(R.string.livetv_favorites_first)
                    else -> anyLabel
                },
            expanded = SECTION_FAVORITES in expandedSections,
            onToggle = { toggleSection(SECTION_FAVORITES) },
        ) {
            Column {
                MinimalSwitchTile(
                    title = stringResource(R.string.livetv_favorites_first_long),
                    checked = working.favoritesFirst,
                    onCheckedChange = { working = working.copy(favoritesFirst = it) },
                )
                MinimalSwitchTile(
                    title = stringResource(R.string.livetv_favorites_only),
                    checked = working.favoritesOnly,
                    onCheckedChange = { working = working.copy(favoritesOnly = it) },
                )
            }
        }

        if (showGuideOptions) {
            val selectedCategories = working.guideCategories
            FilterAccordionSection(
                title = stringResource(R.string.livetv_filter_categories),
                summary =
                    if (selectedCategories.containsAll(ProgramCategory.entries)) allLabel
                    else {
                        categoryLabels
                            .filter { it.first in selectedCategories }
                            .joinToString(", ") { it.second }
                            .ifEmpty { allLabel }
                    },
                expanded = SECTION_CATEGORIES in expandedSections,
                onToggle = { toggleSection(SECTION_CATEGORIES) },
            ) {
                Column {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categoryLabels.forEach { (category, label) ->
                            AfinityToggleChip(
                                label = label,
                                selected = category in selectedCategories,
                                onSelectedChange = { selected ->
                                    working =
                                        working.copy(
                                            guideCategories =
                                                if (selected) selectedCategories + category
                                                else selectedCategories - category
                                        )
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MinimalSwitchTile(
                        title = stringResource(R.string.livetv_color_coded_backgrounds),
                        checked = working.colorCodedBackgrounds,
                        onCheckedChange = { working = working.copy(colorCodedBackgrounds = it) },
                    )
                }
            }

            FilterAccordionSection(
                title = stringResource(R.string.livetv_filter_indicators),
                summary =
                    indicatorLabels
                        .filter { it.first in working.indicators }
                        .joinToString(", ") { it.second }
                        .ifEmpty { noneLabel },
                expanded = SECTION_INDICATORS in expandedSections,
                onToggle = { toggleSection(SECTION_INDICATORS) },
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    indicatorLabels.forEach { (indicator, label) ->
                        AfinityToggleChip(
                            label = label,
                            selected = indicator in working.indicators,
                            onSelectedChange = { selected ->
                                working =
                                    working.copy(
                                        indicators =
                                            if (selected) working.indicators + indicator
                                            else working.indicators - indicator
                                    )
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val SECTION_SORT = "sort"
private const val SECTION_FAVORITES = "favorites"
private const val SECTION_CATEGORIES = "categories"
private const val SECTION_INDICATORS = "indicators"
