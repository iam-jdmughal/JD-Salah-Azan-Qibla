package com.jdsalah.azanqibla

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jdsalah.azanqibla.azan.AzanNotificationManager
import com.jdsalah.azanqibla.azan.AzanSettingsManager

/**
 * Receives BOOT_COMPLETED broadcast to reschedule all prayer alarms
 * and prayer log reminders.
 * All AlarmManager alarms are lost when the device restarts, so this
 * receiver is critical for ensuring notifications survive reboots.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("AlarmReceiver", "Device booted - rescheduling all alarms")
            
            // Reschedule Azan prayer notifications
            val settings = AzanSettingsManager(context).getSettings()
            AzanNotificationManager(context).scheduleAllPrayerNotifications(settings)
            
            // Reschedule prayer log reminders
            val storage = StorageManager(context)
            storage.rescheduleAllReminders()
        }
    }
}
