package com.makd.afinity.data.repository.server

import com.makd.afinity.data.models.server.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ServerRepository {

    val currentBaseUrl: StateFlow<String>
    val isConnected: StateFlow<Boolean>
    val currentServer: StateFlow<Server?>

    suspend fun refreshServerInfo()

    fun getBaseUrl(): String

    suspend fun setBaseUrl(baseUrl: String)

    suspend fun discoverServers(): List<Server>

    fun discoverServersFlow(): Flow<List<Server>>

    suspend fun testServerConnection(
        serverAddress: String
    ): JellyfinServerRepository.ServerConnectionResult

    suspend fun getServerInfo(): Server?

    fun isConnectedToServer(): Boolean

    fun getCurrentServer(): Server?

    fun disconnect()

    fun buildImageUrl(
        itemId: String,
        imageType: String,
        imageIndex: Int = 0,
        tag: String? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        quality: Int? = null,
    ): String

    fun buildStreamUrl(
        itemId: String,
        mediaSourceId: String,
        maxBitrate: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        videoStreamIndex: Int? = null,
        accessToken: String? = null,
    ): String
}
