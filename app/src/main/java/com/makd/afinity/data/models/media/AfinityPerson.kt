package com.makd.afinity.data.models.media

import android.net.Uri
import org.jellyfin.sdk.model.api.PersonKind
import java.util.UUID

data class AfinityPersonImage(val uri: Uri?, val blurHash: String?)

data class AfinityPerson(
    val id: UUID,
    val name: String,
    val type: PersonKind,
    val role: String,
    val image: AfinityPersonImage,
)
