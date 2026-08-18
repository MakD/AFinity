package com.makd.afinity.data.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.makd.afinity.MainActivity
import com.makd.afinity.R
import com.makd.afinity.data.workers.DownloadActionReceiver
import com.makd.afinity.util.formatFileSize
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotificationManager
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    companion object {
        const val GROUP_ACTIVE = "com.makd.afinity.DOWNLOADS_ACTIVE"
        const val GROUP_FINISHED = "com.makd.afinity.DOWNLOADS_FINISHED"
        private const val CHANNEL_ID = "download_channel"
        private const val SUMMARY_ACTIVE_ID = 90001
        private const val SUMMARY_FINISHED_ID = 90002
        private const val CONTENT_INTENT_REQUEST_CODE = 90000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun pauseActionIntent(downloadId: UUID): PendingIntent =
        actionIntent(DownloadActionReceiver.ACTION_PAUSE, downloadId)

    fun cancelActionIntent(downloadId: UUID): PendingIntent =
        actionIntent(DownloadActionReceiver.ACTION_CANCEL, downloadId)

    fun absCancelActionIntent(downloadId: UUID): PendingIntent =
        actionIntent(DownloadActionReceiver.ACTION_ABS_CANCEL, downloadId)

    private fun actionIntent(action: String, downloadId: UUID): PendingIntent {
        val intent =
            Intent(context, DownloadActionReceiver::class.java).apply {
                this.action = action
                putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId.toString())
            }
        return PendingIntent.getBroadcast(
            context,
            "$action$downloadId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun downloadsContentIntent(): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(PendingNavigationManager.EXTRA_OPEN_DOWNLOADS, true)
            }
        return PendingIntent.getActivity(
            context,
            CONTENT_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun postActiveSummary() {
        ensureChannel()
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(context.getString(R.string.notif_downloads_in_progress))
                .setGroup(GROUP_ACTIVE)
                .setGroupSummary(true)
                .setSilent(true)
                .setContentIntent(downloadsContentIntent())
                .build()
        safeNotify(SUMMARY_ACTIVE_ID, notification)
    }

    fun progressNotificationId(downloadId: UUID): Int = downloadId.hashCode()

    fun cancelProgress(downloadId: UUID) {
        val id = progressNotificationId(downloadId)
        try {
            notificationManager.cancel(id)
        } catch (e: Exception) {
            Timber.w(e, "Failed to cancel download progress notification")
        }
        cleanupActiveSummary(id)
    }

    fun cleanupActiveSummary(ownNotificationId: Int) {
        try {
            val remaining =
                notificationManager.activeNotifications.count {
                    it.notification.group == GROUP_ACTIVE &&
                        it.id != SUMMARY_ACTIVE_ID &&
                        it.id != ownNotificationId
                }
            if (remaining == 0) notificationManager.cancel(SUMMARY_ACTIVE_ID)
        } catch (e: Exception) {
            Timber.w(e, "Failed to clean up download summary notification")
        }
    }

    fun postCompleted(
        notificationId: Int,
        title: String,
        subText: String?,
        largeIcon: Bitmap?,
        sizeBytes: Long,
    ) {
        ensureChannel()
        val completeText =
            if (sizeBytes > 0)
                context.getString(
                    R.string.notif_download_complete_size_fmt,
                    formatFileSize(context, sizeBytes),
                )
            else context.getString(R.string.notif_download_complete)
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(completeText)
                .apply {
                    if (!subText.isNullOrBlank()) setSubText(subText)
                    if (largeIcon != null) setLargeIcon(largeIcon)
                }
                .setGroup(GROUP_FINISHED)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(downloadsContentIntent())
                .build()
        safeNotify(notificationId, notification)
        postFinishedSummary()
    }

    fun postFailed(notificationId: Int, title: String, subText: String?, error: String?) {
        ensureChannel()
        val failedText =
            error?.takeIf { it.isNotBlank() }?.let {
                context.getString(R.string.notif_download_failed_reason_fmt, it)
            } ?: context.getString(R.string.notif_download_failed)
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(failedText)
                .apply { if (!subText.isNullOrBlank()) setSubText(subText) }
                .setGroup(GROUP_FINISHED)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(downloadsContentIntent())
                .build()
        safeNotify(notificationId, notification)
        postFinishedSummary()
    }

    private fun postFinishedSummary() {
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(context.getString(R.string.notif_downloads_finished))
                .setGroup(GROUP_FINISHED)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(downloadsContentIntent())
                .build()
        safeNotify(SUMMARY_FINISHED_ID, notification)
    }

    private fun ensureChannel() {
        val channel =
            NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply { description = context.getString(R.string.notif_channel_downloads_desc) }
        notificationManager.createNotificationChannel(channel)
    }

    fun notify(id: Int, notification: android.app.Notification) {
        safeNotify(id, notification)
    }

    private fun safeNotify(id: Int, notification: android.app.Notification) {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return
        try {
            compat.notify(id, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission missing, skipping download notification")
        }
    }
}
