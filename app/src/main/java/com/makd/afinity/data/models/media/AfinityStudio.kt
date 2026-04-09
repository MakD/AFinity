package com.makd.afinity.data.models.media

import androidx.core.net.toUri
import java.util.UUID

data class AfinityStudio(
    val id: UUID,
    val name: String,
    val primaryImageUrl: String?,
    val itemCount: Int = 0,
)

fun AfinityStudio.withBaseUrl(newBaseUrl: String): AfinityStudio {
    val base = newBaseUrl.trimEnd('/').toUri()
    return copy(
        primaryImageUrl = primaryImageUrl?.toUri()
            ?.buildUpon()?.scheme(base.scheme)?.encodedAuthority(base.encodedAuthority)?.build()?.toString()
    )
}
