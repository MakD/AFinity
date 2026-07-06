package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.home.components.HomeSectionHeader
import com.makd.afinity.ui.livetv.models.LiveTvCategory
import com.makd.afinity.ui.livetv.models.ProgramWithChannel
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth

@Composable
fun ProgramCategoryRow(
    category: LiveTvCategory,
    programs: List<ProgramWithChannel>,
    onProgramClick: (ProgramWithChannel) -> Unit,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    if (programs.isEmpty()) return
    val cardWidth = widthSizeClass.landscapeWidth

    Column(modifier = modifier.padding(horizontal = 14.dp)) {
        HomeSectionHeader(title = stringResource(category.displayNameRes))

        LazyRow(
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(
                items = programs,
                key = { "${it.program.id}_${it.channel.id}" },
            ) { programWithChannel ->
                ProgramCard(
                    programWithChannel = programWithChannel,
                    onClick = { onProgramClick(programWithChannel) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}
