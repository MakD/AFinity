package com.makd.afinity.ui.livetv.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinityBadge

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    AfinityBadge(
        text = stringResource(R.string.livetv_live_badge),
        modifier = modifier,
        containerColor = Color.Red,
        contentColor = Color.White,
    )
}