package com.makd.afinity.ui.person

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
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
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LocalPlayerOffset.current
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
                PersonDetailContent(
                    person = uiState.person!!,
                    movies = uiState.movies,
                    shows = uiState.shows,
                    awards = uiState.awards,
                    externalCredits = uiState.externalCredits,
                    onExternalItemClick = onNavigateToSeerrMedia,
                    onViewAllCredits = {
                        val person = uiState.person
                        val tmdbId = person?.providerIds?.get("Tmdb")?.toIntOrNull()
                        if (person != null && tmdbId != null) {
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
