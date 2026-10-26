package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.ui.components.CardRowContentPadding
import com.makd.afinity.ui.components.cardRowBleed
import com.makd.afinity.ui.home.components.HomeSectionHeader
import com.makd.afinity.ui.livetv.models.LiveTvCategory
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth

@Composable
fun ProgramCategoryRow(
    category: LiveTvCategory,
    programs: List<AfinityProgram>,
    onProgramClick: (AfinityProgram) -> Unit,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    if (programs.isEmpty()) return
    val cardWidth = widthSizeClass.landscapeWidth

    Column(modifier = modifier.padding(horizontal = CardDimensions.RowGutter)) {
        HomeSectionHeader(title = stringResource(category.displayNameRes))

        LazyRow(
            modifier = Modifier.cardRowBleed(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = CardRowContentPadding,
        ) {
            items(items = programs, key = { it.id }) { program ->
                ProgramCard(
                    program = program,
                    onClick = { onProgramClick(program) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}
