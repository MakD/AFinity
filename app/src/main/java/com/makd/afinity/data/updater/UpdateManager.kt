package com.makd.afinity.data.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.makd.afinity.BuildConfig
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.updater.models.GitHubRelease
import com.makd.afinity.data.updater.models.UpdateState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class UpdateManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val gitHubApiService: GitHubApiService,
    private val preferencesRepository: PreferencesRepository,
) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var currentDownloadId: Long? = null
    private var currentRelease: GitHubRelease? = null
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var progressJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val downloadReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                Timber.d(
                    "BroadcastReceiver.onReceive called! Download ID: $id, currentDownloadId: $currentDownloadId"
                )
                if (id == currentDownloadId) {
                    Timber.d("IDs match, calling handleDownloadComplete")
                    handleDownloadComplete(id)
                } else {
                    Timber.w(
                        "Received download complete for different ID: $id vs $currentDownloadId"
                    )
                }
            }
        }

    init {
        context.registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED,
        )
        Timber.d("UpdateManager initialized, download receiver registered")
    }

    suspend fun checkForUpdates(): GitHubRelease? {
        _updateState.value = UpdateState.Checking

        val result = gitHubApiService.getLatestRelease()

        return result.fold(
            onSuccess = { release ->
                if (isNewerVersion(release.tagName)) {
                    currentRelease = release
                    val existingFile = getDownloadedApkFile(release)
                    if (existingFile != null) {
                        Timber.d("Update already downloaded: ${existingFile.absolutePath}")
                        _updateState.value = UpdateState.Downloaded(existingFile, release)
                    } else {
                        _updateState.value = UpdateState.Available(release)
                    }
                    preferencesRepository.setLastUpdateCheck(System.currentTimeMillis())
                    release
                } else {
                    _updateState.value = UpdateState.UpToDate
                    preferencesRepository.setLastUpdateCheck(System.currentTimeMillis())
                    null
                }
            },
            onFailure = { error ->
                Timber.e(error, "Failed to check for updates")
                _updateState.value = UpdateState.Error(error.message ?: "Unknown error")
                null
            },
        )
    }

    fun downloadUpdate(release: GitHubRelease) {
        currentRelease = release
        val existingFile = getDownloadedApkFile(release)
        if (existingFile != null) {
            Timber.d("Found existing file, skipping download: ${existingFile.absolutePath}")
            _updateState.value = UpdateState.Downloaded(existingFile, release)
        }

        val currentAbi = getCurrentAbi()
        val apkAsset =
            release.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk") && asset.name.contains(currentAbi)
            }
                ?: release.assets.firstOrNull { asset ->
                    asset.name.endsWith(".apk") && asset.name.contains("arm64-v8a")
                }
                ?: release.assets.firstOrNull { it.name.endsWith(".apk") }

        if (apkAsset == null) {
            _updateState.value = UpdateState.Error("No compatible APK found")
            return
        }

        try {
            val request =
                DownloadManager.Request(Uri.parse(apkAsset.downloadUrl))
                    .setTitle("AFinity Update")
                    .setDescription("Downloading version ${release.tagName}")
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        apkAsset.name,
                    )
                    .setMimeType("application/vnd.android.package-archive")

            currentDownloadId = downloadManager.enqueue(request)
            _updateState.value = UpdateState.Downloading(0)

            Timber.d("Started download: $currentDownloadId")

            trackDownloadProgress()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start download")
            _updateState.value = UpdateState.Error("Failed to start download: ${e.message}")
        }
    }

    private fun trackDownloadProgress() {
        progressJob?.cancel()
        progressJob =
            coroutineScope.launch {
                while (isActive && currentDownloadId != null) {
                    val query = DownloadManager.Query().setFilterById(currentDownloadId!!)
                    val cursor = downloadManager.query(query)

                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIndex)

                        val bytesDownloadedIndex =
                            cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalBytesIndex =
                            cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                        val totalBytes = cursor.getLong(totalBytesIndex)

                        Timber.d("Download status: $status, bytes: $bytesDownloaded / $totalBytes")

                        when (status) {
                            DownloadManager.STATUS_RUNNING -> {
                                if (totalBytes > 0) {
                                    val progress =
                                        ((bytesDownloaded * 100) / totalBytes)
                                            .toInt()
                                            .coerceIn(0, 99)
                                    _updateState.value = UpdateState.Downloading(progress)
                                    Timber.d("Download progress: $progress%")
                                }
                            }

                            DownloadManager.STATUS_SUCCESSFUL -> {
                                Timber.d("Download successful, stopping progress tracking")
                                cursor.close()
                                delay(1000)
                                if (_updateState.value is UpdateState.Downloading) {
                                    Timber.w(
                                        "Broadcast receiver didn't fire, handling completion manually"
                                    )
                                    handleDownloadComplete(currentDownloadId!!)
                                }
                                return@launch
                            }

                            DownloadManager.STATUS_FAILED -> {
                                val reasonIndex =
                                    cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = cursor.getInt(reasonIndex)
                                Timber.e("Download failed with reason: $reason")
                                _updateState.value = UpdateState.Error("Download failed: $reason")
                                cursor.close()
                                return@launch
                            }
                        }
                    } else {
                        cursor.close()
                        return@launch
                    }
                    cursor.close()
                    delay(500)
                }
            }
    }

    fun installUpdate(file: File) {
        try {
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val uri =
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        setDataAndType(
                            Uri.fromFile(file),
                            "application/vnd.android.package-archive",
                        )
                    }
                }

            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to install update")
            _updateState.value = UpdateState.Error("Failed to install update: ${e.message}")
        }
    }

    private fun handleDownloadComplete(downloadId: Long) {
        Timber.d("handleDownloadComplete called for ID: $downloadId")
        progressJob?.cancel()

        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val uriString = cursor.getString(uriIndex)

                    Timber.d("Download URI: $uriString")

                    if (uriString != null) {
                        val file =
                            if (uriString.startsWith("file://")) {
                                File(Uri.parse(uriString).path ?: "")
                            } else {
                                val localFileIndex =
                                    cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                                if (localFileIndex >= 0) {
                                    val localFile = cursor.getString(localFileIndex)
                                    if (localFile != null) File(localFile) else null
                                } else {
                                    null
                                }
                            }

                        if (file?.exists() == true) {
                            _updateState.value = UpdateState.Downloaded(file, currentRelease!!)
                            Timber.d("Download completed: ${file.absolutePath}")
                        } else {
                            Timber.e("File not found at: ${file?.absolutePath}")
                            _updateState.value = UpdateState.Error("Downloaded file not found")
                        }
                    } else {
                        _updateState.value = UpdateState.Error("Download URI is null")
                    }
                }

                DownloadManager.STATUS_FAILED -> {
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonIndex)
                    _updateState.value = UpdateState.Error("Download failed with reason: $reason")
                    Timber.e("Download failed with reason: $reason")
                }
            }
        } else {
            Timber.e("Download cursor is empty")
            _updateState.value = UpdateState.Error("Failed to query download status")
        }
        cursor.close()
        currentDownloadId = null
    }

    private fun getDownloadedApkFile(release: GitHubRelease): File? {
        val currentAbi = getCurrentAbi()
        val apkAsset =
            release.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk") && asset.name.contains(currentAbi)
            }
                ?: release.assets.firstOrNull { asset ->
                    asset.name.endsWith(".apk") && asset.name.contains("arm64-v8a")
                }
                ?: release.assets.firstOrNull { it.name.endsWith(".apk") }

        if (apkAsset == null) return null

        val downloadDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseFileName = apkAsset.name.removeSuffix(".apk")

        val allFiles = downloadDir.listFiles() ?: return null

        val matchingFiles =
            allFiles
                .filter { file ->
                    file.name == apkAsset.name ||
                        file.name.matches(Regex("$baseFileName(-\\d+)?\\.apk"))
                }
                .sortedByDescending { it.lastModified() }

        return matchingFiles.firstOrNull()?.also { Timber.d("Found existing download: ${it.name}") }
    }

    private fun isNewerVersion(remoteVersion: String): Boolean {
        val currentVersion = BuildConfig.VERSION_NAME
        return try {
            val remote = parseVersion(remoteVersion)
            val current = parseVersion(currentVersion)
            remote > current
        } catch (e: Exception) {
            Timber.e(e, "Failed to compare versions: $currentVersion vs $remoteVersion")
            false
        }
    }

    private fun parseVersion(version: String): Int {
        val cleanVersion = version.removePrefix("v").split("-")[0]
        return cleanVersion
            .split(".")
            .take(3)
            .map { it.toIntOrNull() ?: 0 }
            .fold(0) { acc, num -> acc * 1000 + num }
    }

    private fun getCurrentAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
        currentRelease = null
    }

    fun cleanup() {
        progressJob?.cancel()
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unregister download receiver")
        }
    }
}
