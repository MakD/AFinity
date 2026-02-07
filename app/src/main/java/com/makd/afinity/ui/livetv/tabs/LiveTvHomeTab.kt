package com.makd.afinity.ui.livetv.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.livetv.LiveTvUiState
import com.makd.afinity.ui.livetv.components.ProgramCategoryRow
import com.makd.afinity.ui.livetv.models.LiveTvCategory
import com.makd.afinity.ui.livetv.models.ProgramWithChannel
import java.time.LocalDateTime
import kotlinx.coroutines.delay

@Composable
fun LiveTvHomeTab(
    uiState: LiveTvUiState,
    onProgramClick: (ProgramWithChannel) -> Unit,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            currentTime = LocalDateTime.now()
        }
    }
    if (uiState.isCategoriesLoading && uiState.categorizedPrograms.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val orderedCategories =
        listOf(
            LiveTvCategory.ON_NOW,
            LiveTvCategory.MOVIES,
            LiveTvCategory.SHOWS,
            LiveTvCategory.SPORTS,
            LiveTvCategory.KIDS,
            LiveTvCategory.NEWS,
        )

    val categoriesWithPrograms =
        orderedCategories.mapNotNull { category ->
            uiState.categorizedPrograms[category]
                ?.takeIf { it.isNotEmpty() }
                ?.let { programs -> category to programs }
        }

    if (categoriesWithPrograms.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
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
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        items(items = categoriesWithPrograms, key = { it.first.name }) { (category, programs) ->
            ProgramCategoryRow(
                category = category,
                programs = programs,
                now = currentTime,
                onProgramClick = onProgramClick,
                widthSizeClass = widthSizeClass,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
