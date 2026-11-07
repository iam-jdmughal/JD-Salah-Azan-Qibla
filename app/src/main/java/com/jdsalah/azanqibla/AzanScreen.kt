package com.jdsalah.azanqibla

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.media.MediaPlayer
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import com.jdsalah.azanqibla.qibla.hasLocationPermission
import com.jdsalah.azanqibla.qibla.rememberLocationState
import com.jdsalah.azanqibla.azan.*
import com.jdsalah.azanqibla.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzanScreen() {
    val context = LocalContext.current
    val settingsManager = remember { AzanSettingsManager(context) }
    var settings by remember { mutableStateOf(settingsManager.getSettings()) }
    var showSettings by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableIntStateOf(0) }
    var showAdhanPicker by remember { mutableStateOf(false) }
    var soundPickerForFajr by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(Date()) }
    
    // Update time every minute
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000) // Update every second for real-time experience
        }
    }
    
    // Location permission
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    
    val locationState = rememberLocationState(hasLocationPermission)
    val location = locationState.value
    
    // Auto-update location if not set
    LaunchedEffect(location) {
        if (location != null && settings.location == null) {
            settings = settings.copy(
                location = LocationData(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            )
            settingsManager.saveSettings(settings)
        }
    }
    
    // Day tracker to only recompute astronomical prayer times when the calendar day or settings change
    val currentCalendarDay = remember(currentTime) {
        val cal = Calendar.getInstance().apply { time = currentTime }
        cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }
    
    val calculator = remember(settings) { PrayerTimeCalculator(settings) }
    val prayerTimes = remember(settings, currentCalendarDay) { calculator.calculatePrayerTimes() }
    val nextPrayer = remember(settings, currentTime.time / 60_000L) { calculator.getNextPrayer(currentTime) }
    val timeUntilNext = remember(settings, currentTime) { calculator.getTimeUntilNextPrayer(currentTime) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Prayer Times", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("AZAN", style = MaterialTheme.typography.titleLarge, fontSize = 24.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Location status
        if (settings.location == null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Location Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Enable location to calculate accurate prayer times",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Location")
                    }
                }
            }
        } else {
            // Next Prayer Card
            nextPrayer?.let { next ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "NEXT PRAYER",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    calculator.getPrayerName(next.prayer),
                                    style = MaterialTheme.typography.displayLarge,
                                    fontSize = 28.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            
                            timeUntilNext?.let { timeLeft ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "IN",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        calculator.formatTimeRemaining(timeLeft),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Prayer Times List
            prayerTimes?.let { times ->
                Text(
                    "TODAY'S PRAYER TIMES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                times.forEach { prayerTime ->
                    PrayerTimeCard(
                        prayerTime = prayerTime,
                        calculator = calculator,
                        isNext = prayerTime.prayer == nextPrayer?.prayer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        settingsInitialTab = 1 // Adjustments tab
                        showSettings = true 
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Adjust Times",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = { showMethodDialog = true }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Calculate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Method",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = { showAdhanPicker = true }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Azan Sound",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
    
    // Settings Dialog
    if (showSettings) {
        AzanSettingsDialog(
            settings = settings,
            initialTab = settingsInitialTab,
            onDismiss = { showSettings = false },
            onSave = { newSettings ->
                settings = newSettings
                settingsManager.saveSettings(newSettings)
                
                // Schedule notifications
                val notificationManager = AzanNotificationManager(context)
                notificationManager.scheduleAllPrayerNotifications(newSettings)
                
                showSettings = false
            }
        )
    }
    
    if (showMethodDialog) {
        BasicAlertDialog(
            onDismissRequest = { showMethodDialog = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Calculation Method",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showMethodDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    MethodSettings(settings) { newSettings ->
                        settings = newSettings
                        settingsManager.saveSettings(newSettings)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { showMethodDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
    
    // Adhan Sound Picker Dialog
    if (showAdhanPicker) {
        AdhanSoundPickerDialog(
            initialSound = if (soundPickerForFajr) settings.selectedFajrAdhanSound else settings.selectedAdhanSound,
            initialUri = if (soundPickerForFajr) settings.customFajrAdhanUri else settings.customAdhanUri,
            isFajr = soundPickerForFajr,
            onDismiss = { showAdhanPicker = false },
            onSoundSelected = { sound, uri ->
                if (soundPickerForFajr) {
                    settings = settings.copy(selectedFajrAdhanSound = sound, customFajrAdhanUri = uri)
                } else {
                    settings = settings.copy(selectedAdhanSound = sound, customAdhanUri = uri)
                }
                settingsManager.saveSettings(settings)
                
                // Refresh notifications
                val nm = AzanNotificationManager(context)
                nm.scheduleAllPrayerNotifications(settings)
                
                showAdhanPicker = false
            }
        )
    }
}

@Composable
fun PrayerTimeCard(
    prayerTime: PrayerTimeData,
    calculator: PrayerTimeCalculator,
    isNext: Boolean
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (prayerTime.prayer) {
                        Prayer.FAJR -> Icons.Default.WbTwilight
                        Prayer.SUNRISE -> Icons.Default.WbSunny
                        Prayer.DHUHR -> Icons.Default.WbSunny
                        Prayer.ASR -> Icons.Default.FilterDrama
                        Prayer.SUNSET -> Icons.Default.WbCloudy
                        Prayer.MAGHRIB -> Icons.Default.WbCloudy
                        Prayer.ISHA -> Icons.Default.NightsStay
                        Prayer.MIDNIGHT -> Icons.Default.DarkMode
                        Prayer.TAHAJJUD -> Icons.Default.Bedtime
                    }
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        calculator.getPrayerName(prayerTime.prayer),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isNext) {
                        Text(
                            "Next Prayer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Text(
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(prayerTime.time),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzanSettingsDialog(
    settings: AzanSettings,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onSave: (AzanSettings) -> Unit
) {
    var editedSettings by remember { mutableStateOf(settings) }
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Azan Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Visibility") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Adjustments") }
                    )
                }
                
                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    when (selectedTab) {
                        0 -> VisibilitySettings(editedSettings) { editedSettings = it }
                        1 -> AdjustmentSettings(editedSettings) { editedSettings = it }
                    }
                }
                
                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(editedSettings) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun VisibilitySettings(settings: AzanSettings, onUpdate: (AzanSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Show/Hide Prayer Times", style = MaterialTheme.typography.titleSmall)
        
        SwitchRow("Fajr", settings.showFajr) { onUpdate(settings.copy(showFajr = it)) }
        SwitchRow("Sunrise", settings.showSunrise) { onUpdate(settings.copy(showSunrise = it)) }
        SwitchRow("Dhuhr / Jummah", settings.showDhuhr) { onUpdate(settings.copy(showDhuhr = it)) }
        SwitchRow("Asr", settings.showAsr) { onUpdate(settings.copy(showAsr = it)) }
        SwitchRow("Sunset", settings.showSunset) { onUpdate(settings.copy(showSunset = it)) }
        SwitchRow("Maghrib", settings.showMaghrib) { onUpdate(settings.copy(showMaghrib = it)) }
        SwitchRow("Isha", settings.showIsha) { onUpdate(settings.copy(showIsha = it)) }
        SwitchRow("Midnight", settings.showMidnight) { onUpdate(settings.copy(showMidnight = it)) }
        SwitchRow("Tahajjud", settings.showTahajjud) { onUpdate(settings.copy(showTahajjud = it)) }
    }
}

@Composable
fun AdjustmentSettings(settings: AzanSettings, onUpdate: (AzanSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Prayer Time Adjustments (minutes)", style = MaterialTheme.typography.titleSmall)
        Text(
            "Add or subtract minutes from calculated times",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        AdjustmentRow("Fajr", settings.fajrAdjustment) { onUpdate(settings.copy(fajrAdjustment = it)) }
        AdjustmentRow("Sunrise", settings.sunriseAdjustment) { onUpdate(settings.copy(sunriseAdjustment = it)) }
        AdjustmentRow("Dhuhr / Jummah", settings.dhuhrAdjustment) { onUpdate(settings.copy(dhuhrAdjustment = it)) }
        AdjustmentRow("Asr", settings.asrAdjustment) { onUpdate(settings.copy(asrAdjustment = it)) }
        AdjustmentRow("Sunset", settings.sunsetAdjustment) { onUpdate(settings.copy(sunsetAdjustment = it)) }
        AdjustmentRow("Maghrib", settings.maghribAdjustment) { onUpdate(settings.copy(maghribAdjustment = it)) }
        AdjustmentRow("Isha", settings.ishaAdjustment) { onUpdate(settings.copy(ishaAdjustment = it)) }
        AdjustmentRow("Midnight", settings.midnightAdjustment) { onUpdate(settings.copy(midnightAdjustment = it)) }
    }
}

@Composable
fun NotificationSettings(settings: AzanSettings, onOpenSoundPicker: (Boolean) -> Unit, onUpdate: (AzanSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sound Settings", style = MaterialTheme.typography.titleSmall)
        
        SoundSelectionRow("Azan Sound", settings.selectedAdhanSound) {
            onOpenSoundPicker(false)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Prayer Notifications", style = MaterialTheme.typography.titleSmall)
        
        SwitchRow("Enable Notifications", settings.enableNotifications) { 
            onUpdate(settings.copy(enableNotifications = it)) 
        }
        
        if (settings.enableNotifications) {
            Spacer(modifier = Modifier.height(8.dp))
            SwitchRow("Fajr", settings.enableFajrNotification) { onUpdate(settings.copy(enableFajrNotification = it)) }
            SwitchRow("Dhuhr / Jummah", settings.enableDhuhrNotification) { onUpdate(settings.copy(enableDhuhrNotification = it)) }
            SwitchRow("Asr", settings.enableAsrNotification) { onUpdate(settings.copy(enableAsrNotification = it)) }
            SwitchRow("Maghrib", settings.enableMaghribNotification) { onUpdate(settings.copy(enableMaghribNotification = it)) }
            SwitchRow("Isha", settings.enableIshaNotification) { onUpdate(settings.copy(enableIshaNotification = it)) }
        }
    }
}

@Composable
fun SoundSelectionRow(label: String, selectedSound: AdhanSound, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    label, 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(selectedSound.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label, 
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustmentRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    var textValue by remember(value) { mutableStateOf(String.format(Locale.getDefault(), "%+d", value)) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Minus button
            IconButton(
                onClick = { onValueChange(value - 1) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Manual input field
            BasicTextField(
                value = textValue,
                onValueChange = { newText ->
                    textValue = newText
                    // Try to parse the input
                    val parsed = newText.replace("+", "").toIntOrNull()
                    // Increased limit to allow updates like 80, 120 min etc.
                    if (parsed != null && parsed in -999..999) {
                        onValueChange(parsed)
                    }
                },
                modifier = Modifier
                    .width(70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        innerTextField()
                    }
                }
            )
            
            // Plus button
            IconButton(
                onClick = { onValueChange(value + 1) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhanSoundPickerDialog(
    initialSound: AdhanSound,
    initialUri: String?,
    isFajr: Boolean = false,
    onDismiss: () -> Unit,
    onSoundSelected: (AdhanSound, String?) -> Unit
) {
    val context = LocalContext.current
    var selectedSound by remember { mutableStateOf(initialSound) }
    var selectedUri by remember { mutableStateOf(initialUri) }
    var playingSound by remember { mutableStateOf<AdhanSound?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val fileName = if (isFajr) "custom_azan_fajr.audio" else "custom_azan_general.audio"
                val destFile = java.io.File(context.filesDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                selectedUri = android.net.Uri.fromFile(destFile).toString()
                selectedSound = AdhanSound.CUSTOM
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Could not save custom audio: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Clean up media player when dialog is closed
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun togglePlayback(sound: AdhanSound) {
        if (playingSound == sound) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            playingSound = null
        } else {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            
            try {
                if (sound == AdhanSound.CUSTOM) {
                    if (selectedUri != null) {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(context, android.net.Uri.parse(selectedUri))
                            prepare()
                            start()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "No custom file selected", android.widget.Toast.LENGTH_SHORT).show()
                        return
                    }
                } else {
                    val resName = when(sound) {
                        AdhanSound.AZAN_1 -> "azan_1"
                        AdhanSound.AZAN_2 -> "azan_2"
                        AdhanSound.AZAN_3 -> "azan_3"
                        else -> ""
                    }
                    val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
                    if (resId != 0) {
                        mediaPlayer = MediaPlayer.create(context, resId)
                    }
                }

                mediaPlayer?.setOnCompletionListener { 
                    playingSound = null 
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
                mediaPlayer?.start()
                playingSound = sound
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error playing audio", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isFajr) "Fajr Azan Sound" else "General Azan Sound",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp) // Fixed height to ensure Done button is visible
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Built-in Azan Sounds",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Azan 1
                    AdhanSoundCard(
                        sound = AdhanSound.AZAN_1,
                        isSelected = selectedSound == AdhanSound.AZAN_1,
                        isPlaying = playingSound == AdhanSound.AZAN_1,
                        onSelect = { selectedSound = AdhanSound.AZAN_1 },
                        onPlayStop = { togglePlayback(AdhanSound.AZAN_1) }
                    )
                    
                    // Azan 2
                    AdhanSoundCard(
                        sound = AdhanSound.AZAN_2,
                        isSelected = selectedSound == AdhanSound.AZAN_2,
                        isPlaying = playingSound == AdhanSound.AZAN_2,
                        onSelect = { selectedSound = AdhanSound.AZAN_2 },
                        onPlayStop = { togglePlayback(AdhanSound.AZAN_2) }
                    )
                    
                    // Azan 3
                    AdhanSoundCard(
                        sound = AdhanSound.AZAN_3,
                        isSelected = selectedSound == AdhanSound.AZAN_3,
                        isPlaying = playingSound == AdhanSound.AZAN_3,
                        onSelect = { selectedSound = AdhanSound.AZAN_3 },
                        onPlayStop = { togglePlayback(AdhanSound.AZAN_3) }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "Custom Audio",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Custom Audio
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { filePickerLauncher.launch("audio/*") },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedSound == AdhanSound.CUSTOM)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    AdhanSound.CUSTOM.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (selectedSound == AdhanSound.CUSTOM) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    if (selectedUri != null) "Selected: ${selectedUri!!.substringAfterLast("/")}" else AdhanSound.CUSTOM.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { togglePlayback(AdhanSound.CUSTOM) }) {
                                    Icon(
                                        if (playingSound == AdhanSound.CUSTOM) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (selectedSound == AdhanSound.CUSTOM) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                
                // Done Button
                Box(modifier = Modifier.padding(24.dp)) {
                    Button(
                        onClick = { onSoundSelected(selectedSound, selectedUri) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdhanSoundCard(
    sound: AdhanSound,
    isSelected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onPlayStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect() }
            ) {
                Text(
                    sound.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    sound.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Stop button
                IconButton(
                    onClick = onPlayStop,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Selected checkmark
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MethodSettings(settings: AzanSettings, onUpdate: (AzanSettings) -> Unit) {
    var showMethodPicker by remember { mutableStateOf(false) }
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Calculation Method", style = MaterialTheme.typography.titleSmall)
        
        // Current Method Display
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMethodPicker = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        CalculationMethods.methods.find { it.key == settings.calculationMethodKey }?.name 
                            ?: "Muslim World League",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        CalculationMethods.methods.find { it.key == settings.calculationMethodKey }?.description 
                            ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Madhab Selection
        Text("Asr Calculation (Madhab)", style = MaterialTheme.typography.titleSmall)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onUpdate(settings.copy(madhab = com.batoulapps.adhan.Madhab.SHAFI)) },
                shape = RoundedCornerShape(12.dp),
                color = if (settings.madhab == com.batoulapps.adhan.Madhab.SHAFI) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Shafi", style = MaterialTheme.typography.titleMedium)
                    if (settings.madhab == com.batoulapps.adhan.Madhab.SHAFI) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onUpdate(settings.copy(madhab = com.batoulapps.adhan.Madhab.HANAFI)) },
                shape = RoundedCornerShape(12.dp),
                color = if (settings.madhab == com.batoulapps.adhan.Madhab.HANAFI) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hanafi", style = MaterialTheme.typography.titleMedium)
                    if (settings.madhab == com.batoulapps.adhan.Madhab.HANAFI) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    
    if (showMethodPicker) {
        AlertDialog(
            onDismissRequest = { showMethodPicker = false },
            title = { Text("Select Calculation Method") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CalculationMethods.methods.forEach { method ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onUpdate(settings.copy(calculationMethodKey = method.key))
                                    showMethodPicker = false
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (settings.calculationMethodKey == method.key) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) 
                            else 
                                Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(method.name, style = MaterialTheme.typography.titleSmall)
                                }
                                if (settings.calculationMethodKey == method.key) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMethodPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

