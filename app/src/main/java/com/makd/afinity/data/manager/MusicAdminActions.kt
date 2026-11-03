package com.makd.afinity.data.manager

import com.makd.afinity.data.models.admin.DeleteSummary
import com.makd.afinity.data.repository.admin.AdminRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class MusicAdminActions
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val adminRepository: AdminRepository,
) {
    private val deletable = ConcurrentHashMap.newKeySet<String>()

    val isAdmin: Flow<Boolean> =
        sessionManager.currentSession.map { it?.isAdmin == true }.distinctUntilChanged()

    suspend fun canDelete(itemId: UUID): Boolean {
        val session = sessionManager.currentSession.value ?: return false
        if (session.isAdmin != true && session.canDeleteMedia != true) return false
        val key = "${session.userId}|$itemId"
        if (key in deletable) return true
        return adminRepository.canDeleteItem(itemId.toString()).also { allowed ->
            if (allowed) deletable.add(key)
        }
    }

    suspend fun deleteSummary(itemId: UUID): DeleteSummary? =
        adminRepository.getDeleteSummary(itemId.toString())

    suspend fun delete(itemId: UUID): Result<Unit> =
        adminRepository.deleteItem(itemId.toString()).onSuccess {
            deletable.removeIf { it.endsWith("|$itemId") }
        }
}
