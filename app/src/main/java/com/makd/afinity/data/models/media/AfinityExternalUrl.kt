package com.makd.afinity.data.models.media

import org.jellyfin.sdk.model.api.ExternalUrl

data class AfinityExternalUrl(val name: String?, val url: String?)

fun ExternalUrl.toAfinityExternalUrl(): AfinityExternalUrl {
    return AfinityExternalUrl(name = name, url = url)
}
