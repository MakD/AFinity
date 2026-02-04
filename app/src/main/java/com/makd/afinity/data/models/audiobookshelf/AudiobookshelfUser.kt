package com.makd.afinity.data.models.audiobookshelf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudiobookshelfUser(
    @SerialName("id")
    val id: String,
    @SerialName("username")
    val username: String,
    @SerialName("type")
    val type: String? = null,
    @SerialName("token")
    val token: String? = null,
    @SerialName("mediaProgress")
    val mediaProgress: List<MediaProgress>? = null,
    @SerialName("bookmarks")
    val bookmarks: List<Bookmark>? = null,
    @SerialName("isActive")
    val isActive: Boolean? = null,
    @SerialName("isLocked")
    val isLocked: Boolean? = null,
    @SerialName("lastSeen")
    val lastSeen: Long? = null,
    @SerialName("createdAt")
    val createdAt: Long? = null,
    @SerialName("permissions")
    val permissions: UserPermissions? = null,
    @SerialName("librariesAccessible")
    val librariesAccessible: List<String>? = null,
    @SerialName("itemTagsSelected")
    val itemTagsSelected: List<String>? = null
)

@Serializable
data class UserPermissions(
    @SerialName("download")
    val download: Boolean? = null,
    @SerialName("update")
    val update: Boolean? = null,
    @SerialName("delete")
    val delete: Boolean? = null,
    @SerialName("upload")
    val upload: Boolean? = null,
    @SerialName("accessAllLibraries")
    val accessAllLibraries: Boolean? = null,
    @SerialName("accessAllTags")
    val accessAllTags: Boolean? = null,
    @SerialName("accessExplicitContent")
    val accessExplicitContent: Boolean? = null
)

@Serializable
data class Bookmark(
    @SerialName("libraryItemId")
    val libraryItemId: String,
    @SerialName("title")
    val title: String,
    @SerialName("time")
    val time: Double,
    @SerialName("createdAt")
    val createdAt: Long
)

@Serializable
data class LoginRequest(
    @SerialName("username")
    val username: String,
    @SerialName("password")
    val password: String
)

@Serializable
data class LoginResponse(
    @SerialName("user")
    val user: AudiobookshelfUser,
    @SerialName("userDefaultLibraryId")
    val userDefaultLibraryId: String? = null,
    @SerialName("serverSettings")
    val serverSettings: ServerSettings? = null,
    @SerialName("ereaderDevices")
    val ereaderDevices: List<String>? = null
)

@Serializable
data class ServerSettings(
    @SerialName("id")
    val id: String? = null,
    @SerialName("scannerFindCovers")
    val scannerFindCovers: Boolean? = null,
    @SerialName("scannerCoverProvider")
    val scannerCoverProvider: String? = null,
    @SerialName("scannerParseSubtitle")
    val scannerParseSubtitle: Boolean? = null,
    @SerialName("scannerPreferMatchedMetadata")
    val scannerPreferMatchedMetadata: Boolean? = null,
    @SerialName("scannerDisableWatcher")
    val scannerDisableWatcher: Boolean? = null,
    @SerialName("storeCoverWithItem")
    val storeCoverWithItem: Boolean? = null,
    @SerialName("storeMetadataWithItem")
    val storeMetadataWithItem: Boolean? = null,
    @SerialName("metadataFileFormat")
    val metadataFileFormat: String? = null,
    @SerialName("rateLimitLoginRequests")
    val rateLimitLoginRequests: Int? = null,
    @SerialName("rateLimitLoginWindow")
    val rateLimitLoginWindow: Int? = null,
    @SerialName("backupSchedule")
    val backupSchedule: String? = null,
    @SerialName("backupsToKeep")
    val backupsToKeep: Int? = null,
    @SerialName("maxBackupSize")
    val maxBackupSize: Int? = null,
    @SerialName("loggerDailyLogsToKeep")
    val loggerDailyLogsToKeep: Int? = null,
    @SerialName("loggerScannerLogsToKeep")
    val loggerScannerLogsToKeep: Int? = null,
    @SerialName("homeBookshelfView")
    val homeBookshelfView: Int? = null,
    @SerialName("bookshelfView")
    val bookshelfView: Int? = null,
    @SerialName("sortingIgnorePrefix")
    val sortingIgnorePrefix: Boolean? = null,
    @SerialName("sortingPrefixes")
    val sortingPrefixes: List<String>? = null,
    @SerialName("chromecastEnabled")
    val chromecastEnabled: Boolean? = null,
    @SerialName("dateFormat")
    val dateFormat: String? = null,
    @SerialName("language")
    val language: String? = null,
    @SerialName("logLevel")
    val logLevel: Int? = null,
    @SerialName("version")
    val version: String? = null
)

@Serializable
data class AuthorizeResponse(
    @SerialName("user")
    val user: AudiobookshelfUser,
    @SerialName("userDefaultLibraryId")
    val userDefaultLibraryId: String? = null
)
