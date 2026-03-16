package com.makd.afinity.data.models.media

import android.net.Uri
import java.util.UUID
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind

data class AfinityPersonImage(val uri: Uri?, val blurHash: String?)

data class AfinityPerson(
    val id: UUID,
    val name: String,
    val type: PersonKind,
    val role: String,
    val image: AfinityPersonImage,
)
