package com.makd.afinity.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.rememberAfinitySheetState

private const val SNIPPET_CONTEXT = 40
internal const val MAX_SEARCH_HITS = 500

data class ReaderSearchHit(
    val group: String?,
    val before: String,
    val match: String,
    val after: String,
    val page: Int? = null,
    val locatorJson: String? = null,
)

data class ReaderSearchState(
    val query: String = "",
    val hits: List<ReaderSearchHit> = emptyList(),
    val searching: Boolean = false,
    val selected: Int? = null,
) {
    val barVisible: Boolean
        get() = selected != null && hits.isNotEmpty()
}

private val Whitespace = Regex("\\s+")

internal fun normalizeSearchText(text: String): String = text.replace(Whitespace, " ").trim()

internal fun findHits(text: String, query: String, page: Int): List<ReaderSearchHit> {
    if (query.isEmpty() || text.isEmpty()) return emptyList()
    val hits = mutableListOf<ReaderSearchHit>()
    var from = 0
    while (hits.size < MAX_SEARCH_HITS) {
        val start = text.indexOf(query, from, ignoreCase = true)
        if (start < 0) break
        val end = start + query.length
        hits +=
            ReaderSearchHit(
                group = null,
                before = text.substring((start - SNIPPET_CONTEXT).coerceAtLeast(0), start),
                match = text.substring(start, end),
                after = text.substring(end, (end + SNIPPET_CONTEXT).coerceAtMost(text.length)),
                page = page,
            )
        from = end
    }
    return hits
}

@Composable
internal fun ReaderSearchSheet(
    state: ReaderSearchState,
    onSearch: (String) -> Unit,
    onHitClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf(state.query) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (state.query.isEmpty()) focusRequester.requestFocus() }
    AfinityModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberAfinitySheetState(skipPartiallyExpanded = true),
    ) {
        AfinityTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.reader_search_hint),
            leadingIcon = painterResource(R.drawable.ic_search),
            trailingIcon =
                if (query.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                query = ""
                                onSearch("")
                                focusRequester.requestFocus()
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.reader_search_clear),
                            )
                        }
                    }
                } else {
                    null
                },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSearch(query)
                    }
                ),
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focusRequester),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.searching) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            val status =
                when {
                    state.query.isEmpty() -> null
                    state.hits.isNotEmpty() ->
                        pluralStringResource(
                            R.plurals.reader_search_results,
                            state.hits.size,
                            state.hits.size,
                        )
                    state.searching -> stringResource(R.string.reader_search_searching)
                    else -> stringResource(R.string.reader_search_none)
                }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            itemsIndexed(state.hits) { index, hit ->
                val label = hitGroupLabel(hit)
                val previous = state.hits.getOrNull(index - 1)?.let { hitGroupLabel(it) }
                if (label != null && (index == 0 || label != previous)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 16.dp,
                                bottom = 4.dp,
                            ),
                    )
                }
                Text(
                    text =
                        buildAnnotatedString {
                            append("…")
                            append(normalizeSearchText(hit.before))
                            append(" ")
                            withStyle(
                                SpanStyle(fontWeight = FontWeight.Bold, color = colors.primary)
                            ) {
                                append(hit.match)
                            }
                            append(" ")
                            append(normalizeSearchText(hit.after))
                            append("…")
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable(role = Role.Button) { onHitClick(index) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun hitGroupLabel(hit: ReaderSearchHit): String? =
    hit.group?.takeIf { it.isNotBlank() }
        ?: hit.page?.let { stringResource(R.string.reader_page_number_fmt, it + 1) }

@Composable
internal fun BoxScope.ReaderSearchResultBar(
    visible: Boolean,
    state: ReaderSearchState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Row(
            modifier =
                Modifier.windowInsetsPadding(ReaderBottomInsets)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.reader_search_previous),
                )
            }
            Column(
                modifier =
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onOpen)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.reader_search_position_fmt,
                            (state.selected ?: 0) + 1,
                            state.hits.size,
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.query,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = stringResource(R.string.reader_search_next),
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.reader_search_close),
                )
            }
        }
    }
}

@Composable
internal fun ReaderSearchButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = stringResource(R.string.reader_search),
        )
    }
}
