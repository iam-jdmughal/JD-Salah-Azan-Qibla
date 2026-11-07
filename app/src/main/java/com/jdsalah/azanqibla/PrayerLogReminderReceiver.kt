package com.jdsalah.azanqibla

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Handles daily prayer log reminder notifications.
 * When the alarm fires it:
 * 1. Shows a simple pop-up notification reminding the user to log prayers.
 * 2. Reschedules itself for the next day so it keeps repeating daily forever.
 */
class PrayerLogReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "prayer_log_reminders"
        const val EXTRA_REMINDER_TIME = "reminder_time"
        private const val TAG = "PrayerLogReminder"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val timeStr = intent.getStringExtra(EXTRA_REMINDER_TIME) ?: return
        val storage = StorageManager(context)
        if (!storage.isReminderEnabled(timeStr)) {
            Log.d(TAG, "Prayer log reminder $timeStr is disabled, skipping")
            return
        }

        Log.d(TAG, "Prayer log reminder fired for time: $timeStr")
        showNotification(context, timeStr)

        // Reschedule for next day
        storage.scheduleReminderAlarm(timeStr)
    }

    private fun showNotification(context: Context, timeStr: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prayer Log Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily reminders to log your prayers"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, timeStr.hashCode() + 5000, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(android.graphics.Color.BLACK)
            .setContentTitle("🕌 Salah Reminder")
            .setContentText("Have you logged your prayers today?")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Have you logged your prayers today?"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(timeStr.hashCode() + 2000, notification)
    }
}
