package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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

@Composable
fun ProgramCategoryRow(
    category: LiveTvCategory,
    programs: List<ProgramWithChannel>,
    onProgramClick: (ProgramWithChannel) -> Unit,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    if (programs.isEmpty()) return

    val cardWidth = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 200.dp
        WindowWidthSizeClass.Medium -> 220.dp
        WindowWidthSizeClass.Expanded -> 260.dp
        else -> 200.dp
    }

    Column(modifier = modifier) {
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(
                items = programs,
                key = { "${it.program.id}_${it.channel.id}" }
            ) { programWithChannel ->
                ProgramCard(
                    programWithChannel = programWithChannel,
                    onClick = { onProgramClick(programWithChannel) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}