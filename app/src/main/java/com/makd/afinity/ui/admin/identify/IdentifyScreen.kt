package com.makd.afinity.ui.admin.identify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.data.models.admin.IdentifyResult
import com.makd.afinity.data.models.admin.IdentifyTarget
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AfinitySwitch
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.EmptyState

private val CardShape = RoundedCornerShape(16.dp)

private fun providerIdLabel(key: String): String =
    when (key.lowercase()) {
        "imdb" -> "IMDb"
        "tmdb" -> "TMDB"
        "tvdb" -> "TVDB"
        else -> key
    }

private fun matchSubtitle(result: IdentifyResult): String =
    listOfNotNull(result.year?.toString(), result.searchProviderName).joinToString(" • ")

private fun fileName(path: String): String =
    path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyScreen(
    onNavigateUp: () -> Unit,
    onApplySuccess: () -> Unit = onNavigateUp,
    viewModel: IdentifyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val currentOnApplySuccess by rememberUpdatedState(onApplySuccess)

    var pendingResult by remember { mutableStateOf<IdentifyResult?>(null) }
    var idsExpanded by rememberSaveable { mutableStateOf(false) }

    val runSearch = {
        focusManager.clearFocus()
        viewModel.search()
    }

    LaunchedEffect(uiState.applied) {
        if (uiState.applied) {
            pendingResult = null
            currentOnApplySuccess()
        }
    }

    val message = uiState.message
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            pendingResult = null
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    pendingResult?.let { result ->
        MatchSheet(
            result = result,
            replaceAllImages = uiState.replaceAllImages,
            applying = uiState.applying,
            onToggleReplaceImages = viewModel::toggleReplaceImages,
            onConfirm = { viewModel.applyResult(result) },
            onDismiss = { if (!uiState.applying) pendingResult = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.admin_identify_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, snackbar = { AFinitySnackbar(it) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 16.dp + LocalPlayerOffset.current,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.target?.let { target -> item { TargetCard(target = target) } }

            item {
                SearchRow(
                    uiState = uiState,
                    onNameChange = viewModel::updateSearchName,
                    onYearChange = viewModel::updateYear,
                    onSearch = runSearch,
                )
            }

            if (uiState.providers.isNotEmpty()) {
                item {
                    ProviderIdsPanel(
                        providers = uiState.providers,
                        providerIds = uiState.providerIds,
                        expanded = idsExpanded,
                        onToggle = { idsExpanded = !idsExpanded },
                        onIdChange = viewModel::updateProviderId,
                        onSearch = runSearch,
                    )
                }
            }

            if (uiState.results.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.admin_identify_results),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = uiState.results.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                items(uiState.results) { result ->
                    MatchCard(
                        result = result,
                        enabled = !uiState.applying,
                        onClick = {
                            focusManager.clearFocus()
                            pendingResult = result
                        },
                    )
                }
            } else if (uiState.searchFailed) {
                item {
                    SearchStateMessage(
                        icon = painterResource(R.drawable.ic_cloud_off),
                        title = stringResource(R.string.admin_identify_search_failed_title),
                        message = stringResource(R.string.admin_identify_search_failed_message),
                        actionText = stringResource(R.string.action_retry),
                        onAction = viewModel::search,
                    )
                }
            } else if (uiState.hasSearched && !uiState.searching) {
                item {
                    val hasYear = uiState.showYear && uiState.year.isNotBlank()
                    SearchStateMessage(
                        icon = painterResource(R.drawable.ic_search),
                        title = stringResource(R.string.admin_identify_no_results),
                        message = stringResource(R.string.admin_identify_no_results_hint),
                        actionText =
                            if (hasYear) {
                                stringResource(R.string.admin_identify_search_without_year)
                            } else null,
                        onAction = if (hasYear) viewModel::searchWithoutYear else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetCard(target: IdentifyTarget) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(url = target.imageUrl, corner = 8.dp, modifier = Modifier.width(44.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.admin_identify_current_match),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = listOfNotNull(target.name, target.year?.toString()).joinToString(" • "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            target.path
                ?.takeIf { it.isNotBlank() }
                ?.let { path ->
                    Text(
                        text = fileName(path),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
        }
    }
}

@Composable
private fun SearchRow(
    uiState: IdentifyUiState,
    onNameChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val searchActions = KeyboardActions(onSearch = { onSearch() })
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AfinityTextField(
            value = uiState.searchName,
            onValueChange = onNameChange,
            label = stringResource(R.string.admin_identify_field_name),
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = searchActions,
        )
        if (uiState.showYear) {
            AfinityTextField(
                value = uiState.year,
                onValueChange = onYearChange,
                label = stringResource(R.string.admin_identify_field_year),
                modifier = Modifier.width(88.dp),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search,
                    ),
                keyboardActions = searchActions,
            )
        }
        Button(
            onClick = onSearch,
            enabled = uiState.canSearch && !uiState.applying,
            shape = CardShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(56.dp),
        ) {
            if (uiState.searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.admin_identify_search),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderIdsPanel(
    providers: List<ExternalIdProvider>,
    providerIds: Map<String, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onIdChange: (String, String) -> Unit,
    onSearch: () -> Unit,
) {
    val summary = remember(providers) { providers.map { it.name }.distinct().joinToString(", ") }
    val searchActions = KeyboardActions(onSearch = { onSearch() })

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .heightIn(min = 48.dp)
                    .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.admin_identify_provider_ids),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (expanded) "" else summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(
                    if (expanded) R.drawable.ic_keyboard_arrow_up
                    else R.drawable.ic_keyboard_arrow_down
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.admin_identify_provider_ids_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                providers.chunked(2).forEach { rowProviders ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowProviders.forEach { provider ->
                            AfinityTextField(
                                value = providerIds[provider.key] ?: "",
                                onValueChange = { onIdChange(provider.key, it) },
                                label = provider.name,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = searchActions,
                            )
                        }
                        if (rowProviders.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchCard(result: IdentifyResult, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Poster(url = result.imageUrl, corner = 12.dp, modifier = Modifier.width(60.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = matchSubtitle(result),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProviderIdPills(
                providerIds = result.providerIds,
                pillColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            if (!result.overview.isNullOrBlank()) {
                Text(
                    text = result.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderIdPills(providerIds: Map<String, String>, pillColor: Color) {
    if (providerIds.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        providerIds.entries.take(4).forEach { (key, value) ->
            Text(
                text = "${providerIdLabel(key)} $value",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier =
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(pillColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Poster(url: String?, corner: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_movie),
                contentDescription = stringResource(R.string.cd_admin_no_image),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SearchStateMessage(
    icon: Painter,
    title: String,
    message: String,
    actionText: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = icon,
            title = title,
            message = message,
            badgeSize = 72.dp,
            iconSize = 32.dp,
        )
        if (actionText != null && onAction != null) {
            Button(
                onClick = onAction,
                colors =
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(text = actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchSheet(
    result: IdentifyResult,
    replaceAllImages: Boolean,
    applying: Boolean,
    onToggleReplaceImages: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentApplying by rememberUpdatedState(applying)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { !currentApplying },
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Poster(url = result.imageUrl, corner = 12.dp, modifier = Modifier.width(100.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = result.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = matchSubtitle(result),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProviderIdPills(
                        providerIds = result.providerIds,
                        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    if (!result.overview.isNullOrBlank()) {
                        Text(
                            text = result.overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(CardShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = !applying, onClick = onToggleReplaceImages)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.admin_identify_replace_images),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.admin_identify_replace_images_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AfinitySwitch(
                    checked = replaceAllImages,
                    onCheckedChange = { onToggleReplaceImages() },
                    enabled = !applying,
                )
            }

            Text(
                text = stringResource(R.string.admin_identify_confirm_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onConfirm,
                enabled = !applying,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (applying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.admin_identify_applying))
                } else {
                    Text(stringResource(R.string.admin_identify_use_match))
                }
            }
        }
    }
}
