package com.makd.afinity.data.repository

import com.makd.afinity.data.manager.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

class NoActiveSessionException : Exception("No active Jellyfin session")

@Singleton
class JellyfinApiInvoker @Inject constructor(private val sessionManager: SessionManager) {

    suspend fun <T> apiResult(
        block: suspend (apiClient: ApiClient, userId: UUID) -> T
    ): Result<T> =
        withContext(Dispatchers.IO) {
            val apiClient =
                sessionManager.getCurrentApiClient()
                    ?: return@withContext Result.failure(NoActiveSessionException())
            val userId =
                sessionManager.currentSession.value?.userId
                    ?: return@withContext Result.failure(NoActiveSessionException())
            try {
                Result.success(block(apiClient, userId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun <T> apiCall(
        default: T,
        errorMessage: String,
        block: suspend (apiClient: ApiClient, userId: UUID) -> T,
    ): T =
        apiResult(block).getOrElse { e ->
            if (e !is NoActiveSessionException) Timber.e(e, errorMessage)
            default
        }
}