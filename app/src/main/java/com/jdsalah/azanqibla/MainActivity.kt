package com.jdsalah.azanqibla

import com.jdsalah.azanqibla.R
import androidx.core.content.edit

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.view.KeyEvent
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.jdsalah.azanqibla.ui.theme.*
import com.jdsalah.azanqibla.qibla.QiblaCompass
import com.jdsalah.azanqibla.qibla.bearingDifference
import com.jdsalah.azanqibla.qibla.hasLocationPermission
import com.jdsalah.azanqibla.qibla.rememberCompassAzimuth
import com.jdsalah.azanqibla.qibla.rememberLocationState
import com.jdsalah.azanqibla.qibla.rememberQiblaDirection
import java.util.*
import kotlin.math.absoluteValue

// --- Types & Constants ---
enum class Screen { Home, Progress, Azan, Qibla, Settings }
enum class Status { NONE, JAMAAH, ALONE, QAZA, MISSED }

val PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

// --- Storage Logic ---
class StorageManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("JdSalahPrefs", Context.MODE_PRIVATE)

    fun savePrayer(date: String, prayerName: String, status: Status) {
        prefs.edit { putString("prayer_${date}_${prayerName}", status.name) }
    }

    fun getPrayer(date: String, prayerName: String): Status {
        val statusName = prefs.getString("prayer_${date}_${prayerName}", Status.NONE.name)
        return try { Status.valueOf(statusName!!) } catch (_: Exception) { Status.NONE }
    }

    fun saveProfile(name: String, city: String) {
        prefs.edit { putString("user_name", name).putString("user_city", city) }
    }

    fun getProfile(): Map<String, String>? {
        val name = prefs.getString("user_name", null) ?: return null
        val city = prefs.getString("user_city", "Mecca") ?: "Mecca"
        return mapOf("name" to name, "city" to city)
    }

    fun getStats(): Map<String, Int> {
        val allEntries = prefs.all
        var prayed = 0
        var qaza = 0
        var missed = 0
        allEntries.forEach { (key, value) ->
            if (key.startsWith("prayer_")) {
                when (value as? String) {
                    Status.JAMAAH.name, Status.ALONE.name -> prayed++
                    Status.QAZA.name -> qaza++
                    Status.MISSED.name -> missed++
                }
            }
        }
        val total = prayed + qaza + missed
        val momentum = if (total > 0) (prayed * 100 / total) else 0
        return mapOf("prayed" to prayed, "qaza" to qaza, "missed" to missed, "momentum" to momentum)
    }

    fun saveReminder(time: String) {
        val reminders = getReminders().toMutableList()
        if (!reminders.contains(time)) {
            reminders.add(time)
            prefs.edit { putString("reminders", reminders.joinToString(",")) }
            scheduleReminderAlarm(time)
        }
    }

    /**
     * Schedules (or reschedules) a daily prayer log reminder alarm.
     * Uses PrayerLogReminderReceiver which shows a simple pop-up notification
     * and reschedules itself for the next day.
     */
    fun scheduleReminderAlarm(timeStr: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PrayerLogReminderReceiver::class.java).apply {
            action = "PRAYER_LOG_REMINDER"
            putExtra(PrayerLogReminderReceiver.EXTRA_REMINDER_TIME, timeStr)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timeStr.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Parse "05:00 AM" safely
        val parts = timeStr.trim().split(" ", ":").filter { it.isNotBlank() }
        if (parts.size < 2) return
        val h = parts[0].toIntOrNull() ?: return
        val m = parts[1].toIntOrNull() ?: return
        val isAm = parts.lastOrNull()?.equals("AM", ignoreCase = true) ?: true

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR, if (h == 12) 0 else h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.AM_PM, if (isAm) Calendar.AM else Calendar.PM)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    fun isReminderEnabled(time: String): Boolean {
        return prefs.getBoolean("reminder_enabled_$time", true)
    }

    fun setReminderEnabled(time: String, enabled: Boolean) {
        prefs.edit { putBoolean("reminder_enabled_$time", enabled) }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PrayerLogReminderReceiver::class.java).apply {
            action = "PRAYER_LOG_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            time.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (enabled) {
            scheduleReminderAlarm(time)
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    /**
     * Reschedules all saved reminder alarms.
     * Called after device boot to recover all lost alarms.
     */
    fun rescheduleAllReminders() {
        getReminders().forEach { time ->
            if (isReminderEnabled(time)) {
                scheduleReminderAlarm(time)
            }
        }
    }

    fun getReminders(): List<String> {
        val s = prefs.getString("reminders", "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split(",")
    }

    fun deleteReminder(time: String) {
        val reminders = getReminders().toMutableList()
        reminders.remove(time)
        prefs.edit { 
            putString("reminders", reminders.joinToString(","))
            remove("reminder_enabled_$time")
        }
        
        // Cancel the pending alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PrayerLogReminderReceiver::class.java).apply {
            action = "PRAYER_LOG_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            time.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun getDailyStatusForMonth(year: Int, month: Int): Map<Int, Map<String, Status>> {
        val monthData = mutableMapOf<Int, Map<String, Status>>()
        val allEntries = prefs.all
        val prefix = String.format(Locale.US, "prayer_%04d-%02d-", year, month + 1)
        
        allEntries.forEach { (key, value) ->
            if (key.startsWith(prefix)) {
                // key is like "prayer_2026-01-10_Fajr"
                val parts = key.split("_")
                val datePart = parts.getOrNull(1) ?: return@forEach
                val prayerName = parts.getOrNull(2) ?: return@forEach
                val day = datePart.substringAfterLast("-").toIntOrNull() ?: return@forEach
                
                val status = try { Status.valueOf(value as String) } catch(e: Exception) { Status.NONE }
                val dayMap = (monthData[day] ?: emptyMap()).toMutableMap()
                dayMap[prayerName] = status
                monthData[day] = dayMap
            }
        }
        return monthData
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        val azanPrayerName = mutableStateOf<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAzanIntent(intent)
        
        // Show over lockscreen and turn screen on for Azan
        enableAzanLockScreenStyle()

        enableEdgeToEdge()
        setContent {
            JdSalahTheme {
                AppEntry()
            }
        }
    }

    private fun enableAzanLockScreenStyle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAzanIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (azanPrayerName.value != null) {
                // Stop Azan if it's currently playing and showing on overlay
                azanSoundService_stopAzan(this)
                azanPrayerName.value = null
                return true // Consume the event so volume doesn't actually change
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkAzanIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("is_azan_alarm", false) == true) {
            azanPrayerName.value = intent.getStringExtra("prayer_name") ?: "Prayer"
        }
    }
}

// --- Shared Components ---
@Composable
fun IslamicPattern(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Float = 3f,
    patternSize: Float = 100f
) {
    Spacer(
        modifier = modifier.drawWithCache {
            val width = size.width
            val height = size.height
            
            val path = Path()
            
            // Draw repeating geometric pattern (Interlaced octagons/stars)
            var y = 0f
            while (y < height + patternSize) {
                var x = 0f
                while (x < width + patternSize) {
                    val cx = x
                    val cy = y
                    val r = patternSize * 0.4f
                    
                    // Square 1
                    path.moveTo(cx - r, cy - r)
                    path.lineTo(cx + r, cy - r)
                    path.lineTo(cx + r, cy + r)
                    path.lineTo(cx - r, cy + r)
                    path.close()
                    
                    // Diamond (Square 2 rotated 45 degrees)
                    val rd = r * 1.414f
                    path.moveTo(cx, cy - rd)
                    path.lineTo(cx + rd, cy)
                    path.lineTo(cx, cy + rd)
                    path.lineTo(cx - rd, cy)
                    path.close()
                    
                    // Connecting lines to neighbors
                    path.moveTo(cx + r, cy)
                    path.lineTo(cx + patternSize - r, cy)
                    
                    path.moveTo(cx, cy + r)
                    path.lineTo(cx, cy + patternSize - r)

                    x += patternSize
                }
                y += patternSize
            }
            
            val stroke = Stroke(width = strokeWidth)
            onDrawBehind {
                drawPath(
                    path = path,
                    color = color,
                    style = stroke
                )
            }
        }
    )
}

@Composable
fun IslamicLogo(modifier: Modifier = Modifier, color: Color = Color.White) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val logoResId = if (isDark) R.drawable.logo_vector_night else R.drawable.logo_vector
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = logoResId),
            contentDescription = "JD Salah, Azan Qibla Logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun BismillahCalligraphy(modifier: Modifier = Modifier, color: Color = Color.White) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.ic_bismillah),
            contentDescription = "Bismillah Calligraphy",
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(color)
        )
    }
}

@Composable
fun KaabaIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Make lines bold like the reference image
        val strokeWidth = w * 0.08f 
        val padding = w * 0.1f
        val cornerRadius = w * 0.15f

        // 1. Draw the outer shape (Outline)
        val outlinePath = Path().apply {
            // Start at top-left (below peak)
            moveTo(padding, h * 0.3f)
            // Go up to the peak (roof center)
            lineTo(w * 0.5f, h * 0.15f)
            // Go down to top-right
            lineTo(w - padding, h * 0.3f)
            // Right vertical line down to near bottom
            lineTo(w - padding, h - padding - cornerRadius)
            // Bottom-right rounded corner
            quadraticBezierTo(
                w - padding, 
                h - padding, 
                w - padding - cornerRadius, 
                h - padding
            )
            // Bottom horizontal line
            lineTo(padding + cornerRadius, h - padding)
            // Bottom-left rounded corner
            quadraticBezierTo(
                padding, 
                h - padding, 
                padding, 
                h - padding - cornerRadius
            )
            // Close back to top-left
            close()
        }

        drawPath(
            path = outlinePath,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        // 2. Draw the two chevron bands inside
        val bandsPath = Path().apply {
            // Top band
            moveTo(padding * 1.8f, h * 0.45f)
            lineTo(w * 0.5f, h * 0.33f)
            lineTo(w - padding * 1.8f, h * 0.45f)

            // Bottom band
            moveTo(padding * 1.8f, h * 0.58f)
            lineTo(w * 0.5f, h * 0.46f)
            lineTo(w - padding * 1.8f, h * 0.58f)
        }

        drawPath(
            path = bandsPath,
            color = color,
            style = Stroke(
                width = strokeWidth * 0.8f, // Slightly thinner bands
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}


@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .then(interactionModifier),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(0.dp)) {
            content()
        }
    }
}

// --- App Entry (Logic Root) ---
@Composable
private fun AppEntry() {
    val context = LocalContext.current
    val storage = remember { StorageManager(context) }
    var userProfile by remember { mutableStateOf(storage.getProfile()) }
    var isLoading by remember { mutableStateOf(true) }
    var isDarkTheme by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        kotlinx.coroutines.delay(1000)
        isLoading = false
    }

    JdSalahTheme(darkTheme = isDarkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IslamicLogo(
                            modifier = Modifier.size(180.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("JD Salah, Azan Qibla", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            } else if (userProfile == null) {
                OnboardingScreen { name, city ->
                    storage.saveProfile(name, city)
                    userProfile = mapOf("name" to name, "city" to city)
                }
            } else {
                MainDashboard(userProfile!!, isDarkTheme, storage, { isDarkTheme = !isDarkTheme }) { newProfile ->
                    userProfile = newProfile
                }
            }

            // Azan Alarm Overlay (Full Screen)
            val azanName = MainActivity.azanPrayerName.value
            if (azanName != null) {
                AzanAlarmOverlay(
                    prayerName = azanName,
                    onDismiss = {
                        azanSoundService_stopAzan(context)
                        MainActivity.azanPrayerName.value = null
                    }
                )
            }
        }
    }
}

// Helper to avoid name collision
private fun azanSoundService_stopAzan(context: Context) {
    com.jdsalah.azanqibla.azan.AzanSoundService.stopAzan(context)
}

@Composable
fun AzanAlarmOverlay(
    prayerName: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundDark)
            .clickable(enabled = false) {}, // Block clicks to bottom
        contentAlignment = Alignment.TopCenter
    ) {
        // Header with Logo and App Name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IslamicLogo(
                modifier = Modifier.size(38.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "JD Salah, Azan Qibla",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        // Decorative background
        IslamicPattern(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.1f)
                .scale(2f),
            color = MaterialTheme.colorScheme.primary
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // verticalArrangement = Arrangement.Center, // Remove this to allow explicit spacing
            modifier = Modifier.padding(32.dp).fillMaxHeight(), // Fill height to center vertically manually or via Arrangement
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Background Logo removed as requested
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = prayerName,
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                        fontSize = 48.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "It's time for $prayerName prayer",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(80.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(
                    "Stop, I'm going to Pray Salah",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: (String, String) -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Mecca") }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Procedural Islamic Geometric Pattern
        IslamicPattern(
            modifier = Modifier.fillMaxSize().alpha(0.05f),
            color = MaterialTheme.colorScheme.primary
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(60.dp))
                
                // Islamic decorative element - Mosque icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(0.1f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IslamicLogo(
                        modifier = Modifier.size(35.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Bismillah Calligraphy
                BismillahCalligraphy(
                    modifier = Modifier
                        .height(85.dp) // Increased size
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "JD Salah, Azan Qibla",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Your journey to consistency starts here",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(48.dp))

                AnimatedContent(targetState = step) { currentStep ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        when (currentStep) {
                            1 -> {
                                // Decorative line
                                Box(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(2.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "YOUR IDENTITY",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "What is your good name?",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                TextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    placeholder = {
                                        Text(
                                            "e.g. Muhammad, Fatima",
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(0.4f),
                                            RoundedCornerShape(16.dp)
                                        ),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(0.5f),
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            2 -> {
                                Box(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(2.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "بِسْمِ اللهِ",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        letterSpacing = 1.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Starting today...",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Your prayer journey starts from today, ${String.format(Locale.getDefault(), "%tB %te, %tY", Date(), Date(), Date())}.",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "May Allah ﷻ make it easy for you and grant you consistency.",
                                    color = MaterialTheme.colorScheme.primary.copy(0.9f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column {
                // Progress indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1, 2).forEach { i ->
                        Box(
                            modifier = Modifier
                                .height(3.dp)
                                .width(if (step == i) 32.dp else 12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (step == i)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                        )
                        if (i < 2) Spacer(modifier = Modifier.width(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Next button
                Button(
                    onClick = { if (step < 2) step++ else onComplete(name, city) },
                    enabled = (step == 1 && name.isNotEmpty()) || step == 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        if (step == 2) "Start Journey" else "Continue",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(user: Map<String, String>, isDarkTheme: Boolean, storage: StorageManager, onToggleTheme: () -> Unit, onProfileUpdate: (Map<String, String>) -> Unit) {
    var selectedTab by remember { mutableStateOf(Screen.Home) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IslamicLogo(
                            modifier = Modifier.size(34.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("JD Salah, Azan Qibla", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    }
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(if (isDarkTheme) Icons.Default.WbSunny else Icons.Default.NightsStay, contentDescription = "Toggle Theme", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = if (isDarkTheme) Color.Black else MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                listOf(
                    Triple(Screen.Home, Icons.Default.Home, "Home"),
                    Triple(Screen.Progress, Icons.Default.CheckCircle, "Progress"),
                    Triple(Screen.Azan, Icons.Default.Notifications, "Azan"),
                    Triple(Screen.Qibla, null, "Qibla"), // null = use custom mosque icon
                    Triple(Screen.Settings, Icons.Default.Settings, "Settings")
                ).forEach { (screen, icon, label) ->
                    val isSelected = selectedTab == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = screen },
                        icon = {
                            if (icon != null) {
                                Icon(icon, contentDescription = label)
                            } else {
                                // Custom Kaaba icon for Qibla
                                Box(modifier = Modifier.size(24.dp)) {
                                    KaabaIcon(
                                        modifier = Modifier.fillMaxSize(),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Crossfade(targetState = selectedTab) { screen ->
                when(screen) {
                    Screen.Home -> DailyScreen(storage)
                    Screen.Progress -> AnalyticsScreen(storage)
                    Screen.Azan -> AzanScreen()
                    Screen.Qibla -> QiblaFinder()
                    Screen.Settings -> SettingsScreen(user, storage) { newProfile ->
                        onProfileUpdate(newProfile)
                    }
                }
            }
        }
    }
}


// --- Home (Daily) ---
@Composable
fun DailyScreen(storage: StorageManager) {
    var date by remember { mutableStateOf(Calendar.getInstance()) }
    val dateStr = String.format(Locale.US, "%tF", date) // YYYY-MM-DD deterministic ASCII

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { date = (date.clone() as Calendar).apply { add(Calendar.DATE, -1) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Day")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())?.uppercase() ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(String.format(Locale.getDefault(), "%tB %te, %tY", date, date, date), style = MaterialTheme.typography.titleLarge)
            }
            IconButton(onClick = { date = (date.clone() as Calendar).apply { add(Calendar.DATE, 1) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Day")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val isFriday = date.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY

        PRAYERS.forEach { prayer ->
            val displayName = if (prayer == "Dhuhr" && isFriday) "Jummah" else prayer
            key(dateStr + prayer) {
                PrayerCardItem(displayName, initialStatus = storage.getPrayer(dateStr, prayer)) { newStatus ->
                    storage.savePrayer(dateStr, prayer, newStatus)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun PrayerCardItem(name: String, initialStatus: Status, onStatusChange: (Status) -> Unit) {
    var status by remember { mutableStateOf(initialStatus) }
    var isExpanded by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        val icon = when(name) {
                            "Fajr" -> Icons.Default.WbTwilight
                            "Dhuhr", "Jummah" -> Icons.Default.WbSunny
                            "Asr" -> Icons.Default.FilterDrama
                            "Maghrib" -> Icons.Default.WbCloudy
                            else -> Icons.Default.NightsStay
                        }
                        Icon(icon, contentDescription = "Prayer Icon", tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (status != Status.NONE) {
                    Box(modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(status.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.background).padding(4.dp)) {
                listOf(Status.JAMAAH to "PRAYED", Status.QAZA to "QAZA", Status.MISSED to "MISSED").forEach { (s, label) ->
                    val isActive = if(s == Status.JAMAAH) status == Status.JAMAAH || status == Status.ALONE else status == s
                    Box(
                        modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if(isActive) (if(s == Status.JAMAAH) APP_STATUS_GREEN else if(s == Status.QAZA) AMBER_QAZA else RED_MISSED) else Color.Transparent)
                            .clickable { 
                                val nextStatus = if(isActive) Status.NONE else s
                                status = nextStatus
                                onStatusChange(nextStatus)
                                isExpanded = (nextStatus == Status.JAMAAH)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if(isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded && (status == Status.JAMAAH || status == Status.ALONE)) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.background).padding(4.dp)) {
                        Box(modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if(status == Status.JAMAAH) APP_STATUS_GREEN else Color.Transparent).clickable { 
                            status = Status.JAMAAH
                            onStatusChange(Status.JAMAAH)
                        }, contentAlignment = Alignment.Center) {
                            Text("JAMA'AH", style = MaterialTheme.typography.labelSmall, color = if(status == Status.JAMAAH) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if(status == Status.ALONE) APP_STATUS_GREEN else Color.Transparent).clickable { 
                            status = Status.ALONE
                            onStatusChange(Status.ALONE)
                        }, contentAlignment = Alignment.Center) {
                            Text("ALONE", style = MaterialTheme.typography.labelSmall, color = if(status == Status.ALONE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// --- Analytics ---
@Composable
fun AnalyticsScreen(storage: StorageManager) {
    val stats = remember { storage.getStats() }
    var matrixDate by remember { mutableStateOf(Calendar.getInstance()) }
    val monthlyData = storage.getDailyStatusForMonth(matrixDate.get(Calendar.YEAR), matrixDate.get(Calendar.MONTH))

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Insights", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("Your Progress", style = MaterialTheme.typography.displayLarge, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Row(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MOMENTUM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("${stats["momentum"]}%", style = MaterialTheme.typography.displayLarge, fontSize = 48.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text("Based on your prayer journey", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(modifier = Modifier.size(80.dp).border(6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, contentDescription = "Momentum Star", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Prayed" to (stats["prayed"] ?: 0), "Qaza" to (stats["qaza"] ?: 0), "Missed" to (stats["missed"] ?: 0)).forEach { (label, count) ->
                GlassCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = if(label == "Missed") RED_MISSED else if(label == "Qaza") AMBER_QAZA else APP_STATUS_GREEN)
                        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text((matrixDate.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: "") + " " + matrixDate.get(Calendar.YEAR), style = MaterialTheme.typography.titleLarge)
                        Text("JOURNEY MATRIX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        IconButton(onClick = { matrixDate = (matrixDate.clone() as Calendar).apply { add(Calendar.MONTH, -1) } }) { 
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month") 
                        }
                        IconButton(onClick = { matrixDate = (matrixDate.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }) { 
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month") 
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                val daysInMonth = matrixDate.getActualMaximum(Calendar.DAY_OF_MONTH)
                val firstDayOfWeek = (matrixDate.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_WEEK) - 1
                
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(6) { week ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(7) { dayOfWeek ->
                                val day = week * 7 + dayOfWeek - firstDayOfWeek + 1
                                if (day in 1..daysInMonth) {
                                    val dayStatusMap = monthlyData[day] ?: emptyMap()
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                day.toString(), 
                                                style = MaterialTheme.typography.labelSmall, 
                                                fontSize = 10.sp, 
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(20.dp),
                                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                PRAYERS.forEach { prayerName ->
                                                    val status = dayStatusMap[prayerName] ?: Status.NONE
                                                    val barHeight = when(status) {
                                                        Status.JAMAAH, Status.ALONE -> 1f
                                                        Status.QAZA -> 0.6f
                                                        Status.MISSED -> 0.3f
                                                        else -> 0.1f
                                                    }
                                                    val color = when(status) {
                                                        Status.JAMAAH, Status.ALONE -> APP_STATUS_GREEN
                                                        Status.QAZA -> AMBER_QAZA
                                                        Status.MISSED -> RED_MISSED
                                                        else -> MaterialTheme.colorScheme.onSurface.copy(0.1f)
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight(barHeight)
                                                            .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                                                            .background(color)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

// --- Qibla Finder ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaFinder() {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val azimuthState = rememberCompassAzimuth()
    val locationState = rememberLocationState(hasLocationPermission)
    val location = locationState.value
    val qiblaDirectionState = rememberQiblaDirection(location)
    val qiblaDirection = qiblaDirectionState.value
    val azimuth = azimuthState.value

    val bearingDelta = if (azimuth != null && qiblaDirection != null) bearingDifference(azimuth, qiblaDirection) else null
    
    // Hysteresis & Debounce for alignment
    var isAligned by remember { mutableStateOf(false) }
    var lastVibratedTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(bearingDelta) {
        val delta = bearingDelta?.absoluteValue
        if (delta != null) {
            if (!isAligned && delta <= 3.0f) {
                isAligned = true
            } else if (isAligned && delta > 6.0f) {
                isAligned = false
            }
        } else {
            isAligned = false
        }
    }

    // Vibrate with 2-second cooldown to prevent jitter
    LaunchedEffect(isAligned) {
        val now = System.currentTimeMillis()
        if (isAligned && (now - lastVibratedTime >= 2000L)) {
            lastVibratedTime = now
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(150)
            }
        }
    }

    // Geodesic distance to Kaaba (21.422487, 39.826206)
    val distanceText = remember(location) {
        if (location != null) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                location.latitude,
                location.longitude,
                21.422487,
                39.826206,
                results
            )
            val distanceKm = results[0] / 1000f
            if (distanceKm < 100f) {
                String.format(Locale.getDefault(), "Distance to Makkah: %.1f km", distanceKm)
            } else {
                String.format(Locale.getDefault(), "Distance to Makkah: %,.0f km", distanceKm)
            }
        } else {
            "Identifying Makkah..."
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Qibla Finder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text("AL-MAKKAH", style = MaterialTheme.typography.titleLarge, fontSize = 24.sp, textAlign = TextAlign.Center)
        
        Spacer(modifier = Modifier.weight(1f))
        
        Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            if(isAligned) {
                Box(modifier = Modifier.fillMaxSize().background(AppPrimary.copy(alpha = 0.1f), CircleShape))
            }
            
            QiblaCompass(
                modifier = Modifier.fillMaxSize(),
                azimuthDegrees = azimuth,
                qiblaDirectionDegrees = qiblaDirection,
                indicatorTint = null,
                location = location,
                animationDurationMillis = 260,
                showInfoPanel = false
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if(location == null) "Locating..." else if(azimuth == null) "Pending" else if(isAligned) "You are facing Qibla" else String.format(Locale.getDefault(), "%.0f°", bearingDelta?.absoluteValue ?: 0f),
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center
                )
                Text(distanceText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                if (!hasLocationPermission) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text("Enable Qibla Sensors")
                    }
                }
            }
        }
    }
}

// --- Settings ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(user: Map<String, String>, storage: StorageManager, onProfileUpdate: (Map<String, String>) -> Unit) {
    val context = LocalContext.current
    val azanSettingsManager = remember { com.jdsalah.azanqibla.azan.AzanSettingsManager(context) }
    var azanSettings by remember { mutableStateOf(azanSettingsManager.getSettings()) }
    var showAdhanPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var reminders by remember { mutableStateOf(storage.getReminders()) }
    var showProfileEntry by remember { mutableStateOf(false) }
    
    var editName by remember { mutableStateOf(user["name"] ?: "") }
    var editCity by remember { mutableStateOf(user["city"] ?: "") }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Preferences", style = MaterialTheme.typography.labelSmall, color = AppPrimary)
        Text("Settings", style = MaterialTheme.typography.displayLarge, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(16.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(user["name"] ?: "Musafir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(user["city"] ?: "Mecca", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showProfileEntry = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Azan Notification Settings Section
        Text("AZAN NOTIFICATIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prayer Alert Sounds", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Configure Azan sound for prayer time notifications",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Adhan Sound
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { 
                        showAdhanPicker = true 
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Azan Sound", 
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(azanSettings.selectedAdhanSound.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text("Prayer Notifications", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                // Enable Notifications Toggle
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { 
                        azanSettings = azanSettings.copy(enableNotifications = !azanSettings.enableNotifications)
                        azanSettingsManager.saveSettings(azanSettings)
                        val nm = com.jdsalah.azanqibla.azan.AzanNotificationManager(context)
                        nm.scheduleAllPrayerNotifications(azanSettings)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Enable Notifications", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (azanSettings.enableNotifications) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (azanSettings.enableNotifications) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Enabled",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = "Disabled",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                if (azanSettings.enableNotifications) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Individual Prayer Toggles
                    NotificationToggleRow("Fajr", azanSettings.enableFajrNotification) { 
                        azanSettings = azanSettings.copy(enableFajrNotification = it)
                        azanSettingsManager.saveSettings(azanSettings)
                        val nm = com.jdsalah.azanqibla.azan.AzanNotificationManager(context)
                        nm.scheduleAllPrayerNotifications(azanSettings)
                    }
                    NotificationToggleRow("Dhuhr / Jummah", azanSettings.enableDhuhrNotification) { 
                        azanSettings = azanSettings.copy(enableDhuhrNotification = it)
                        azanSettingsManager.saveSettings(azanSettings)
                        val nm = com.jdsalah.azanqibla.azan.AzanNotificationManager(context)
                        nm.scheduleAllPrayerNotifications(azanSettings)
                    }
                    NotificationToggleRow("Asr", azanSettings.enableAsrNotification) { 
                        azanSettings = azanSettings.copy(enableAsrNotification = it)
                        azanSettingsManager.saveSettings(azanSettings)
                        val nm = com.jdsalah.azanqibla.azan.AzanNotificationManager(context)
                        nm.scheduleAllPrayerNotifications(azanSettings)
                    }
                    NotificationToggleRow("Maghrib", azanSettings.enableMaghribNotification) { 
                        azanSettings = azanSettings.copy(enableMaghribNotification = it)
                        azanSettingsManager.saveSettings(azanSettings)
                        val nm = com.jdsalah.azanqibla.azan.AzanNotificationManager(context)
                        nm.scheduleAllPrayerNotifications(azanSettings)
                    }
                    NotificationToggleRow("Isha", azanSettings.enableIshaNotification) { 
                        azanSettings = azanSettings.copy(enableIshaNotification = it)
                        azanSettingsManager.saveSettings(azanSettings)
                        val nm = com.jdsalah.azanqibla.azan.AzanNotificationManager(context)
                        nm.scheduleAllPrayerNotifications(azanSettings)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Reminders Section
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("REMINDERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("For Daily Prayer Logs to Get Consistent in Momentum 100%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                IconButton(onClick = { 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    showTimePicker = true 
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Notification", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        if (reminders.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsNone, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Please add reminders to get connected with Allah.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            reminders.forEach { time ->
                var isEnabled by remember(time) { mutableStateOf(storage.isReminderEnabled(time)) }
                key(time) {
                    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(
                                    time, 
                                    style = MaterialTheme.typography.titleLarge, 
                                    fontSize = 22.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = if(isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.38f)
                                )
                                Text(
                                    if (isEnabled) "Daily Salah Reminder" else "Reminder Paused", 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { 
                                val next = !isEnabled
                                isEnabled = next
                                storage.setReminderEnabled(time, next)
                            }) {
                                Icon(
                                    if(isEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, 
                                    contentDescription = if(isEnabled) "Active" else "Paused", 
                                    tint = if(isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                            IconButton(onClick = { 
                                storage.deleteReminder(time)
                                reminders = storage.getReminders()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        
        // Developer & Support Section
        Text("SUPPORT THE DEVELOPERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Animated Heart Support Card
        val infiniteTransition = rememberInfiniteTransition(label = "heartGlow")
        val heartAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "heartAlpha"
        )
        
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { /* Will add support options here */ }
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Support",
                    tint = Color(0xFFEC4899).copy(alpha = heartAlpha),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Support Our Mission",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Help us serve the Ummah better. Your support keeps this app free and adfree for everyone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Rate & Suggest
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(
                modifier = Modifier.weight(1f),
                onClick = { 
                    try {
                        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                        context.startActivity(marketIntent)
                    } catch (e: Exception) {
                        try {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                            context.startActivity(webIntent)
                        } catch (e2: Exception) {
                            Toast.makeText(context, "Unable to open Play Store", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Rate",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Rate Us",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "5 stars ⭐",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            GlassCard(
                modifier = Modifier.weight(1f),
                onClick = { 
                    // Open email for feedback/suggestions
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_SUBJECT, "Feedback for JD Salah, Azan Qibla")
                        putExtra(Intent.EXTRA_TEXT, "Assalamu Alaikum,\n\nI have a suggestion/feedback for JD Salah, Azan Qibla:\n\n")
                    }
                    try {
                        context.startActivity(Intent.createChooser(emailIntent, "Send Feedback"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = "Suggest",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Suggest",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Ideas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showProfileEntry) {
        AlertDialog(
            onDismissRequest = { showProfileEntry = false },
            confirmButton = { 
                TextButton(onClick = { 
                    storage.saveProfile(editName, editCity)
                    showProfileEntry = false
                    onProfileUpdate(mapOf("name" to editName, "city" to editCity))
                }) { Text("Save") } 
            },
            title = { Text("Update Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editCity, onValueChange = { editCity = it }, label = { Text("City") }, modifier = Modifier.fillMaxWidth())
                }
            }
        )
    }

    if (showTimePicker) {
        var hour by remember { mutableStateOf(5) }
        var minute by remember { mutableStateOf(0) }
        var isAm by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { 
                TextButton(onClick = { 
                    val timeStr = String.format(Locale.US, "%02d:%02d %s", hour, minute, if(isAm) "AM" else "PM")
                    storage.saveReminder(timeStr)
                    reminders = storage.getReminders()
                    showTimePicker = false 
                }) { Text("Save") } 
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            title = { Text("Set Reminder Time") },
            text = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    // Hour Column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { 
                            hour = if(hour == 12) 1 else hour + 1
                        }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                        
                        // Manual Hour Input
                        var hourText by remember { mutableStateOf(String.format("%02d", hour)) }
                        
                        // Sync display when hour changes from arrow buttons
                        LaunchedEffect(hour) {
                            val currentParsed = hourText.toIntOrNull()
                            if (currentParsed != hour) {
                                hourText = String.format("%02d", hour)
                            }
                        }
                        
                        BasicTextField(
                            value = hourText,
                            onValueChange = { newText: String ->
                                if (newText.isEmpty()) {
                                    hourText = ""
                                } else if (newText.length <= 2 && newText.all { it.isDigit() }) {
                                    hourText = newText
                                    val parsed = newText.toIntOrNull()
                                    if (parsed != null && parsed in 1..12) {
                                        hour = parsed
                                    }
                                }
                            },
                            textStyle = MaterialTheme.typography.displayLarge.copy(
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.width(60.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        
                        IconButton(onClick = { 
                            hour = if(hour == 1) 12 else hour - 1
                        }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                    }
                    
                    Text(":", style = MaterialTheme.typography.displayLarge)
                    
                    // Minute Column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { minute = (minute + 1) % 60 }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                        
                        // Manual Minute Input
                        var minuteText by remember { mutableStateOf(String.format("%02d", minute)) }
                        
                        // Sync display when minute changes from arrow buttons
                        LaunchedEffect(minute) {
                            val currentParsed = minuteText.toIntOrNull()
                            if (currentParsed != minute) {
                                minuteText = String.format("%02d", minute)
                            }
                        }
                        
                        BasicTextField(
                            value = minuteText,
                            onValueChange = { newText: String ->
                                if (newText.isEmpty()) {
                                    minuteText = ""
                                } else if (newText.length <= 2 && newText.all { it.isDigit() }) {
                                    minuteText = newText
                                    val parsed = newText.toIntOrNull()
                                    if (parsed != null && parsed in 0..59) {
                                        minute = parsed
                                    }
                                }
                            },
                            textStyle = MaterialTheme.typography.displayLarge.copy(
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.width(60.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        IconButton(onClick = { minute = (minute + 59) % 60 }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // AM/PM Column
                    Column(
                        modifier = Modifier
                            .height(120.dp)
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "AM", 
                            color = if(isAm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), 
                            modifier = Modifier.clickable { isAm = true }, 
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if(isAm) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "PM", 
                            color = if(!isAm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), 
                            modifier = Modifier.clickable { isAm = false }, 
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if(!isAm) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
    
    // Adhan Sound Picker Dialog
    if (showAdhanPicker) {
        AdhanSoundPickerDialog(
            initialSound = azanSettings.selectedAdhanSound,
            initialUri = azanSettings.customAdhanUri,
            isFajr = false,
            onDismiss = { showAdhanPicker = false },
            onSoundSelected = { sound, uri ->
                azanSettings = azanSettings.copy(selectedAdhanSound = sound, customAdhanUri = uri)
                azanSettingsManager.saveSettings(azanSettings)
                
                // Refresh notifications
                val nm = com.jdsalah.azanqibla.azan.AzanNotificationManager(context)
                nm.scheduleAllPrayerNotifications(azanSettings)
                
                showAdhanPicker = false
            }
        )
    }
}

// Helper composable for notification toggles
@Composable
fun NotificationToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label, 
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
            )
            if (checked) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Enabled",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    Icons.Default.Circle,
                    contentDescription = "Disabled",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
