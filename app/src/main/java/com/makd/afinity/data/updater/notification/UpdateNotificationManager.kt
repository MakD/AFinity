package com.makd.afinity.data.updater.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.makd.afinity.MainActivity
import com.makd.afinity.R
import com.makd.afinity.data.updater.models.GitHubRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateNotificationManager
@Inject
constructor(@ApplicationContext private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "app_updates"
        private const val CHANNEL_NAME = "App Updates"
        private const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                    .apply {
                        description = "Notifications for app updates"
                        enableLights(true)
                        enableVibration(true)
                    }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showUpdateAvailableNotification(release: GitHubRelease) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to_updates", true)
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Update Available")
                .setContentText("Version ${release.tagName} is now available")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "Version ${release.tagName} is now available. Tap to view details and download."
                        )
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelUpdateNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
