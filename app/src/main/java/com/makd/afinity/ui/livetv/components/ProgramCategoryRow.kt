package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.livetv.models.LiveTvCategory
import com.makd.afinity.ui.livetv.models.ProgramWithChannel
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import java.time.LocalDateTime

@Composable
fun ProgramCategoryRow(
    category: LiveTvCategory,
    programs: List<ProgramWithChannel>,
    onProgramClick: (ProgramWithChannel) -> Unit,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    now: LocalDateTime
) {
    if (programs.isEmpty()) return

    val cardWidth = widthSizeClass.landscapeWidth
    val cardHeight =
        CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = programs,
                key = { "${it.program.id}_${it.channel.id}" }
            ) { programWithChannel ->
                ProgramCard(
                    programWithChannel = programWithChannel,
                    onClick = { onProgramClick(programWithChannel) },
                    cardWidth = cardWidth,
                    now = now
                )
            }
        }
    }
}