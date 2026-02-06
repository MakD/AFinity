package com.makd.afinity.data.repository.auth

import com.makd.afinity.data.models.auth.QuickConnectState
import com.makd.afinity.data.models.user.User
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.model.api.AuthenticationResult

interface AuthRepository {

    val currentUser: StateFlow<User?>
    val isAuthenticated: StateFlow<Boolean>

    suspend fun restoreAuthenticationState(): Boolean

    suspend fun hasValidSavedAuth(): Boolean

    suspend fun saveAuthenticationData(
        authResult: AuthenticationResult,
        serverUrl: String,
        username: String,
    )

    suspend fun clearAllAuthData()

    suspend fun authenticateByName(username: String, password: String): AuthResult

    suspend fun authenticateWithQuickConnect(secret: String): AuthResult

    suspend fun logout()

    suspend fun initiateQuickConnect(): QuickConnectState?

    suspend fun getQuickConnectState(secret: String): QuickConnectState?

    suspend fun getCurrentUser(): User?

    suspend fun getPublicUsers(): List<User>

    fun hasValidToken(): Boolean

    fun getAccessToken(): String?

    sealed class AuthResult {
        data class Success(val authResult: AuthenticationResult) : AuthResult()

        data class Error(val message: String) : AuthResult()
    }
}
