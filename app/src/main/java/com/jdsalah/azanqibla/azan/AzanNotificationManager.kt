package com.jdsalah.azanqibla.azan

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jdsalah.azanqibla.MainActivity
import com.jdsalah.azanqibla.R
import java.util.*

class AzanNotificationManager(private val context: Context) {
    
    companion object {
        const val FAJR_CHANNEL_ID_BASE = "azan_fajr_notifications"
        const val GENERAL_CHANNEL_ID_BASE = "azan_general_notifications"
        const val CHANNEL_NAME = "Prayer Time Notifications"
        const val NOTIFICATION_ID_BASE = 1000
        const val DAILY_RESCHEDULE_REQUEST_CODE = 9000
        private const val TAG = "AzanNotificationMgr"

        fun getChannelId(settings: AzanSettings, isFajr: Boolean): String {
            val sound = if (isFajr) settings.selectedFajrAdhanSound else settings.selectedAdhanSound
            val base = if (isFajr) FAJR_CHANNEL_ID_BASE else GENERAL_CHANNEL_ID_BASE
            return "${base}_${sound.name}"
        }

        fun getSoundUri(context: Context, settings: AzanSettings, isFajr: Boolean): Uri {
            val sound = if (isFajr) settings.selectedFajrAdhanSound else settings.selectedAdhanSound
            return when (sound) {
                AdhanSound.AZAN_1 -> Uri.parse("android.resource://${context.packageName}/${R.raw.azan_1}")
                AdhanSound.AZAN_2 -> Uri.parse("android.resource://${context.packageName}/${R.raw.azan_2}")
                AdhanSound.AZAN_3 -> Uri.parse("android.resource://${context.packageName}/${R.raw.azan_3}")
                AdhanSound.CUSTOM -> {
                    val uriStr = if (isFajr) settings.customFajrAdhanUri else settings.customAdhanUri
                    if (uriStr != null) Uri.parse(uriStr) else Uri.parse("android.resource://${context.packageName}/${R.raw.azan_1}")
                }
            }
        }
    }
    
    private fun createNotificationChannels(settings: AzanSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Create General Channel (SILENT - Sound played by Service)
            val generalId = AzanNotificationManager.getChannelId(settings, false)
            val generalChannel = NotificationChannel(generalId, "Adhan Notifications", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "General prayer time notifications"
                enableVibration(true)
                setSound(null, null) // Silent, let service play sound
            }
            notificationManager.createNotificationChannel(generalChannel)
            
            // Create Fajr Channel (SILENT - Sound played by Service)
            val fajrId = AzanNotificationManager.getChannelId(settings, true)
            val fajrChannel = NotificationChannel(fajrId, "Fajr Adhan Notifications", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Fajr prayer time notifications"
                enableVibration(true)
                setSound(null, null) // Silent, let service play sound
            }
            notificationManager.createNotificationChannel(fajrChannel)
        }
    }
    
    fun scheduleAllPrayerNotifications(settings: AzanSettings) {
        createNotificationChannels(settings)
        if (!settings.enableNotifications) return
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // CRITICAL: Schedule the daily midnight reschedule alarm FIRST.
        // This ensures the chain never breaks, even if the receiver gets killed
        // before finishing the rest of the work below.
        scheduleDailyReschedule(alarmManager)
        
        val calculator = PrayerTimeCalculator(settings)
        val now = Date()
        // Add 60s buffer to prevent rescheduling a prayer that just fired
        // (AlarmManager can fire a few ms early, causing the same prayer to be rescheduled)
        val bufferTime = Date(now.time + 60_000L)
        
        // Schedule today's remaining prayers
        val todayPrayerTimes = calculator.calculatePrayerTimes() ?: return
        var scheduledCount = 0
        
        todayPrayerTimes.forEach { prayerTime ->
            val shouldNotify = when (prayerTime.prayer) {
                Prayer.FAJR -> settings.enableFajrNotification
                Prayer.DHUHR -> settings.enableDhuhrNotification
                Prayer.ASR -> settings.enableAsrNotification
                Prayer.MAGHRIB -> settings.enableMaghribNotification
                Prayer.ISHA -> settings.enableIshaNotification
                else -> false
            }
            
            if (shouldNotify && prayerTime.time.after(bufferTime)) {
                schedulePrayerNotification(prayerTime, alarmManager, 0)
                scheduledCount++
            }
        }
        
        // Also schedule tomorrow's prayers so they are always ready
        val tomorrow = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, 1)
        }.time
        val tomorrowPrayerTimes = calculator.calculatePrayerTimes(tomorrow)
        tomorrowPrayerTimes?.forEach { prayerTime ->
            val shouldNotify = when (prayerTime.prayer) {
                Prayer.FAJR -> settings.enableFajrNotification
                Prayer.DHUHR -> settings.enableDhuhrNotification
                Prayer.ASR -> settings.enableAsrNotification
                Prayer.MAGHRIB -> settings.enableMaghribNotification
                Prayer.ISHA -> settings.enableIshaNotification
                else -> false
            }
            
            if (shouldNotify && prayerTime.time.after(now)) {
                schedulePrayerNotification(prayerTime, alarmManager, 10) // offset request codes for tomorrow
                scheduledCount++
            }
        }
        
        Log.d(TAG, "Scheduled $scheduledCount prayer alarms (today + tomorrow)")
        
        // Schedule reminders for today
        scheduleReminders(settings, alarmManager)
    }
    
    /**
     * Schedules an alarm at midnight every day to reschedule prayer notifications.
     * This is the KEY mechanism that ensures azan keeps working even when the app is not opened.
     */
    fun scheduleDailyReschedule(alarmManager: AlarmManager) {
        // Calculate the next 12:05 AM
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If 12:05 AM has already passed today, schedule for tomorrow
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        val intent = Intent(context, DailyRescheduleReceiver::class.java).apply {
            action = "DAILY_RESCHEDULE_PRAYERS"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_RESCHEDULE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    midnight.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    midnight.timeInMillis,
                    pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                midnight.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                midnight.timeInMillis,
                pendingIntent
            )
        }
        
        Log.d(TAG, "Daily reschedule alarm set for ${midnight.time}")
    }
    
    private fun schedulePrayerNotification(prayerTime: PrayerTimeData, alarmManager: AlarmManager, dayOffset: Int = 0) {
        val intent = Intent(context, AzanNotificationReceiver::class.java).apply {
            action = "PRAYER_TIME_NOTIFICATION"
            putExtra("prayer_name", prayerTime.prayer.name)
            putExtra("prayer_time", prayerTime.time.time)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_BASE + prayerTime.prayer.ordinal + dayOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    prayerTime.time.time,
                    pendingIntent
                )
            } else {
                // Fallback for when exact alarm permission not granted
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    prayerTime.time.time,
                    pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                prayerTime.time.time,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                prayerTime.time.time,
                pendingIntent
            )
        }
        
        Log.d(TAG, "Scheduled ${prayerTime.prayer.name} at ${prayerTime.time}")
    }
    
    private fun scheduleReminders(settings: AzanSettings, alarmManager: AlarmManager) {
        val settingsManager = AzanSettingsManager(context)
        val reminders = settingsManager.getReminders()
        val calculator = PrayerTimeCalculator(settings)
        val prayerTimes = calculator.calculatePrayerTimes() ?: return
        
        reminders.filter { it.enabled }.forEach { reminder ->
            val prayerTime = prayerTimes.firstOrNull { it.prayer == reminder.prayer } ?: return@forEach
            val reminderTime = Date(prayerTime.time.time + (reminder.minutesOffset * 60 * 1000))
            
            if (reminderTime.after(Date())) {
                val intent = Intent(context, AzanNotificationReceiver::class.java).apply {
                    action = "PRAYER_REMINDER"
                    putExtra("prayer_name", reminder.prayer.name)
                    putExtra("reminder_label", reminder.label)
                    putExtra("reminder_time", reminderTime.time)
                    putExtra("minutes_offset", reminder.minutesOffset)
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    NOTIFICATION_ID_BASE + 100 + reminder.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            reminderTime.time,
                            pendingIntent
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime.time,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime.time,
                        pendingIntent
                    )
                }
            }
        }
    }
    
    fun cancelAllNotifications() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Cancel prayer notifications
        Prayer.values().forEach { prayer ->
            // Cancel today's alarm (offset 0)
            val intent = Intent(context, AzanNotificationReceiver::class.java)
            val pendingIntentToday = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID_BASE + prayer.ordinal,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntentToday)
            
            // Cancel tomorrow's alarm (offset 10)
            val pendingIntentTomorrow = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID_BASE + prayer.ordinal + 10,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntentTomorrow)
        }
    }
}

class AzanNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "PRAYER_TIME_NOTIFICATION" -> {
                val prayerName = intent.getStringExtra("prayer_name") ?: return
                val isFajr = prayerName.uppercase() == "FAJR"
                // Show "Jummah" instead of "Dhuhr" on Fridays
                val displayName = if (prayerName.uppercase() == "DHUHR") {
                    val cal = Calendar.getInstance()
                    if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) "Jummah" else "Dhuhr"
                } else {
                    PrayerTimeCalculator(AzanSettingsManager(context).getSettings()).getPrayerName(
                        Prayer.valueOf(prayerName.uppercase())
                    )
                }
                showPrayerNotification(context, displayName, isFajr)
            }
            "PRAYER_REMINDER" -> {
                val prayerName = intent.getStringExtra("prayer_name") ?: return
                val label = intent.getStringExtra("reminder_label") ?: ""
                val minutesOffset = intent.getIntExtra("minutes_offset", 0)
                showReminderNotification(context, prayerName, label, minutesOffset)
            }
        }
    }
    
    private fun showPrayerNotification(context: Context, prayerName: String, isFajr: Boolean) {
        val settings = AzanSettingsManager(context).getSettings()
        val soundUri = AzanNotificationManager.getSoundUri(context, settings, isFajr)
        
        // Start the Azan sound service which now handles the FULL notification and sound
        // This prevents the "double notification" issue
        AzanSoundService.startAzan(context, prayerName, soundUri)
        
        // Reschedule for next day
        AzanNotificationManager(context).scheduleAllPrayerNotifications(settings)
    }
    
    private fun showReminderNotification(context: Context, prayerName: String, label: String, minutesOffset: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Ensure reminder channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("azan_reminders", "Prayer Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val timeText = if (minutesOffset < 0) {
            "${-minutesOffset} minutes before $prayerName"
        } else {
            "$minutesOffset minutes after $prayerName"
        }
        
        val notification = NotificationCompat.Builder(context, "azan_reminders")
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.app_primary))
            .setContentTitle(if (label.isNotEmpty()) label else "Prayer Reminder")
            .setContentText(timeText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(
            AzanNotificationManager.NOTIFICATION_ID_BASE + 1000 + label.hashCode(),
            notification
        )
    }
}

// Receiver to stop Azan sound when user clicks "Yes, I'm going to pray Salah"
class StopAzanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "STOP_AZAN") {
            // Stop the Azan sound service
            AzanSoundService.stopAzan(context)
            
            // Cancel the notification
            val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
        }
    }
}

/**
 * Fires every day at ~12:05 AM to reschedule prayer alarms for the new day.
 * This is critical for ensuring Azan notifications continue working even if
 * the app hasn't been opened in days.
 *
 * Uses goAsync() to get up to 30 seconds of execution time instead of the
 * default 10 seconds, preventing Android from killing the receiver before
 * it finishes scheduling all alarms.
 */
class DailyRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("DailyReschedule", "Daily reschedule triggered at ${Date()}")
        
        val pendingResult = goAsync()
        try {
            val settings = AzanSettingsManager(context).getSettings()
            AzanNotificationManager(context).scheduleAllPrayerNotifications(settings)
        } catch (e: Exception) {
            Log.e("DailyReschedule", "Error rescheduling prayers", e)
            // Even if scheduling prayers fails, ensure the NEXT midnight alarm is set
            // so the chain never breaks
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                AzanNotificationManager(context).scheduleDailyReschedule(alarmManager)
            } catch (e2: Exception) {
                Log.e("DailyReschedule", "Failed to reschedule midnight alarm", e2)
            }
        } finally {
            pendingResult.finish()
        }
    }
}
