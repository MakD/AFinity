package com.makd.afinity.ui.livetv.models

import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram

data class ProgramWithChannel(
    val program: AfinityProgram,
    val channel: AfinityChannel
)