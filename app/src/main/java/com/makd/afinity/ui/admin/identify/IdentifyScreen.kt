package com.makd.afinity.ui.admin.identify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.IdentifyResult

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

    var hasSearched by remember { mutableStateOf(false) }
    var applyingResult by remember { mutableStateOf<IdentifyResult?>(null) }

    LaunchedEffect(uiState.applied) {
        if (uiState.applied) {
            snackbarHostState.showSnackbar("Identity applied successfully")
            onApplySuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            applyingResult = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_identify_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SleekTextField(
                            value = uiState.searchName,
                            onValueChange = viewModel::updateSearchName,
                            label = stringResource(R.string.admin_identify_field_name),
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        SleekTextField(
                            value = uiState.year,
                            onValueChange = viewModel::updateYear,
                            label = stringResource(R.string.admin_identify_field_year),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                        )
                    }

                    if (uiState.providers.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.admin_identify_provider_ids),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        uiState.providers.forEach { provider ->
                            val idValue = uiState.providerIds[provider.key] ?: ""
                            SleekTextField(
                                value = idValue,
                                onValueChange = { viewModel.updateProviderId(provider.key, it) },
                                label = provider.name,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions =
                                    KeyboardActions(
                                        onDone = {
                                            focusManager.clearFocus()
                                            if (!uiState.searching) {
                                                hasSearched = true
                                                viewModel.search()
                                            }
                                        }
                                    ),
                            )
                        }
                    }

                    SwitchRowSimple(
                        label = stringResource(R.string.admin_identify_replace_images),
                        checked = uiState.replaceAllImages,
                        onToggle = viewModel::toggleReplaceImages,
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            hasSearched = true
                            viewModel.search()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !uiState.searching && uiState.searchName.isNotBlank(),
                    ) {
                        if (uiState.searching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.admin_identify_search), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(visible = uiState.results.isNotEmpty() || uiState.searching) {
                    Text(
                        text = stringResource(R.string.admin_identify_results),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            items(uiState.results) { result ->
                IdentifyResultCard(
                    result = result,
                    isApplying = applyingResult == result && uiState.applying,
                    onClick = {
                        applyingResult = result
                        viewModel.applyResult(result)
                    },
                )
            }

            item {
                AnimatedVisibility(
                    visible = !uiState.searching && uiState.results.isEmpty() && hasSearched,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.admin_identify_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentifyResultCard(
    result: IdentifyResult,
    isApplying: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = !isApplying, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier.width(80.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (result.imageUrl != null) {
                    AsyncImage(
                        model = result.imageUrl,
                        contentDescription = result.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_broken_image),
                        contentDescription = stringResource(R.string.cd_admin_no_image),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                    )
                }
                if (isApplying) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text =
                        buildString {
                            if (result.year != null) append(result.year)
                            if (result.year != null && result.searchProviderName != null)
                                append(" • ")
                            if (result.searchProviderName != null) append(result.searchProviderName)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )

                if (!result.overview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = result.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SleekTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(12.dp),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
    )
}

@Composable
private fun SwitchRowSimple(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
        )
    }
}
