package com.makd.afinity.data.models.media

import android.net.Uri
import com.makd.afinity.data.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind

data class AfinityPersonImage(val uri: Uri?, val blurHash: String?)

fun BaseItemPerson.toAfinityImage(repository: JellyfinRepository): AfinityPersonImage {
    val baseUrl = Uri.parse(repository.getBaseUrl())
    return AfinityPersonImage(
        uri =
            primaryImageTag?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("items/$id/Images/${ImageType.PRIMARY}")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        blurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.get(primaryImageTag),
    )
}

data class AfinityPerson(
    val id: UUID,
    val name: String,
    val type: PersonKind,
    val role: String,
    val image: AfinityPersonImage,
)

fun BaseItemPerson.toAfinityPerson(repository: JellyfinRepository): AfinityPerson {
    return AfinityPerson(
        id = id,
        name = name.orEmpty(),
        type = type,
        role = role.orEmpty(),
        image = toAfinityImage(repository),
    )
}
