package com.makd.afinity.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.post
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.time.Instant
import java.util.UUID

@Serializable
internal data class UserItemDataUpdateBody(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long,
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
)

suspend fun ApiClient.userDataUtc(
    itemId: UUID,
    userId: UUID,
    positionTicks: Long,
    lastPlayedAtMillis: Long?,
): Response<UserItemDataDto> =
    post(
        pathTemplate = "/UserItems/{itemId}/UserData",
        pathParameters = mapOf("itemId" to itemId),
        queryParameters = mapOf("userId" to userId),
        requestBody =
            UserItemDataUpdateBody(
                playbackPositionTicks = positionTicks,
                lastPlayedDate = lastPlayedAtMillis?.let { Instant.ofEpochMilli(it).toString() },
            ),
    )
