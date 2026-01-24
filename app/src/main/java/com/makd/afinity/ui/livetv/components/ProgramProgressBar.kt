package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.livetv.AfinityProgram

@Composable
fun ProgramProgressBar(
    program: AfinityProgram,
    modifier: Modifier = Modifier
) {
    val progress = program.getProgressPercent() / 100f

    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round
    )
}