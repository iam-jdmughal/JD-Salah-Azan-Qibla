package com.jdsalah.azanqibla.azan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jdsalah.azanqibla.MainActivity
import com.jdsalah.azanqibla.R

class AzanSoundService : Service() {
    
    private var mediaPlayer: MediaPlayer? = null
    
    companion object {
        const val ACTION_START_AZAN = "START_AZAN"
        const val ACTION_STOP_AZAN = "STOP_AZAN"
        const val EXTRA_PRAYER_NAME = "PRAYER_NAME"
        const val EXTRA_SOUND_URI = "SOUND_URI"
        const val NOTIFICATION_ID = 9999
        
        fun startAzan(context: Context, prayerName: String, soundUri: Uri) {
            val intent = Intent(context, AzanSoundService::class.java).apply {
                action = ACTION_START_AZAN
                putExtra(EXTRA_PRAYER_NAME, prayerName)
                putExtra(EXTRA_SOUND_URI, soundUri.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopAzan(context: Context) {
            val intent = Intent(context, AzanSoundService::class.java).apply {
                action = ACTION_STOP_AZAN
            }
            context.startService(intent)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_AZAN -> {
                val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Prayer"
                val soundUriString = intent.getStringExtra(EXTRA_SOUND_URI)
                val soundUri = if (soundUriString != null) Uri.parse(soundUriString) else null
                
                startForeground(NOTIFICATION_ID, createAdvancedNotification(prayerName))
                
                if (soundUri != null) {
                    playAzan(soundUri)
                }
            }
            ACTION_STOP_AZAN -> {
                stopAzan()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    
    private fun createAdvancedNotification(prayerName: String): Notification {
        val channelId = "azan_sound_service_v3" // Bumped to force fresh channel with silent sound
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Azan Sound Service",
                NotificationManager.IMPORTANCE_HIGH // High importance for heads-up
            ).apply {
                description = "Playing Adhan Sound"
                setSound(null, null) // Silent channel, we play media manually
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            // Delete old cached channels that may have had sound enabled
            notificationManager.deleteNotificationChannel("azan_sound_service")
            notificationManager.deleteNotificationChannel("azan_sound_service_v2")
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("is_azan_alarm", true)
            putExtra("prayer_name", prayerName)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            prayerName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Stop Action Intent (points back to this service to stop it)
        val stopIntent = Intent(this, AzanSoundService::class.java).apply {
            action = ACTION_STOP_AZAN
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            prayerName.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Custom Message
        val customMessage = when (prayerName) {
            "Fajr" -> "Wake up for Fajr prayer. Start your day with Allah's blessings."
            "Dhuhr" -> "Time for Dhuhr prayer. Take a break and connect with Allah."
            "Jummah" -> "It's Jummah! Head to the Masjid for the blessed Friday prayer."
            "Asr" -> "Time for Asr prayer. Renew your faith in the afternoon."
            "Maghrib" -> "Time for Maghrib prayer. Thank Allah as the day ends."
            "Isha" -> "Time for Isha prayer. End your day in remembrance of Allah."
            else -> "It's time to pray $prayerName"
        }
        
        // Current Time
        val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        val currentTime = timeFormat.format(java.util.Date())
        
        // Custom Layout
        val customView = android.widget.RemoteViews(packageName, R.layout.notification_azan).apply {
            setTextViewText(R.id.notification_title, prayerName)
            setTextViewText(R.id.notification_time, currentTime)
            setTextViewText(R.id.notification_message, customMessage)
            setOnClickPendingIntent(R.id.dismiss_button, stopPendingIntent)
        }
        
        // Full-screen intent for lock screen overlay
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("is_azan_alarm", true)
            putExtra("prayer_name", prayerName)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            prayerName.hashCode() + 5000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.app_primary))
            .setContentTitle("Time for $prayerName Prayer")
            .setContentText(customMessage)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .build()
    }
    
    private fun playAzan(soundUri: Uri) {
        try {
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                if (soundUri.scheme == "android.resource") {
                    val resId = soundUri.lastPathSegment?.toIntOrNull()
                    if (resId != null && resId != 0) {
                        val afd = resources.openRawResourceFd(resId)
                        if (afd != null) {
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            afd.close()
                        } else {
                            setDataSource(applicationContext, soundUri)
                        }
                    } else {
                        setDataSource(applicationContext, soundUri)
                    }
                } else {
                    setDataSource(applicationContext, soundUri)
                }
                isLooping = false
                setOnCompletionListener {
                    stopAzan()
                    stopSelf()
                }
                setOnPreparedListener {
                    start()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }
    
    
    private fun stopAzan() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAzan()
    }
}
