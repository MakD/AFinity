package com.makd.afinity.ui.person

import android.widget.Toast
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.admin.refresh.RefreshMetadataDialog
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.DetailMenuAction
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.person.components.PersonDetailContent
import com.makd.afinity.ui.requests.FilterParams
import com.makd.afinity.ui.requests.FilterType
import com.makd.afinity.ui.utils.rememberTopBarOpacity

@Composable
fun PersonScreen(
    onItemClick: (AfinityItem) -> Unit,
    onNavigateToSeerrMedia: (SearchResultItem) -> Unit,
    onNavigateToFilteredMedia: (FilterParams) -> Unit,
    onHomeClick: () -> Unit,
    onEditMetadataClick: (String) -> Unit,
    onEditImagesClick: (String) -> Unit,
    onIdentifyClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    var showRefreshDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = {},
                onHomeClick = onHomeClick,
                backgroundOpacity = backgroundOpacity,
            )
        },
        modifier = modifier,
        mode = AfinityScreenMode.Hero,
        scrollOpacity = { topBarOpacity },
    ) {
        when {
            uiState.person != null -> {
                val person = uiState.person!!
                val personId = person.id.toString()
                val adminActions =
                    if (isAdmin) {
                        listOf(
                            DetailMenuAction(
                                label = stringResource(R.string.admin_action_edit_metadata),
                                iconRes = R.drawable.ic_edit_circle,
                                onClick = { onEditMetadataClick(personId) },
                            ),
                            DetailMenuAction(
                                label = stringResource(R.string.admin_action_identify),
                                iconRes = R.drawable.ic_search,
                                onClick = { onIdentifyClick(personId) },
                            ),
                            DetailMenuAction(
                                label = stringResource(R.string.admin_action_edit_images),
                                iconRes = R.drawable.ic_photo_search,
                                onClick = { onEditImagesClick(personId) },
                            ),
                            DetailMenuAction(
                                label = stringResource(R.string.admin_action_refresh_metadata),
                                iconRes = R.drawable.ic_refresh,
                                onClick = { showRefreshDialog = true },
                            ),
                        )
                    } else emptyList()
                if (showRefreshDialog) {
                    val refreshQueuedMessage = stringResource(R.string.admin_refresh_queued)
                    val context = LocalContext.current
                    RefreshMetadataDialog(
                        itemId = personId,
                        itemName = person.name,
                        onDismiss = { showRefreshDialog = false },
                        showTrickplay = false,
                        onQueued = {
                            Toast.makeText(context, refreshQueuedMessage, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                PersonDetailContent(
                    person = person,
                    adminActions = adminActions,
                    movies = uiState.movies,
                    shows = uiState.shows,
                    awards = uiState.awards,
                    externalCredits = uiState.externalCredits,
                    onExternalItemClick = onNavigateToSeerrMedia,
                    onViewAllCredits = {
                        val tmdbId = person.providerIds?.get("Tmdb")?.toIntOrNull()
                        if (tmdbId != null) {
                            onNavigateToFilteredMedia(
                                FilterParams(
                                    type = FilterType.PERSON,
                                    id = tmdbId,
                                    name = person.name,
                                )
                            )
                        }
                    },
                    onItemClick = onItemClick,
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    widthSizeClass = widthSizeClass,
                    lazyListState = lazyListState,
                )
            }

            uiState.error != null -> {
                FullScreenError(
                    message = uiState.error,
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = { viewModel.retry() },
                )
            }

            else -> {
                FullScreenLoading()
            }
        }
    }
}
