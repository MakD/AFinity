package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.utils.horizontalBleed

val CardRowContentPadding = PaddingValues(horizontal = CardDimensions.RowGutter)

fun Modifier.cardRowBleed(): Modifier = horizontalBleed(CardDimensions.RowGutter)

fun cardRowContentPadding(ownPadding: Dp): PaddingValues =
    if (ownPadding > 0.dp) PaddingValues(horizontal = ownPadding) else CardRowContentPadding

fun Modifier.cardRowBleedUnlessPadded(ownPadding: Dp): Modifier =
    if (ownPadding > 0.dp) this else cardRowBleed()
