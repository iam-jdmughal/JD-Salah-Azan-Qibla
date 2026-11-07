package com.jdsalah.azanqibla.azan

import com.batoulapps.adhan.*
import java.util.*

class PrayerTimeCalculator(private val settings: AzanSettings) {
    
    fun calculatePrayerTimes(date: Date = Date()): List<PrayerTimeData>? {
        val location = settings.location ?: return null
        
        val coordinates = Coordinates(location.latitude, location.longitude)
        val params = CalculationMethods.getMethod(settings.calculationMethodKey)
        params.madhab = settings.madhab
        
        // Apply adjustments
        params.adjustments.fajr = settings.fajrAdjustment
        params.adjustments.sunrise = settings.sunriseAdjustment
        params.adjustments.dhuhr = settings.dhuhrAdjustment
        params.adjustments.asr = settings.asrAdjustment
        params.adjustments.maghrib = settings.maghribAdjustment
        params.adjustments.isha = settings.ishaAdjustment
        
        // Extract date components
        val calendar = Calendar.getInstance().apply { time = date }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        
        // Create DateComponents object
        val dateComponents = com.batoulapps.adhan.data.DateComponents(year, month, day)
        
        val prayerTimes = PrayerTimes(coordinates, dateComponents, params)
        val sunnahTimes = SunnahTimes(prayerTimes)
        
        val times = mutableListOf<PrayerTimeData>()
        
        if (settings.showFajr) {
            times.add(PrayerTimeData(Prayer.FAJR, prayerTimes.fajr, settings.showFajr))
        }
        if (settings.showSunrise) {
            times.add(PrayerTimeData(Prayer.SUNRISE, prayerTimes.sunrise, settings.showSunrise))
        }
        if (settings.showDhuhr) {
            times.add(PrayerTimeData(Prayer.DHUHR, prayerTimes.dhuhr, settings.showDhuhr))
        }
        if (settings.showAsr) {
            times.add(PrayerTimeData(Prayer.ASR, prayerTimes.asr, settings.showAsr))
        }
        if (settings.showSunset) {
            val sunset = Date(prayerTimes.maghrib.time + settings.sunsetAdjustment * 60 * 1000L)
            times.add(PrayerTimeData(Prayer.SUNSET, sunset, settings.showSunset))
        }
        if (settings.showMaghrib) {
            times.add(PrayerTimeData(Prayer.MAGHRIB, prayerTimes.maghrib, settings.showMaghrib))
        }
        if (settings.showIsha) {
            times.add(PrayerTimeData(Prayer.ISHA, prayerTimes.isha, settings.showIsha))
        }
        if (settings.showMidnight) {
            val midnight = Date(sunnahTimes.middleOfTheNight.time + settings.midnightAdjustment * 60 * 1000L)
            times.add(PrayerTimeData(Prayer.MIDNIGHT, midnight, settings.showMidnight))
        }
        if (settings.showTahajjud) {
            times.add(PrayerTimeData(Prayer.TAHAJJUD, sunnahTimes.lastThirdOfTheNight, settings.showTahajjud))
        }
        
        return times
    }
    
    fun getNextPrayer(currentTime: Date = Date()): PrayerTimeData? {
        val times = calculatePrayerTimes(currentTime) ?: return null
        
        // Filter only actual prayers (not sunrise, sunset, midnight, tahajjud)
        val actualPrayers = times.filter { 
            it.prayer in listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        }
        
        val nextToday = actualPrayers.firstOrNull { it.time.after(currentTime) }
        if (nextToday != null) return nextToday

        // If no more prayers today, get next day's Fajr
        val tomorrow = Calendar.getInstance().apply {
            time = currentTime
            add(Calendar.DAY_OF_YEAR, 1)
        }.time
        
        val tomorrowTimes = calculatePrayerTimes(tomorrow) ?: return null
        return tomorrowTimes.firstOrNull { it.prayer == Prayer.FAJR }
    }
    
    fun getCurrentPrayer(currentTime: Date = Date()): PrayerTimeData? {
        val times = calculatePrayerTimes() ?: return null
        
        val actualPrayers = times.filter { 
            it.prayer in listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        }
        
        return actualPrayers.lastOrNull { it.time.before(currentTime) || it.time == currentTime }
    }
    
    fun getTimeUntilNextPrayer(currentTime: Date = Date()): Long? {
        val nextPrayer = getNextPrayer(currentTime) ?: return null
        return nextPrayer.time.time - currentTime.time
    }
    
    fun getPrayerName(prayer: Prayer, date: Date = Date()): String {
        return when (prayer) {
            Prayer.FAJR -> "Fajr"
            Prayer.SUNRISE -> "Sunrise"
            Prayer.DHUHR -> {
                val cal = Calendar.getInstance().apply { time = date }
                if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) "Jummah" else "Dhuhr"
            }
            Prayer.ASR -> "Asr"
            Prayer.SUNSET -> "Sunset"
            Prayer.MAGHRIB -> "Maghrib"
            Prayer.ISHA -> "Isha"
            Prayer.MIDNIGHT -> "Midnight"
            Prayer.TAHAJJUD -> "Tahajjud"
        }
    }
    
    fun formatTimeRemaining(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%dh %02dm %02ds", hours, minutes, seconds)
        } else if (minutes > 0) {
            String.format(Locale.getDefault(), "%dm %02ds", minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%ds", seconds)
        }
    }
}
