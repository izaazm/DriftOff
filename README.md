# DriftOff - Smart Sleep Monitor

A smart sleep monitoring Android application that automatically detects drowsiness and adjusts phone settings to help users fall asleep faster.

## Running Environment

### Requirements

| Component | Version |
|-----------|---------|
| **Android Studio** | Ladybug (2024.2.1) or newer |
| **Kotlin** | 1.9+ |
| **Minimum SDK** | API 26 (Android 8.0 Oreo) |
| **Target SDK** | API 36 |
| **JDK** | 11 |

### Tested Devices

- Samsung Galaxy S20 Ultra, Android 13
- Android Emulator (limited sensor simulation)

---

## Installation Guide

### Step 1: Clone the Repository (if files are not present)

```bash
git clone https://github.com/izaazm/DriftOff.git
cd MyApplication
```

### Step 2: Open in Android Studio

1. Launch **Android Studio**
2. Select **File → Open**
3. Navigate to the cloned `MyApplication` folder
4. Click **OK** and wait for Gradle sync to complete

### Step 3: Build the Project

1. Click **Build → Make Project** (or press `Ctrl+F9` / `Cmd+F9`)
2. Wait for the build to complete (check the "Build" tab for progress)

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Install on Device

**Option A: Run from Android Studio**
1. Connect your Android device via USB (enable USB debugging)
2. Click the **Run** button (green play icon) or press `Shift+F10`
3. Select your device and click **OK**

**Option B: Install APK manually**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Step 5: Grant Permissions

When first launching the app, grant the following permissions:

| Permission | Purpose | How to Grant |
|------------|---------|--------------|
| **Modify System Settings** | Control brightness | Settings → Apps → DriftOff → Permissions |
| **Do Not Disturb Access** | Enable DND mode | Settings → Apps → Special access → Do Not Disturb |
| **Camera** (Optional) | Eye-closed verification | Runtime prompt |
| **Microphone** (Optional) | Ambient noise detection | Runtime prompt |
| **Notification** | Foreground service | Runtime prompt |

---

## Logging Guide (For Debugging & Grading)

The app uses Android's `Log` class extensively during development. Use **Logcat** in Android Studio to view logs.

### Important Log Tags

| Tag | Description |
|-----|-------------|
| `SleepMonitorService` | Main service lifecycle, monitoring cycles, state transitions |
| `DrowsinessScoreCalculator` | Score calculation, feature weights, adaptive multiplier |
| `PhoneSettingsController` | Brightness/volume changes, DND state |
| `SensorDataProvider` | Accelerometer, light sensor readings |
| `AudioSampleProvider` | Ambient noise dB levels |
| `CameraVerificationService` | Face detection, eye-closed verification |
| `SleepAnalyticsRepository` | Session tracking, data persistence |