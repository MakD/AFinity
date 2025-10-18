package com.makd.afinity.data.updater

import com.makd.afinity.data.updater.models.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubApiService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/MakD/AFinity/releases/latest"
        private const val USER_AGENT = "AFinity-Android-App"
    }

    suspend fun getLatestRelease(): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", USER_AGENT)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("GitHub API request failed: ${response.code}")
                return@withContext Result.failure(
                    Exception("Failed to fetch release: HTTP ${response.code}")
                )
            }

            val body = response.body?.string() ?: return@withContext Result.failure(
                Exception("Empty response body")
            )

            val release = json.decodeFromString<GitHubRelease>(body)

            Timber.d("Fetched latest release: ${release.tagName}")
            Result.success(release)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching latest release")
            Result.failure(e)
        }
    }
}