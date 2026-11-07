# 🕋 JD Salah, Azan Qibla
### High-Performance Islamic Prayer Times, Qibla Compass & Salah Tracker for Android

**JD Salah, Azan Qibla** is a premium, lightweight, and privacy-focused Android application created by **JD Mughal** to deliver astronomically accurate prayer times, reliable Azan alarms, a smooth real-time Qibla finder, and comprehensive daily Salah habit tracking—engineered with 100% Jetpack Compose and modern Material 3 design.

---

[📥 Download Latest APK](https://github.com/iam-jdmughal/JD-Salah-Azan-Qibla/releases/latest) • [⚡ Key Features](#-key-features) • [📦 Calculation Methods](#-supported-calculation-methods) • [🛠️ Technical Architecture](#%EF%B8%8F-technical-architecture) • [🚀 How to Build](#-building-from-source) • [❓ FAQ](#-frequently-asked-questions-faq) • [👨‍💻 Developer](#-developer--community)

---

## 🌟 What is JD Salah, Azan Qibla?

**JD Salah, Azan Qibla** is an open-source, ad-free Islamic utility designed to help Muslims worldwide stay steadfast in their daily prayers. Unlike commercial prayer apps that are bloated with intrusive banner ads, location trackers, and 50+ MB download sizes, JD Salah operates completely **offline-first**, weighs just **~2.7 MB**, and respects your device resources and battery life.

Whether you are calculating Fajr in North America, tracking Asr according to the Hanafi or Shafi madhab, aligning your compass directly to the Holy Kaaba in Makkah, or reviewing your monthly prayer consistency in the interactive Journey Matrix, JD Salah provides a tranquil, focused experience free of distractions.

---

## ⚡ Key Features

* 🕋 **Astronomically Accurate Prayer Times:** Computes Fajr, Sunrise, Dhuhr, Asr, Sunset, Maghrib, Isha, Midnight, and Tahajjud with second-level countdown timers to the next prayer.
* 🧭 **Real-Time 360° Qibla Compass:** Smooth sensor fusion utilizing hardware rotation vectors and filtered accelerometer/magnetometer feeds. Features anti-flip 360° rotation sanitization, real-time geodesic distance to Makkah, and haptic feedback when aligned.
* 🔊 **Reliable Azan Alarm Engine:** Plays authentic Adhan calls to prayer via Android's high-priority `USAGE_ALARM` audio stream, ensuring Fajr wakes you up even if the phone's media volume is silent or muted.
* 📱 **Full-Screen Lockscreen Azan Overlay:** Shows a peaceful Islamic overlay with one-tap prayer confirmation when Azan triggers while your device is locked.
* 🎵 **Custom Audio Support:** Choose between built-in recitations or select any custom Adhan MP3 from your device storage—safely copied to sandbox storage for guaranteed alarm playback across reboots.
* 📊 **Daily Prayer Log & Momentum Tracker:** Log each prayer with detailed statuses (**Jama'ah**, **Alone**, **Qaza**, **Missed**) and track your consistency percentage with an interactive monthly Journey Matrix calendar heatmap.
* ⏰ **Automated Daily Rescheduling:** Employs intelligent AlarmManager chains and boot receivers (`BOOT_COMPLETED` and daily midnight triggers) so prayer notifications never stop firing even if the app remains closed for days.
* 🎨 **Bespoke Islamic Geometry UI:** Crafted with glassmorphic cards (`GlassCard`), dynamic procedural Islamic star patterns drawn with GPU caching, Bismillah Arabic calligraphy, and seamless Dark/Light theming.
* 🪶 **Ultra Lightweight (~2.7 MB):** R8-minified DEX bytecode with zero bloat and instant startup.
* 🔒 **100% Privacy-First & Offline:** Zero ads, zero telemetry, zero analytics, zero external cloud dependencies. Your location stays strictly on your device.

---

## 📦 Supported Calculation Methods

JD Salah integrates the industry-standard Adhan algorithm, offering 10 globally recognized conventions and dual juristic methods (Madhab):

| Method Name | Region / Organization | Fajr Angle | Isha Parameter |
| :--- | :--- | :---: | :---: |
| **Muslim World League** | Global Standard (Europe, Far East) | 18.0° | 17.0° |
| **Egyptian General Authority** | Egypt, Africa, Middle East | 19.5° | 17.5° |
| **Univ. of Islamic Sciences, Karachi** | Pakistan, India, Bangladesh, Afghanistan | 18.0° | 18.0° |
| **Umm al-Qura University** | Makkah, Saudi Arabia, Gulf | 18.5° | 90 min after Maghrib |
| **Dubai (Gulf Region)** | United Arab Emirates | 18.2° | 18.2° |
| **Qatar** | State of Qatar | 18.0° | 90 min after Maghrib |
| **Kuwait** | State of Kuwait | 18.0° | 17.5° |
| **Moonsighting Committee** | North America & Global Astronomical | 18.0° | 18.0° |
| **Singapore (MUIS)** | Singapore, Malaysia, Indonesia | 20.0° | 18.0° |
| **ISNA (North America)** | United States & Canada | 15.0° | 15.0° |

> **Asr Juristic Methods:** Supports both **Standard (Shafi'i, Maliki, Hanbali)** and **Hanafi** shadow ratios, alongside customizable per-prayer minute offset adjustments (-60 to +60 min).

---

## 🛠️ Technical Architecture

* **Language:** 100% Kotlin
* **UI Framework:** Jetpack Compose & Material 3 (BOM `2024.02.01`)
* **Prayer Calculation Core:** `com.batoulapps.adhan:adhan:1.2.1`
* **Sensor Architecture:** Fused Android `SensorManager` (`TYPE_ROTATION_VECTOR` with low-pass filtered accelerometer & magnetometer fallback)
* **Background Scheduling:** Android `AlarmManager` with exact alarm compliance (`SCHEDULE_EXACT_ALARM`) and Doze-mode persistence
* **Audio Service:** Android Foreground Service (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`) routed through `AudioAttributes.USAGE_ALARM`
* **Minimum SDK:** Android 7.0 Nougat (API 24)
* **Target SDK:** Android 16 (API 36)
* **Release Artifacts:**
  * **Signed APK:** ~2.7 MB (`JDSalahAzanQibla-release.apk`)
  * **Play Store AAB:** ~4.3 MB (`JDSalahAzanQibla-release.aab`)

---

## 📥 Download JD Salah, Azan Qibla APK

You can download the pre-compiled, production-signed APK directly from GitHub:

👉 **[Download JD Salah, Azan Qibla APK (v1.0.0)](https://github.com/iam-jdmughal/JD-Salah-Azan-Qibla/releases/latest)**

---

## 🚀 Building from Source

### 1. Prerequisites
- **Android Studio** Ladybug / Hedgehog or newer (or command-line Gradle)
- **JDK 17** installed and configured in your environment
- **Android SDK Platform 36** (API 36)

### 2. Clone the Repository
```bash
git clone https://github.com/iam-jdmughal/JD-Salah-Azan-Qibla.git
cd JD-Salah-Azan-Qibla
```

### 3. Build Signed Release APK
To assemble the optimized, minified release APK:
```bash
./gradlew assembleRelease
```
* **Output Path:** `app/build/outputs/apk/release/JDSalahAzanQibla-release.apk`

### 4. Build Play Store App Bundle (AAB)
To generate the Google Play Console upload bundle:
```bash
./gradlew bundleRelease
```
* **Output Path:** `app/build/outputs/bundle/release/JDSalahAzanQibla-release.aab`

### 5. Build Debug APK (For Fast Development)
```bash
./gradlew assembleDebug
```
* **Output Path:** `app/build/outputs/apk/debug/JDSalahAzanQibla-debug.apk`

---

## ❓ Frequently Asked Questions (FAQ)

#### What makes JD Salah different from apps like Muslim Pro?
Most mainstream prayer apps contain heavy advertisement tracking SDKs, third-party analytics, premium subscriptions, and downloads exceeding 50–100 MB. **JD Salah is 100% free, open-source, ad-free, and respects your privacy.** It does not track your location, sell data, or require an internet connection once your city is configured.

#### Does the Azan sound if my phone is on Silent or Do Not Disturb?
Yes. JD Salah routes its audio through Android's dedicated **Alarm Stream** (`USAGE_ALARM`), matching the behavior of your morning alarm clock.

#### Why is the app size only ~2.7 MB?
We use pure Kotlin and Jetpack Compose without bulky third-party libraries, bloated image assets, or heavy tracking services. In release builds, R8 minification strips out all unused vector icons and resources.

#### Can I use my own custom Adhan audio?
Yes. In the Azan sound picker, you can choose any MP3 or audio file from your device. JD Salah automatically copies the file into internal secure storage so that background alarms play smoothly without permission revocation.

---

## 👨‍💻 Developer & Community

* **Developer:** [JD Mughal](https://github.com/iam-jdmughal)
* **Organization:** JD Softwares / JD Corporation
* **Repository:** [JD-Salah-Azan-Qibla on GitHub](https://github.com/iam-jdmughal/JD-Salah-Azan-Qibla)

If you find this application beneficial for your spiritual routine, please consider starring ⭐ the repository and sharing it with family and friends!

---

## 📄 License

```text
Copyright (c) 2026 JD Mughal / JD Softwares

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
