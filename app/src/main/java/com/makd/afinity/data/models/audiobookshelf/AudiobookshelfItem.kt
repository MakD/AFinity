package com.makd.afinity.data.models.audiobookshelf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LibraryItem(
    @SerialName("id") val id: String,
    @SerialName("ino") val ino: String? = null,
    @SerialName("oldLibraryItemId") val oldLibraryItemId: String? = null,
    @SerialName("libraryId") val libraryId: String,
    @SerialName("folderId") val folderId: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("relPath") val relPath: String? = null,
    @SerialName("isFile") val isFile: Boolean? = null,
    @SerialName("mtimeMs") val mtimeMs: Long? = null,
    @SerialName("ctimeMs") val ctimeMs: Long? = null,
    @SerialName("birthtimeMs") val birthtimeMs: Long? = null,
    @SerialName("addedAt") val addedAt: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    @SerialName("isMissing") val isMissing: Boolean? = null,
    @SerialName("isInvalid") val isInvalid: Boolean? = null,
    @SerialName("mediaType") val mediaType: String,
    @SerialName("media") val media: Media,
    @SerialName("numFiles") val numFiles: Int? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("userMediaProgress") val userMediaProgress: MediaProgress? = null,
    @SerialName("rssFeedUrl") val rssFeedUrl: String? = null,
)

@Serializable
data class Media(
    @SerialName("id") val id: String? = null,
    @SerialName("libraryItemId") val libraryItemId: String? = null,
    @SerialName("metadata") val metadata: MediaMetadata,
    @SerialName("coverPath") val coverPath: String? = null,
    @SerialName("tags") val tags: List<String>? = null,
    @SerialName("audioFiles") val audioFiles: List<AudioFile>? = null,
    @SerialName("chapters") val chapters: List<BookChapter>? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("tracks") val tracks: List<AudioTrack>? = null,
    @SerialName("episodes") val episodes: List<PodcastEpisode>? = null,
    @SerialName("autoDownloadEpisodes") val autoDownloadEpisodes: Boolean? = null,
    @SerialName("autoDownloadSchedule") val autoDownloadSchedule: String? = null,
    @SerialName("lastEpisodeCheck") val lastEpisodeCheck: Long? = null,
    @SerialName("maxEpisodesToKeep") val maxEpisodesToKeep: Int? = null,
    @SerialName("maxNewEpisodesToDownload") val maxNewEpisodesToDownload: Int? = null,
    @SerialName("ebookFile") val ebookFile: EbookFile? = null,
    @SerialName("numTracks") val numTracks: Int? = null,
    @SerialName("numAudioFiles") val numAudioFiles: Int? = null,
    @SerialName("numChapters") val numChapters: Int? = null,
    @SerialName("numMissingParts") val numMissingParts: Int? = null,
    @SerialName("numInvalidAudioFiles") val numInvalidAudioFiles: Int? = null,
)

@Serializable
data class MediaMetadata(
    @SerialName("title") val title: String? = null,
    @SerialName("titleIgnorePrefix") val titleIgnorePrefix: String? = null,
    @SerialName("subtitle") val subtitle: String? = null,
    @SerialName("authorName") val authorName: String? = null,
    @SerialName("authorNameLF") val authorNameLF: String? = null,
    @SerialName("narratorName") val narratorName: String? = null,
    @SerialName("seriesName") val seriesName: String? = null,
    @SerialName("genres") val genres: List<String>? = null,
    @SerialName("publishedYear") val publishedYear: String? = null,
    @SerialName("publishedDate") val publishedDate: String? = null,
    @SerialName("publisher") val publisher: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("isbn") val isbn: String? = null,
    @SerialName("asin") val asin: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("explicit") val explicit: Boolean? = null,
    @SerialName("abridged") val abridged: Boolean? = null,
    @SerialName("authors") val authors: List<Author>? = null,
    @SerialName("narrators") val narrators: List<String>? = null,
    @SerialName("series") val series: List<SeriesItem>? = null,
    @SerialName("feedUrl") val feedUrl: String? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("itunesPageUrl") val itunesPageUrl: String? = null,
    @SerialName("itunesId") val itunesId: Int? = null,
    @SerialName("itunesArtistId") val itunesArtistId: Int? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("lastCoverSearch") val lastCoverSearch: Long? = null,
    @SerialName("lastCoverSearchQuery") val lastCoverSearchQuery: String? = null,
)

@Serializable
data class Author(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("asin") val asin: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("imagePath") val imagePath: String? = null,
    @SerialName("addedAt") val addedAt: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    @SerialName("numBooks") val numBooks: Int? = null,
)

@Serializable
data class SeriesItem(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("sequence") val sequence: String? = null,
)

@Serializable
data class AudioFile(
    @SerialName("index") val index: Int,
    @SerialName("ino") val ino: String,
    @SerialName("metadata") val metadata: FileMetadata,
    @SerialName("addedAt") val addedAt: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    @SerialName("trackNumFromMeta") val trackNumFromMeta: Int? = null,
    @SerialName("discNumFromMeta") val discNumFromMeta: Int? = null,
    @SerialName("trackNumFromFilename") val trackNumFromFilename: Int? = null,
    @SerialName("discNumFromFilename") val discNumFromFilename: Int? = null,
    @SerialName("manuallyVerified") val manuallyVerified: Boolean? = null,
    @SerialName("invalid") val invalid: Boolean? = null,
    @SerialName("exclude") val exclude: Boolean? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("format") val format: String? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("bitRate") val bitRate: Int? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("codec") val codec: String? = null,
    @SerialName("timeBase") val timeBase: String? = null,
    @SerialName("channels") val channels: Int? = null,
    @SerialName("channelLayout") val channelLayout: String? = null,
    @SerialName("embeddedCoverArt") val embeddedCoverArt: String? = null,
    @SerialName("metaTags") val metaTags: AudioMetaTags? = null,
    @SerialName("mimeType") val mimeType: String? = null,
)

@Serializable
data class FileMetadata(
    @SerialName("filename") val filename: String,
    @SerialName("ext") val ext: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("relPath") val relPath: String? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("mtimeMs") val mtimeMs: Long? = null,
    @SerialName("ctimeMs") val ctimeMs: Long? = null,
    @SerialName("birthtimeMs") val birthtimeMs: Long? = null,
)

@Serializable
data class AudioMetaTags(
    @SerialName("tagAlbum") val tagAlbum: String? = null,
    @SerialName("tagArtist") val tagArtist: String? = null,
    @SerialName("tagGenre") val tagGenre: String? = null,
    @SerialName("tagTitle") val tagTitle: String? = null,
    @SerialName("tagSeries") val tagSeries: String? = null,
    @SerialName("tagSeriesPart") val tagSeriesPart: String? = null,
    @SerialName("tagTrack") val tagTrack: String? = null,
    @SerialName("tagDisc") val tagDisc: String? = null,
    @SerialName("tagSubtitle") val tagSubtitle: String? = null,
    @SerialName("tagAlbumArtist") val tagAlbumArtist: String? = null,
    @SerialName("tagDate") val tagDate: String? = null,
    @SerialName("tagComposer") val tagComposer: String? = null,
    @SerialName("tagPublisher") val tagPublisher: String? = null,
    @SerialName("tagComment") val tagComment: String? = null,
    @SerialName("tagDescription") val tagDescription: String? = null,
    @SerialName("tagEncoder") val tagEncoder: String? = null,
    @SerialName("tagEncodedBy") val tagEncodedBy: String? = null,
    @SerialName("tagIsbn") val tagIsbn: String? = null,
    @SerialName("tagLanguage") val tagLanguage: String? = null,
    @SerialName("tagASIN") val tagASIN: String? = null,
    @SerialName("tagOverdriveMediaMarker") val tagOverdriveMediaMarker: String? = null,
    @SerialName("tagOriginalYear") val tagOriginalYear: String? = null,
    @SerialName("tagReleaseCountry") val tagReleaseCountry: String? = null,
    @SerialName("tagReleaseType") val tagReleaseType: String? = null,
    @SerialName("tagReleaseStatus") val tagReleaseStatus: String? = null,
    @SerialName("tagISRC") val tagISRC: String? = null,
    @SerialName("tagMusicBrainzTrackId") val tagMusicBrainzTrackId: String? = null,
    @SerialName("tagMusicBrainzAlbumId") val tagMusicBrainzAlbumId: String? = null,
    @SerialName("tagMusicBrainzAlbumArtistId") val tagMusicBrainzAlbumArtistId: String? = null,
    @SerialName("tagMusicBrainzArtistId") val tagMusicBrainzArtistId: String? = null,
)

@Serializable
data class EbookFile(
    @SerialName("ino") val ino: String,
    @SerialName("metadata") val metadata: FileMetadata,
    @SerialName("ebookFormat") val ebookFormat: String,
    @SerialName("addedAt") val addedAt: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
)

@Serializable
data class PodcastEpisode(
    @SerialName("id") val id: String,
    @SerialName("oldEpisodeId") val oldEpisodeId: String? = null,
    @SerialName("index") val index: Int? = null,
    @SerialName("season") val season: String? = null,
    @SerialName("episode") val episode: String? = null,
    @SerialName("episodeType") val episodeType: String? = null,
    @SerialName("title") val title: String,
    @SerialName("subtitle") val subtitle: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("enclosure") val enclosure: Enclosure? = null,
    @SerialName("guid") val guid: String? = null,
    @SerialName("pubDate") val pubDate: String? = null,
    @SerialName("chapters") val chapters: List<BookChapter>? = null,
    @SerialName("audioFile") val audioFile: AudioFile? = null,
    @SerialName("audioTrack") val audioTrack: AudioTrack? = null,
    @SerialName("publishedAt") val publishedAt: Long? = null,
    @SerialName("addedAt") val addedAt: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("size") val size: Long? = null,
)

@Serializable
data class Enclosure(
    @SerialName("url") val url: String,
    @SerialName("type") val type: String? = null,
    @SerialName("length") val length: String? = null,
)

@Serializable
data class ItemResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("ino") val ino: String? = null,
    @SerialName("oldLibraryItemId") val oldLibraryItemId: String? = null,
    @SerialName("libraryId") val libraryId: String? = null,
    @SerialName("folderId") val folderId: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("relPath") val relPath: String? = null,
    @SerialName("isFile") val isFile: Boolean? = null,
    @SerialName("mtimeMs") val mtimeMs: Long? = null,
    @SerialName("ctimeMs") val ctimeMs: Long? = null,
    @SerialName("birthtimeMs") val birthtimeMs: Long? = null,
    @SerialName("addedAt") val addedAt: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    @SerialName("isMissing") val isMissing: Boolean? = null,
    @SerialName("isInvalid") val isInvalid: Boolean? = null,
    @SerialName("mediaType") val mediaType: String? = null,
    @SerialName("media") val media: Media? = null,
    @SerialName("numFiles") val numFiles: Int? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("userMediaProgress") val userMediaProgress: MediaProgress? = null,
    @SerialName("rssFeedUrl") val rssFeedUrl: String? = null,
    @SerialName("libraryFiles") val libraryFiles: List<LibraryFile>? = null,
)

@Serializable
data class LibraryFile(
    @SerialName("ino") val ino: String,
    @SerialName("metadata") val metadata: FileMetadata,
    @SerialName("addedAt") val addedAt: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    @SerialName("fileType") val fileType: String? = null,
)

@Serializable
data class SearchResponse(
    @SerialName("book") val book: List<SearchResultBook>? = null,
    @SerialName("podcast") val podcast: List<SearchResultPodcast>? = null,
    @SerialName("narrators") val narrators: List<NarratorResult>? = null,
    @SerialName("authors") val authors: List<Author>? = null,
    @SerialName("series") val series: List<SeriesResult>? = null,
    @SerialName("tags") val tags: List<String>? = null,
)

@Serializable
data class SearchResultBook(
    @SerialName("libraryItem") val libraryItem: LibraryItem,
    @SerialName("matchKey") val matchKey: String? = null,
    @SerialName("matchText") val matchText: String? = null,
)

@Serializable
data class SearchResultPodcast(
    @SerialName("libraryItem") val libraryItem: LibraryItem,
    @SerialName("matchKey") val matchKey: String? = null,
    @SerialName("matchText") val matchText: String? = null,
)

@Serializable
data class NarratorResult(
    @SerialName("name") val name: String,
    @SerialName("numBooks") val numBooks: Int? = null,
)

@Serializable
data class SeriesResult(
    @SerialName("series") val series: SeriesItem,
    @SerialName("books") val books: List<LibraryItem>? = null,
)

@Serializable
data class ItemsInProgressResponse(@SerialName("libraryItems") val libraryItems: List<LibraryItem>)
