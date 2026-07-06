package com.makd.afinity.data.repository.audiobookshelf

import com.makd.afinity.data.database.entities.AudiobookshelfEpisodeEntity
import com.makd.afinity.data.models.audiobookshelf.AudioFile
import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.data.models.audiobookshelf.Enclosure
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import kotlinx.serialization.json.Json

internal fun PodcastEpisode.toEntity(
    libraryItemId: String,
    serverId: String,
    userId: String,
    json: Json,
): AudiobookshelfEpisodeEntity {
    return AudiobookshelfEpisodeEntity(
        id = id,
        libraryItemId = libraryItemId,
        jellyfinServerId = serverId,
        jellyfinUserId = userId,
        oldEpisodeId = oldEpisodeId,
        episodeIndex = index,
        season = season,
        episode = episode,
        episodeType = episodeType,
        title = title,
        subtitle = subtitle,
        description = description,
        enclosureUrl = enclosure?.url,
        enclosureType = enclosure?.type,
        enclosureLength = enclosure?.length,
        guid = guid,
        pubDate = pubDate,
        serializedChapters = chapters?.let { json.encodeToString(it) },
        serializedAudioFile = audioFile?.let { json.encodeToString(it) },
        serializedAudioTrack = audioTrack?.let { json.encodeToString(it) },
        publishedAt = publishedAt,
        addedAt = addedAt,
        updatedAt = updatedAt,
        duration = duration,
        size = size,
    )
}

internal fun AudiobookshelfEpisodeEntity.toPodcastEpisode(json: Json): PodcastEpisode {
    return PodcastEpisode(
        id = id,
        oldEpisodeId = oldEpisodeId,
        index = episodeIndex,
        season = season,
        episode = episode,
        episodeType = episodeType,
        title = title,
        subtitle = subtitle,
        description = description,
        enclosure =
            enclosureUrl?.let { url ->
                Enclosure(url = url, type = enclosureType, length = enclosureLength)
            },
        guid = guid,
        pubDate = pubDate,
        chapters =
            serializedChapters?.let {
                try {
                    json.decodeFromString<List<BookChapter>>(it)
                } catch (e: Exception) {
                    null
                }
            },
        audioFile =
            serializedAudioFile?.let {
                try {
                    json.decodeFromString<AudioFile>(it)
                } catch (e: Exception) {
                    null
                }
            },
        audioTrack =
            serializedAudioTrack?.let {
                try {
                    json.decodeFromString<AudioTrack>(it)
                } catch (e: Exception) {
                    null
                }
            },
        publishedAt = publishedAt,
        addedAt = addedAt,
        updatedAt = updatedAt,
        duration = duration,
        size = size,
    )
}
