package com.makd.afinity.data.models.lastfm

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class LastFmTopTracksResponse(
    val toptracks: LastFmTopTracks? = null,
    val error: Int? = null,
    val message: String? = null,
)

@Serializable
data class LastFmTopTracks(
    @Serializable(with = LastFmTrackListSerializer::class)
    val track: List<LastFmTopTrack> = emptyList()
)

@Serializable data class LastFmTopTrack(val name: String? = null, val listeners: String? = null)

@Serializable data class LastFmTrackPopularity(val name: String, val listeners: Long)

object LastFmTrackListSerializer :
    JsonTransformingSerializer<List<LastFmTopTrack>>(ListSerializer(LastFmTopTrack.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonObject) JsonArray(listOf(element)) else element
}
