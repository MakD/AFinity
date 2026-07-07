package com.makd.afinity.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.DiscoverFilterOptions
import com.makd.afinity.data.models.jellyseerr.Genre
import com.makd.afinity.data.models.jellyseerr.TmdbKeyword
import com.makd.afinity.data.models.jellyseerr.TvStatus
import com.makd.afinity.data.models.jellyseerr.WatchProviderDetails
import com.makd.afinity.data.models.jellyseerr.WatchProviderRegion
import com.makd.afinity.ui.components.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
private fun tvStatusLabel(status: TvStatus): String =
    when (status) {
        TvStatus.RETURNING_SERIES -> stringResource(R.string.tv_status_returning_series)
        TvStatus.PLANNED -> stringResource(R.string.tv_status_planned)
        TvStatus.IN_PRODUCTION -> stringResource(R.string.tv_status_in_production)
        TvStatus.ENDED -> stringResource(R.string.tv_status_ended)
        TvStatus.CANCELLED -> stringResource(R.string.tv_status_cancelled)
        TvStatus.PILOT -> stringResource(R.string.tv_status_pilot)
    }

private enum class KeywordField {
    INCLUDE,
    EXCLUDE,
}

private val US_MOVIE_CERTIFICATIONS = listOf("NR", "G", "PG", "PG-13", "R", "NC-17")
private val US_TV_CERTIFICATIONS = listOf("NR", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14", "TV-MA")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverFilterBottomSheet(
    isTv: Boolean,
    showGenrePicker: Boolean,
    genres: List<Genre>,
    filterOptions: DiscoverFilterOptions,
    watchProviderRegions: List<WatchProviderRegion>,
    watchProviders: List<WatchProviderDetails>,
    onRegionSelected: (String) -> Unit,
    initialSelectedKeywords: List<TmdbKeyword>,
    initialSelectedExcludeKeywords: List<TmdbKeyword>,
    keywordSearchResults: List<TmdbKeyword>,
    onKeywordQueryChange: (String) -> Unit,
    onApply: (DiscoverFilterOptions, List<TmdbKeyword>, List<TmdbKeyword>) -> Unit,
    onDismiss: () -> Unit,
) {
    var releaseDateGte by remember { mutableStateOf(filterOptions.releaseDateGte) }
    var releaseDateLte by remember { mutableStateOf(filterOptions.releaseDateLte) }
    var runtimeRange by remember {
        mutableStateOf(
            (filterOptions.runtimeGte?.toFloat() ?: 0f)..(filterOptions.runtimeLte?.toFloat()
                    ?: 400f)
        )
    }
    var ratingRange by remember {
        mutableStateOf(
            (filterOptions.voteAverageGte?.toFloat() ?: 0f)..(filterOptions.voteAverageLte
                    ?.toFloat() ?: 10f)
        )
    }
    var voteCountRange by remember {
        mutableStateOf(
            (filterOptions.voteCountGte?.toFloat() ?: 0f)..(filterOptions.voteCountLte?.toFloat()
                    ?: 1000f)
        )
    }
    var tvStatus by remember { mutableStateOf(filterOptions.tvStatus.toSet()) }
    var selectedCertifications by remember { mutableStateOf(filterOptions.certification.toSet()) }
    var selectedGenreIds by remember { mutableStateOf(filterOptions.genreIds.toSet()) }
    var genreQuery by remember { mutableStateOf("") }

    var watchRegion by remember { mutableStateOf(filterOptions.watchRegion) }
    var selectedProviderIds by remember { mutableStateOf(filterOptions.watchProviderIds.toSet()) }
    var selectedKeywords by remember { mutableStateOf(initialSelectedKeywords) }
    var selectedExcludeKeywords by remember { mutableStateOf(initialSelectedExcludeKeywords) }
    var keywordQuery by remember { mutableStateOf("") }
    var excludeKeywordQuery by remember { mutableStateOf("") }
    var activeKeywordField by remember { mutableStateOf<KeywordField?>(null) }

    var showRegionPicker by remember { mutableStateOf(false) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { watchRegion?.let { onRegionSelected(it) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.discover_filter_title),
                        style =
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    TextButton(
                        onClick = {
                            releaseDateGte = null
                            releaseDateLte = null
                            runtimeRange = 0f..400f
                            ratingRange = 0f..10f
                            voteCountRange = 0f..1000f
                            tvStatus = emptySet()
                            selectedCertifications = emptySet()
                            selectedGenreIds = emptySet()
                            watchRegion = null
                            selectedProviderIds = emptySet()
                            selectedKeywords = emptyList()
                            selectedExcludeKeywords = emptyList()
                        }
                    ) {
                        Text(stringResource(R.string.discover_filter_clear_all))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (showGenrePicker && genres.isNotEmpty()) {
                    val selectedGenres = genres.filter { selectedGenreIds.contains(it.id) }
                    val genreSuggestions = genres.filter {
                        !selectedGenreIds.contains(it.id) &&
                            (genreQuery.isBlank() ||
                                it.name.contains(genreQuery, ignoreCase = true))
                    }

                    SearchableChipMultiSelect(
                        label = stringResource(R.string.discover_filter_genres),
                        placeholder = stringResource(R.string.discover_filter_genres_hint),
                        query = genreQuery,
                        onQueryChange = { genreQuery = it },
                        suggestions = genreSuggestions,
                        suggestionLabel = { it.name },
                        onSuggestionSelected = { genre ->
                            selectedGenreIds = selectedGenreIds + genre.id
                            genreQuery = ""
                        },
                        selected = selectedGenres,
                        selectedLabel = { it.name },
                        onRemoveSelected = { genre ->
                            selectedGenreIds = selectedGenreIds - genre.id
                        },
                    )

                    SectionDivider()
                }

                FilterSectionHeader(stringResource(R.string.discover_filter_release_date))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DateFieldButton(
                        label = stringResource(R.string.discover_filter_release_date_from),
                        value = releaseDateGte,
                        onClick = { showFromDatePicker = true },
                        modifier = Modifier.weight(1f),
                    )
                    DateFieldButton(
                        label = stringResource(R.string.discover_filter_release_date_to),
                        value = releaseDateLte,
                        onClick = { showToDatePicker = true },
                        modifier = Modifier.weight(1f),
                    )
                }

                SectionDivider()

                FilterSectionHeader(
                    "${stringResource(R.string.discover_filter_runtime)}  ${runtimeRange.start.roundToInt()}–${runtimeRange.endInclusive.roundToInt()} min"
                )
                RangeSlider(
                    value = runtimeRange,
                    onValueChange = { runtimeRange = it },
                    valueRange = 0f..400f,
                    colors = discoverSliderColors(),
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                )

                SectionDivider()

                FilterSectionHeader(
                    "${stringResource(R.string.discover_filter_rating)}  ${"%.1f".format(ratingRange.start)}–${"%.1f".format(ratingRange.endInclusive)}"
                )
                RangeSlider(
                    value = ratingRange,
                    onValueChange = { ratingRange = it },
                    valueRange = 0f..10f,
                    colors = discoverSliderColors(),
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                )

                SectionDivider()

                FilterSectionHeader(
                    "${stringResource(R.string.discover_filter_vote_count)}  ${voteCountRange.start.roundToInt()}–${voteCountRange.endInclusive.roundToInt()}"
                )
                RangeSlider(
                    value = voteCountRange,
                    onValueChange = { voteCountRange = it },
                    valueRange = 0f..1000f,
                    colors = discoverSliderColors(),
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                )

                if (isTv) {
                    SectionDivider()
                    FilterSectionHeader(stringResource(R.string.discover_filter_tv_status))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TvStatus.entries.forEach { status ->
                            FilterChip(
                                selected = tvStatus.contains(status.value),
                                onClick = {
                                    tvStatus =
                                        if (tvStatus.contains(status.value)) tvStatus - status.value
                                        else tvStatus + status.value
                                },
                                label = { Text(tvStatusLabel(status)) },
                            )
                        }
                    }
                }

                SectionDivider()

                FilterSectionHeader(stringResource(R.string.discover_filter_certification))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val certificationOptions =
                        if (isTv) US_TV_CERTIFICATIONS else US_MOVIE_CERTIFICATIONS
                    certificationOptions.forEach { cert ->
                        FilterChip(
                            selected = selectedCertifications.contains(cert),
                            onClick = {
                                selectedCertifications =
                                    if (selectedCertifications.contains(cert))
                                        selectedCertifications - cert
                                    else selectedCertifications + cert
                            },
                            label = { Text(cert) },
                        )
                    }
                }

                SectionDivider()

                FilterSectionHeader(stringResource(R.string.discover_filter_watch_providers))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showRegionPicker = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.discover_filter_watch_region),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text =
                            watchProviderRegions.find { it.isoCode == watchRegion }?.englishName
                                ?: stringResource(R.string.discover_filter_any),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (watchProviders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        watchProviders.forEach { provider ->
                            WatchProviderTile(
                                provider = provider,
                                selected = selectedProviderIds.contains(provider.id),
                                onClick = {
                                    selectedProviderIds =
                                        if (selectedProviderIds.contains(provider.id))
                                            selectedProviderIds - provider.id
                                        else selectedProviderIds + provider.id
                                },
                            )
                        }
                    }
                }

                SectionDivider()

                SearchableChipMultiSelect(
                    label = stringResource(R.string.discover_filter_keywords),
                    placeholder = stringResource(R.string.discover_filter_keyword_search_hint),
                    query = keywordQuery,
                    onQueryChange = {
                        keywordQuery = it
                        activeKeywordField = KeywordField.INCLUDE
                        onKeywordQueryChange(it)
                    },
                    suggestions =
                        if (activeKeywordField == KeywordField.INCLUDE) keywordSearchResults
                        else emptyList(),
                    suggestionLabel = { it.name },
                    onSuggestionSelected = { keyword ->
                        selectedKeywords = selectedKeywords + keyword
                        keywordQuery = ""
                        onKeywordQueryChange("")
                    },
                    selected = selectedKeywords,
                    selectedLabel = { it.name },
                    onRemoveSelected = { keyword -> selectedKeywords = selectedKeywords - keyword },
                )

                Spacer(modifier = Modifier.height(16.dp))

                SearchableChipMultiSelect(
                    label = stringResource(R.string.discover_filter_exclude_keywords),
                    placeholder = stringResource(R.string.discover_filter_keyword_search_hint),
                    query = excludeKeywordQuery,
                    onQueryChange = {
                        excludeKeywordQuery = it
                        activeKeywordField = KeywordField.EXCLUDE
                        onKeywordQueryChange(it)
                    },
                    suggestions =
                        if (activeKeywordField == KeywordField.EXCLUDE) keywordSearchResults
                        else emptyList(),
                    suggestionLabel = { it.name },
                    onSuggestionSelected = { keyword ->
                        selectedExcludeKeywords = selectedExcludeKeywords + keyword
                        excludeKeywordQuery = ""
                        onKeywordQueryChange("")
                    },
                    selected = selectedExcludeKeywords,
                    selectedLabel = { it.name },
                    onRemoveSelected = { keyword ->
                        selectedExcludeKeywords = selectedExcludeKeywords - keyword
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = {
                        onApply(
                            DiscoverFilterOptions(
                                genreIds =
                                    if (showGenrePicker) selectedGenreIds.toList()
                                    else filterOptions.genreIds,
                                releaseDateGte = releaseDateGte,
                                releaseDateLte = releaseDateLte,
                                runtimeGte = runtimeRange.start.roundToInt().takeIf { it > 0 },
                                runtimeLte =
                                    runtimeRange.endInclusive.roundToInt().takeIf { it < 400 },
                                voteAverageGte = ratingRange.start.toDouble().takeIf { it > 0.0 },
                                voteAverageLte =
                                    ratingRange.endInclusive.toDouble().takeIf { it < 10.0 },
                                voteCountGte = voteCountRange.start.roundToInt().takeIf { it > 0 },
                                voteCountLte =
                                    voteCountRange.endInclusive.roundToInt().takeIf { it < 1000 },
                                tvStatus = tvStatus.toList(),
                                certification = selectedCertifications.toList(),
                                watchProviderIds = selectedProviderIds.toList(),
                                watchRegion = watchRegion,
                                keywordIds = selectedKeywords.map { it.id },
                                excludeKeywordIds = selectedExcludeKeywords.map { it.id },
                            ),
                            selectedKeywords,
                            selectedExcludeKeywords,
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }

    if (showRegionPicker) {
        RegionPickerDialog(
            regions = watchProviderRegions,
            selected = watchRegion,
            onSelect = {
                watchRegion = it
                selectedProviderIds = emptySet()
                showRegionPicker = false
                if (it != null) onRegionSelected(it)
            },
            onDismiss = { showRegionPicker = false },
        )
    }

    if (showFromDatePicker) {
        DateFieldPickerDialog(
            initialDate = releaseDateGte,
            onConfirm = {
                releaseDateGte = it
                showFromDatePicker = false
            },
            onDismiss = { showFromDatePicker = false },
        )
    }

    if (showToDatePicker) {
        DateFieldPickerDialog(
            initialDate = releaseDateLte,
            onConfirm = {
                releaseDateLte = it
                showToDatePicker = false
            },
            onDismiss = { showToDatePicker = false },
        )
    }
}

@Composable
private fun discoverSliderColors(): SliderColors =
    SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
        inactiveTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
    )

@Composable
private fun FilterSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchProviderTile(
    provider: WatchProviderDetails,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(provider.name) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier =
                Modifier.size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 2.dp,
                        color =
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable(onClick = onClick)
        ) {
            provider.logoUrl()?.let { url ->
                AsyncImage(
                    imageUrl = url,
                    contentDescription = null,
                    targetWidth = 64.dp,
                    targetHeight = 64.dp,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }

            if (selected) {
                Box(
                    modifier =
                        Modifier.align(Alignment.TopStart)
                            .offset(x = (-6).dp, y = (-6).dp)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateFieldButton(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.shapes.medium,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value ?: stringResource(R.string.discover_filter_any),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFieldPickerDialog(
    initialDate: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = initialDate?.let {
        runCatching {
            LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
            .getOrNull()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onConfirm(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    } else {
                        onConfirm(null)
                    }
                }
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun CountryRow(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RegionPickerDialog(
    regions: List<WatchProviderRegion>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discover_filter_select_region)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    CountryRow(
                        name = stringResource(R.string.discover_filter_any),
                        isSelected = selected == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(regions, key = { it.isoCode }) { region ->
                    CountryRow(
                        name = region.englishName,
                        isSelected = selected == region.isoCode,
                        onClick = { onSelect(region.isoCode) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun <T> SearchableChipMultiSelect(
    label: String,
    placeholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<T>,
    suggestionLabel: (T) -> String,
    onSuggestionSelected: (T) -> Unit,
    selected: List<T>,
    selectedLabel: (T) -> String,
    onRemoveSelected: (T) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    FilterSectionHeader(label)

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        trailingIcon =
            if (isFocused) {
                {
                    IconButton(onClick = { focusManager.clearFocus() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_clear),
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                }
            } else null,
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
    )

    if (isFocused && suggestions.isNotEmpty()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Column(
                modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
            ) {
                suggestions.take(20).forEach { suggestion ->
                    Text(
                        text = suggestionLabel(suggestion),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onSuggestionSelected(suggestion)
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    if (selected.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selected.forEach { item ->
                FilterChip(
                    selected = true,
                    onClick = { onRemoveSelected(item) },
                    label = { Text(selectedLabel(item)) },
                )
            }
        }
    }
}
