# Proguard & R8 optimization rules for SmartAlarm

# Preserve ViewBinding
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
}

# Preserve ML Kit and CameraX
-keep class com.google.mlkit.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn androidx.camera.**

# Preserve Alarm Data Model
-keepclassmembers class com.smartalarm.app.AlarmData {
    <fields>;
    <methods>;
}

# Keep Activities, Services, Receivers
-keep class com.smartalarm.app.** { *; }
