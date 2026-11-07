package com.makd.afinity.ui.franchise

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AppBarProfile
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.MediaCategoryGrid
import com.makd.afinity.ui.components.playerClearance
import com.makd.afinity.ui.main.MainUiState

@Composable
fun FranchiseScreen(
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    viewModel: FranchiseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerClearance = playerClearance()

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = { AfinityTopAppBarTitle(uiState.name) },
                backgroundOpacity = backgroundOpacity,
                onSearchClick = onSearchClick,
                profile =
                    AppBarProfile(
                        onClick = onProfileClick,
                        name = mainUiState.userName,
                        imageUrl = mainUiState.userProfileImageUrl,
                    ),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        when {
            uiState.isLoading -> FullScreenLoading(modifier = Modifier.padding(innerPadding))

            uiState.hasError ->
                FullScreenError(
                    message = stringResource(R.string.franchise_load_failed),
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = viewModel::load,
                    modifier = Modifier.padding(innerPadding),
                )

            else ->
                MediaCategoryGrid(
                    items = uiState.items,
                    landscape = false,
                    widthSizeClass = widthSizeClass,
                    playerClearance = playerClearance,
                    onItemClick = onItemClick,
                    initialSortBy = SortBy.RELEASE_DATE,
                    modifier = Modifier.padding(innerPadding),
                )
        }
    }
}
