# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/slava/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# Keep all Azan models and data structures for Gson serialization
-keep class com.jdsalah.azanqibla.azan.** { *; }
-keepclassmembers class com.jdsalah.azanqibla.azan.** { *; }

# Keep Adhan library calculation parameters and enums
-keep class com.batoulapps.adhan.** { *; }
-keepclassmembers class com.batoulapps.adhan.** { *; }

# Keep SharedPreferences models and enum names
-keepclassmembers enum com.jdsalah.azanqibla.** { *; }

