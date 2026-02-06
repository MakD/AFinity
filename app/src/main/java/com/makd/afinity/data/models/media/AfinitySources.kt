package com.makd.afinity.data.models.media

interface AfinitySources {
    val sources: List<AfinitySource>
    val runtimeTicks: Long
    val trickplayInfo: Map<String, AfinityTrickplayInfo>?
}
