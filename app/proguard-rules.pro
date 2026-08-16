# Proguard rules for SmartAlarm

# Keep ViewBinding classes
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
}

# Keep ML Kit and CameraX classes
-keep class com.google.mlkit.** { *; }
-keep class androidx.camera.** { *; }

# Keep Alarm data model for JSON serialization
-keepclassmembers class com.smartalarm.app.AlarmData {
    <fields>;
    <methods>;
}
