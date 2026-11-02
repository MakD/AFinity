package com.makd.afinity.ui.settings.servers

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.makd.afinity.ui.theme.isDarkSurface

internal val JellyseerrColor = Color(0xFFA88AFA)
internal val AudiobookshelfColor = Color(0xFFCD9E46)
internal val tmdbColor = Color(0xFF3CBEC9)
internal val mdblistColor = Color(0xFF4283C9)
internal val lastFmColor = Color(0xFFD51007)

internal val SaveColor: Color
    @Composable
    get() =
        if (isDarkSurface()) {
            Color(0xFF81C784)
        } else {
            Color(0xFF2E7D32)
        }

internal val CancelColor: Color
    @Composable
    get() =
        if (isDarkSurface()) {
            Color(0xFFE57373)
        } else {
            Color(0xFFC62828)
        }
