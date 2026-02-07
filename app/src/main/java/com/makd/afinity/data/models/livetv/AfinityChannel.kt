package com.makd.afinity.data.models.livetv

import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySource
import java.util.UUID

data class AfinityChannel(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String = "",
    override val played: Boolean = false,
    override val favorite: Boolean = false,
    override val liked: Boolean = false,
    override val canPlay: Boolean = true,
    override val canDownload: Boolean = false,
    override val sources: List<AfinitySource> = emptyList(),
    override val runtimeTicks: Long = 0,
    override val playbackPositionTicks: Long = 0,
    override val unplayedItemCount: Int? = null,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter> = emptyList(),
    override val providerIds: Map<String, String>? = null,
    override val externalUrls: List<AfinityExternalUrl>? = null,
    val channelNumber: String?,
    val channelType: ChannelType,
    val currentProgram: AfinityProgram? = null,
    val serviceName: String?,
) : AfinityItem
