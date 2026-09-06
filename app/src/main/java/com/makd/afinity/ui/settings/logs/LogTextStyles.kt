package com.makd.afinity.ui.settings.logs

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object LogTextStyles {

    private val mono = TextStyle(fontFamily = FontFamily.Monospace)

    val badge = mono.copy(fontSize = 9.sp, lineHeight = 11.sp)
    val metaSmall = mono.copy(fontSize = 10.sp, lineHeight = 12.sp)
    val meta = mono.copy(fontSize = 11.sp, lineHeight = 13.sp)
    val label = mono.copy(fontSize = 12.sp, lineHeight = 14.sp)

    val trace = mono.copy(fontSize = 10.sp, lineHeight = 16.sp)
    val message = mono.copy(fontSize = 12.sp, lineHeight = 18.sp)

    val console = mono.copy(fontSize = 12.sp, lineHeight = 18.sp)

    val pill = TextStyle(fontSize = 10.sp, lineHeight = 12.sp)
    val pillSmall = TextStyle(fontSize = 9.sp, lineHeight = 11.sp)
}
