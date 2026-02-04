package com.makd.afinity.data.models.audiobookshelf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class Library(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("folders")
    val folders: List<Folder>? = null,
    @SerialName("displayOrder")
    val displayOrder: Int? = null,
    @SerialName("icon")
    val icon: String? = null,
    @SerialName("mediaType")
    val mediaType: String,
    @SerialName("provider")
    val provider: String? = null,
    @SerialName("settings")
    val settings: LibrarySettings? = null,
    @SerialName("stats")
    val stats: LibraryStats? = null,
    @SerialName("createdAt")
    val createdAt: Long? = null,
    @SerialName("lastUpdate")
    val lastUpdate: Long? = null
)

@Serializable
data class Folder(
    @SerialName("id")
    val id: String,
    @SerialName("fullPath")
    val fullPath: String,
    @SerialName("libraryId")
    val libraryId: String? = null,
    @SerialName("addedAt")
    val addedAt: Long? = null
)

@Serializable
data class LibrarySettings(
    @SerialName("coverAspectRatio")
    val coverAspectRatio: Int? = null,
    @SerialName("disableWatcher")
    val disableWatcher: Boolean? = null,
    @SerialName("skipMatchingMediaWithAsin")
    val skipMatchingMediaWithAsin: Boolean? = null,
    @SerialName("skipMatchingMediaWithIsbn")
    val skipMatchingMediaWithIsbn: Boolean? = null,
    @SerialName("autoScanCronExpression")
    val autoScanCronExpression: String? = null,
    @SerialName("audiobooksOnly")
    val audiobooksOnly: Boolean? = null,
    @SerialName("hideSingleBookSeries")
    val hideSingleBookSeries: Boolean? = null,
    @SerialName("onlyShowLaterBooksInContinueSeries")
    val onlyShowLaterBooksInContinueSeries: Boolean? = null,
    @SerialName("metadataPrecedence")
    val metadataPrecedence: List<String>? = null,
    @SerialName("podcastSearchRegion")
    val podcastSearchRegion: String? = null
)

@Serializable
data class LibraryStats(
    @SerialName("totalItems")
    val totalItems: Int? = null,
    @SerialName("totalSize")
    val totalSize: Long? = null,
    @SerialName("totalDuration")
    val totalDuration: Double? = null,
    @SerialName("numAudioFiles")
    val numAudioFiles: Int? = null,
    @SerialName("numAudioTracks")
    val numAudioTracks: Int? = null
)

@Serializable
data class LibrariesResponse(
    @SerialName("libraries")
    val libraries: List<Library>
)

@Serializable
data class LibraryResponse(
    @SerialName("library")
    val library: Library? = null,
    @SerialName("issues")
    val issues: Int? = null,
    @SerialName("numUserPlaylists")
    val numUserPlaylists: Int? = null,
    @SerialName("filterData")
    val filterData: FilterData? = null
)

@Serializable
data class FilterData(
    @SerialName("authors")
    val authors: List<AuthorFilter>? = null,
    @SerialName("genres")
    val genres: List<String>? = null,
    @SerialName("tags")
    val tags: List<String>? = null,
    @SerialName("series")
    val series: List<SeriesFilter>? = null,
    @SerialName("narrators")
    val narrators: List<String>? = null,
    @SerialName("languages")
    val languages: List<String>? = null,
    @SerialName("publishers")
    val publishers: List<String>? = null
)

@Serializable
data class AuthorFilter(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String
)

@Serializable
data class SeriesFilter(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String
)

@Serializable
data class LibraryItemsResponse(
    @SerialName("results")
    val results: List<LibraryItem>,
    @SerialName("total")
    val total: Int,
    @SerialName("limit")
    val limit: Int,
    @SerialName("page")
    val page: Int,
    @SerialName("sortBy")
    val sortBy: String? = null,
    @SerialName("sortDesc")
    val sortDesc: Boolean? = null,
    @SerialName("filterBy")
    val filterBy: String? = null,
    @SerialName("mediaType")
    val mediaType: String? = null,
    @SerialName("minified")
    val minified: Boolean? = null,
    @SerialName("collapseseries")
    val collapseseries: Boolean? = null,
    @SerialName("include")
    val include: String? = null
)

@Serializable
data class AudiobookshelfSeries(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("nameIgnorePrefix")
    val nameIgnorePrefix: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("addedAt")
    val addedAt: Long? = null,
    @SerialName("updatedAt")
    val updatedAt: Long? = null,
    @SerialName("libraryId")
    val libraryId: String? = null,
    @SerialName("books")
    val books: List<LibraryItem> = emptyList()
)

@Serializable
data class SeriesListResponse(
    @SerialName("results")
    val results: List<AudiobookshelfSeries>,
    @SerialName("total")
    val total: Int,
    @SerialName("limit")
    val limit: Int,
    @SerialName("page")
    val page: Int,
    @SerialName("sortBy")
    val sortBy: String? = null,
    @SerialName("sortDesc")
    val sortDesc: Boolean? = null,
    @SerialName("filterBy")
    val filterBy: String? = null,
    @SerialName("minified")
    val minified: Boolean? = null,
    @SerialName("include")
    val include: String? = null
)

@Serializable
data class PersonalizedView(
    @SerialName("id")
    val id: String,
    @SerialName("label")
    val label: String,
    @SerialName("labelStringKey")
    val labelStringKey: String? = null,
    @SerialName("type")
    val type: String,
    @SerialName("entities")
    val entities: JsonArray = JsonArray(emptyList())
)
