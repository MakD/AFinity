package com.makd.afinity.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.person.components.PersonDetailContent

@Composable
fun PersonScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current
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

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                FullScreenLoading()
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_error_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { viewModel.retry() }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }

            uiState.person != null -> {
                PersonDetailContent(
                    person = uiState.person!!,
                    movies = uiState.movies,
                    shows = uiState.shows,
                    onItemClick = { item ->
                        val route =
                            Destination.createItemDetailRoute(
                                itemId = item.id.toString(),
                                itemType =
                                    when (item) {
                                        is AfinityShow -> "Series"
                                        is AfinitySeason -> "Season"
                                        else -> null
                                    },
                                seriesId = (item as? AfinitySeason)?.seriesId?.toString(),
                            )
                        navController.navigate(route)
                    },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    widthSizeClass = widthSizeClass,
                )
            }
        }
    }
}
