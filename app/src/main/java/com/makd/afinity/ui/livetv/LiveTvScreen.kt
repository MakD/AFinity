@file:UnstableApi

package com.makd.afinity.ui.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AppBarProfile
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LibraryShortcutCard
import com.makd.afinity.ui.components.LibraryShortcutsRow
import com.makd.afinity.ui.livetv.components.ProgramCategoryRow
import com.makd.afinity.ui.livetv.models.LiveTvCategory
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun LiveTvScreen(
    mainUiState: MainUiState,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onGuideClick: () -> Unit,
    onChannelsClick: () -> Unit,
    onRecordingsClick: () -> Unit,
    onProgramClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: LiveTvViewModel = hiltViewModel(),
    onMenuClick: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onPauseOrDispose { viewModel.setScreenVisible(false) }
    }

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = { AfinityTopAppBarTitle(stringResource(R.string.livetv_title)) },
                onMenuClick = onMenuClick,
                onSearchClick = onSearchClick,
                profile =
                    AppBarProfile(
                        onClick = onProfileClick,
                        name = mainUiState.userName,
                        imageUrl = mainUiState.userProfileImageUrl,
                    ),
                backgroundOpacity = backgroundOpacity,
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        when {
            uiState.isLoading -> FullScreenLoading(modifier = Modifier.padding(paddingValues))

            uiState.accessCheckFailed ->
                FullScreenError(
                    message = stringResource(R.string.error_failed_check_livetv),
                    modifier = Modifier.padding(paddingValues),
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = viewModel::retry,
                )

            !uiState.hasLiveTvAccess ->
                FullScreenEmpty(
                    modifier = Modifier.padding(paddingValues),
                    icon = painterResource(id = R.drawable.ic_live_tv_nav),
                    title = stringResource(R.string.livetv_error_not_available_title),
                    message = stringResource(R.string.livetv_error_not_available_message),
                )

            else ->
                LiveTvHomeContent(
                    uiState = uiState,
                    onGuideClick = onGuideClick,
                    onChannelsClick = onChannelsClick,
                    onRecordingsClick = onRecordingsClick,
                    onProgramClick = { program -> onProgramClick(program.id.toString()) },
                    onRetry = viewModel::retry,
                    widthSizeClass = widthSizeClass,
                    modifier = Modifier.padding(paddingValues),
                )
        }
    }
}

@Composable
private fun LiveTvHomeContent(
    uiState: LiveTvUiState,
    onGuideClick: () -> Unit,
    onChannelsClick: () -> Unit,
    onRecordingsClick: () -> Unit,
    onProgramClick: (AfinityProgram) -> Unit,
    onRetry: () -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val playerOffset = LocalPlayerOffset.current
    val cardWidth = CardDimensions.landscapeCardWidth

    val categoriesWithPrograms =
        LiveTvCategory.entries.mapNotNull { category ->
            uiState.categorizedPrograms[category]
                ?.takeIf { it.isNotEmpty() }
                ?.let { programs -> category to programs }
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp + playerOffset),
    ) {
        item(key = "browse") {
            LibraryShortcutsRow(
                title = stringResource(R.string.livetv_action_browse),
                modifier = Modifier.padding(bottom = 28.dp),
            ) {
                item(key = "guide") {
                    LibraryShortcutCard(
                        label = stringResource(R.string.livetv_tab_guide),
                        iconRes = R.drawable.ic_epg,
                        gradientStart = Color(0xFFB1AADA),
                        gradientEnd = Color(0xFF5A5482),
                        cardWidth = cardWidth,
                        onClick = onGuideClick,
                    )
                }
                item(key = "channels") {
                    LibraryShortcutCard(
                        label = stringResource(R.string.livetv_tab_channels),
                        iconRes = R.drawable.ic_channels,
                        gradientStart = Color(0xFFA4C4D6),
                        gradientEnd = Color(0xFF50787A),
                        cardWidth = cardWidth,
                        onClick = onChannelsClick,
                    )
                }
                item(key = "recordings") {
                    LibraryShortcutCard(
                        label = stringResource(R.string.livetv_tab_recordings),
                        iconRes = R.drawable.ic_record,
                        gradientStart = Color(0xFFE2AE95),
                        gradientEnd = Color(0xFF965243),
                        cardWidth = cardWidth,
                        onClick = onRecordingsClick,
                    )
                }
            }
        }

        when {
            uiState.isCategoriesLoading && categoriesWithPrograms.isEmpty() ->
                item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

            uiState.programsFailed && categoriesWithPrograms.isEmpty() ->
                item(key = "error") {
                    LiveTvSectionError(
                        message = stringResource(R.string.livetv_programs_load_failed),
                        onRetry = onRetry,
                    )
                }

            categoriesWithPrograms.isEmpty() -> item(key = "empty") { LiveTvHomeEmpty() }

            else ->
                items(items = categoriesWithPrograms, key = { it.first.name }) {
                    (category, programs) ->
                    ProgramCategoryRow(
                        category = category,
                        programs = programs,
                        onProgramClick = onProgramClick,
                        widthSizeClass = widthSizeClass,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
        }
    }
}

@Composable
private fun LiveTvHomeEmpty() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_live_tv_nav),
            contentDescription = null,
            modifier = Modifier.padding(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        Text(
            text = stringResource(R.string.livetv_home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = stringResource(R.string.livetv_home_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun LiveTvSectionError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            colors =
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(
                text = stringResource(R.string.action_retry),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
