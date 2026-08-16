# Smart Alarm — Android Code Studio Ready

A Kotlin-based anti-snooze Android alarm application with interactive wake-up challenges (Math, Camera motion & Face angle detection, Simon memory).

---

## 🛠️ Android Code Studio Setup Guide

### 1. Open the Project
1. In **Android Code Studio**, choose **Open Project**.
2. Navigate to and select the root directory:
   `/storage/emulated/0/Download/SmartAlarm_AndroidCodeStudio_READY`
   *(Ensure you select the folder containing `settings.gradle.kts`, not the `app/` subfolder).*
3. Allow Gradle sync to complete.

### 2. Building & Running in Android Code Studio
- Tap **Run** or **Build** in Android Code Studio.
- If using the built-in terminal:
  ```bash
  ./gradlew assembleDebug
  ```
  The generated debug APK will be placed in:
  `app/build/outputs/apk/debug/app-debug.apk`

---

## 📦 Compatibility & Technical Specifications

| Parameter | Value / Version |
| :--- | :--- |
| **Minimum SDK** | `26` (Android 8.0 Oreo) |
| **Target / Compile SDK** | `35` (Android 15) |
| **Java Compatibility** | Java 17 |
| **Kotlin Version** | `2.0.21` |
| **Android Gradle Plugin** | `8.6.1` |
| **Gradle Wrapper** | `8.7` |
| **View Binding** | Enabled |

---

## 🔔 Android Permissions
- `CAMERA`: For wake-up movement & face verification
- `USE_EXACT_ALARM` & `SCHEDULE_EXACT_ALARM`: For precise alarm triggering in Doze mode
- `USE_FULL_SCREEN_INTENT`: Displaying alarm over lockscreen
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Continuous audio playback
- `POST_NOTIFICATIONS`: Android 13+ alarm notifications
- `RECEIVE_BOOT_COMPLETED`: Alarm restoration on device restart
