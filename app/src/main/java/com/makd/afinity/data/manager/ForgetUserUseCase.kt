package com.makd.afinity.data.manager

import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForgetUserUseCase
@Inject
constructor(
    private val serverDatabaseDao: ServerDatabaseDao,
    private val downloadRepository: DownloadRepository,
    private val absDownloadRepository: AbsDownloadRepository,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val sessionManager: SessionManager,
) {

    suspend operator fun invoke(serverId: String, userId: UUID): Result<Unit> =
        withContext(Dispatchers.IO + NonCancellable) {
            val current = sessionManager.currentSession.value
            if (current?.serverId == serverId && current.userId == userId) {
                return@withContext Result.failure(
                    IllegalStateException("Cannot forget the account that is currently signed in")
                )
            }

            try {
                serverDatabaseDao.getDownloadsForUser(serverId, userId).forEach { download ->
                    downloadRepository
                        .deleteDownload(download.id)
                        .onFailure { Timber.w(it, "Failed to delete download ${download.id}") }
                }

                serverDatabaseDao.getAbsDownloadIdsForUser(serverId, userId.toString()).forEach {
                    id ->
                    absDownloadRepository
                        .deleteDownload(id)
                        .onFailure { Timber.w(it, "Failed to delete Audiobookshelf download $id") }
                }

                securePreferencesRepository.clearServerUserToken(serverId, userId)
                securePreferencesRepository.clearJellyseerrAuthForUser(serverId, userId)
                securePreferencesRepository.clearAudiobookshelfAuthForUser(serverId, userId)

                serverDatabaseDao.clearAllDataForUser(serverId, userId)
                Timber.i("Forgot account $userId on server $serverId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to forget account $userId on server $serverId")
                Result.failure(e)
            }
        }
}
