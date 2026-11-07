package com.jdsalah.azanqibla.azan

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import com.batoulapps.adhan.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

// Prayer time data class
@Keep
data class PrayerTimeData(
    val prayer: Prayer,
    val time: Date,
    val isVisible: Boolean = true
)

@Keep
enum class Prayer {
    FAJR, SUNRISE, DHUHR, ASR, SUNSET, MAGHRIB, ISHA, MIDNIGHT, TAHAJJUD
}

// Calculation method wrapper
@Keep
data class CalculationMethodInfo(
    val key: String,
    val name: String,
    val description: String
)

// Location data
@Keep
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val cityName: String = "",
    val countryName: String = ""
)

// Built-in Adhan sounds
@Keep
enum class AdhanSound(val displayName: String, val description: String) {
    AZAN_1("Azan 1", "Beautiful Adhan call to prayer"),
    AZAN_2("Azan 2", "Traditional Adhan recitation"),
    AZAN_3("Azan 3", "Classic Adhan style"),
    CUSTOM("Custom Audio", "Select from device storage")
}

// Azan settings
@Keep
data class AzanSettings(
    val location: LocationData? = null,
    val calculationMethodKey: String = "MuslimWorldLeague",
    val madhab: Madhab = Madhab.SHAFI,
    
    // Prayer visibility
    val showFajr: Boolean = true,
    val showSunrise: Boolean = true,
    val showDhuhr: Boolean = true,
    val showAsr: Boolean = true,
    val showSunset: Boolean = false,
    val showMaghrib: Boolean = true,
    val showIsha: Boolean = true,
    val showMidnight: Boolean = false,
    val showTahajjud: Boolean = false,
    
    // Prayer adjustments (in minutes)
    val fajrAdjustment: Int = 0,
    val sunriseAdjustment: Int = 0,
    val dhuhrAdjustment: Int = 0,
    val asrAdjustment: Int = 0,
    val sunsetAdjustment: Int = 0,
    val maghribAdjustment: Int = 0,
    val ishaAdjustment: Int = 0,
    val midnightAdjustment: Int = 0,
    
    // Notification settings
    val enableNotifications: Boolean = true,
    val enableFajrNotification: Boolean = true,
    val enableDhuhrNotification: Boolean = true,
    val enableAsrNotification: Boolean = true,
    val enableMaghribNotification: Boolean = true,
    val enableIshaNotification: Boolean = true,
    
    // Adhan audio selection
    val selectedAdhanSound: AdhanSound = AdhanSound.AZAN_1,
    val selectedFajrAdhanSound: AdhanSound = AdhanSound.AZAN_1,
    val customAdhanUri: String? = null,
    val customFajrAdhanUri: String? = null,
    
    // Widget settings
    val enableHomescreenWidget: Boolean = false,
    val enableNotificationWidget: Boolean = false
)

// Reminder data
@Keep
data class PrayerReminder(
    val id: String = UUID.randomUUID().toString(),
    val prayer: Prayer,
    val minutesOffset: Int, // negative for before, positive for after
    val label: String = "",
    val enabled: Boolean = true
)

class AzanSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AzanSettings", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun saveSettings(settings: AzanSettings) {
        prefs.edit().apply {
            putString("settings", gson.toJson(settings))
            apply()
        }
    }
    
    fun getSettings(): AzanSettings {
        val json = prefs.getString("settings", null)
        return if (json != null) {
            gson.fromJson(json, AzanSettings::class.java)
        } else {
            AzanSettings()
        }
    }
    
    fun saveReminders(reminders: List<PrayerReminder>) {
        prefs.edit().apply {
            putString("reminders", gson.toJson(reminders))
            apply()
        }
    }
    
    fun getReminders(): List<PrayerReminder> {
        val json = prefs.getString("reminders", null)
        return if (json != null) {
            val type = object : TypeToken<List<PrayerReminder>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }
}

// Available calculation methods
object CalculationMethods {
    val methods = listOf(
        CalculationMethodInfo(
            "MuslimWorldLeague",
            "Muslim World League",
            "Standard method used by many countries. Fajr: 18°, Isha: 17°"
        ),
        CalculationMethodInfo(
            "Egyptian",
            "Egyptian General Authority",
            "Used in Egypt and some Middle Eastern countries. Fajr: 19.5°, Isha: 17.5°"
        ),
        CalculationMethodInfo(
            "Karachi",
            "University of Islamic Sciences, Karachi",
            "Used in Pakistan. Fajr: 18°, Isha: 18°"
        ),
        CalculationMethodInfo(
            "UmmAlQura",
            "Umm al-Qura University, Makkah",
            "Used in Saudi Arabia. Fajr: 18.5°, Isha: 90 min after Maghrib"
        ),
        CalculationMethodInfo(
            "Dubai",
            "Dubai (Gulf Region)",
            "Used in UAE. Fajr: 18.2°, Isha: 18.2°"
        ),
        CalculationMethodInfo(
            "Qatar",
            "Qatar",
            "Used in Qatar. Fajr: 18°, Isha: 90 min after Maghrib"
        ),
        CalculationMethodInfo(
            "Kuwait",
            "Kuwait",
            "Used in Kuwait. Fajr: 18°, Isha: 17.5°"
        ),
        CalculationMethodInfo(
            "MoonsightingCommittee",
            "Moonsighting Committee",
            "Used in North America. Fajr: 18°, Isha: 18°"
        ),
        CalculationMethodInfo(
            "Singapore",
            "Singapore",
            "Used in Singapore. Fajr: 20°, Isha: 18°"
        ),
        CalculationMethodInfo(
            "NorthAmerica",
            "ISNA (North America)",
            "Islamic Society of North America. Fajr: 15°, Isha: 15°"
        )
    )
    
    fun getMethod(key: String): CalculationParameters {
        return when (key) {
            "MuslimWorldLeague" -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            "Egyptian" -> CalculationMethod.EGYPTIAN.parameters
            "Karachi" -> CalculationMethod.KARACHI.parameters
            "UmmAlQura" -> CalculationMethod.UMM_AL_QURA.parameters
            "Dubai" -> CalculationMethod.DUBAI.parameters
            "Qatar" -> CalculationMethod.QATAR.parameters
            "Kuwait" -> CalculationMethod.KUWAIT.parameters
            "MoonsightingCommittee" -> CalculationMethod.MOON_SIGHTING_COMMITTEE.parameters
            "Singapore" -> CalculationMethod.SINGAPORE.parameters
            "NorthAmerica" -> CalculationMethod.NORTH_AMERICA.parameters
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        }
    }
}
