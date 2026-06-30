package com.makd.afinity.data.updater

import com.makd.afinity.data.updater.models.GitHubRelease
import com.makd.afinity.di.GitHubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubApiService
@Inject
constructor(@param:GitHubClient private val okHttpClient: OkHttpClient) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val GITHUB_LATEST_URL =
            "https://api.github.com/repos/MakD/AFinity/releases/latest"
        private const val GITHUB_RELEASES_URL =
            "https://api.github.com/repos/MakD/AFinity/releases"
        private const val USER_AGENT = "AFinity-Android-App"
    }

    suspend fun getLatestRelease(): Result<GitHubRelease> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(GITHUB_LATEST_URL)
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

                val body =
                    response.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response body"))

                val release = json.decodeFromString<GitHubRelease>(body)

                Timber.d("Fetched latest stable release: ${release.tagName}")
                Result.success(release)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching latest release")
                Result.failure(e)
            }
        }

    suspend fun getLatestPrereleaseRelease(): Result<GitHubRelease> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(GITHUB_RELEASES_URL)
                        .addHeader("Accept", "application/vnd.github.v3+json")
                        .addHeader("User-Agent", USER_AGENT)
                        .build()

                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Timber.e("GitHub API request failed: ${response.code}")
                    return@withContext Result.failure(
                        Exception("Failed to fetch releases: HTTP ${response.code}")
                    )
                }

                val body =
                    response.body?.string()
                        ?: return@withContext Result.failure(Exception("Empty response body"))

                val releases = json.decodeFromString<List<GitHubRelease>>(body)
                val latest = releases.firstOrNull { it.prerelease && !it.draft }
                    ?: return@withContext Result.failure(Exception("No nightly releases found"))

                Timber.d("Fetched latest nightly release: ${latest.tagName}")
                Result.success(latest)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching nightly releases")
                Result.failure(e)
            }
        }
}
