# Contributing to Halanoi Sovereign 🛡️

First off, thank you for considering contributing to **Halanoi Sovereign**! Open-source contributions from the community help make Halanoi a more secure, reliable, and effective tool for digital wellbeing and focus lockdown.

Please take a moment to review this document before submitting contributions.

---

## 📋 Table of Contents
- [How Can You Help?](#-how-can-you-help)
- [Development Setup & Quick Start](#-development-setup--quick-start)
- [Working with Device Owner & ADB](#-working-with-device-owner--adb)
- [Branching & Pull Request Workflow](#-branching--pull-request-workflow)
- [Coding Guidelines & Standards](#-coding-guidelines--standards)
- [Reporting Bugs & Suggesting Features](#-reporting-bugs--suggesting-features)
- [Security Vulnerabilities](#-security-vulnerabilities)

---

## 🤝 How Can You Help?

- 🐛 **Report Bugs**: Encountered a crash, false positive, or bypass? [Open an Issue](https://github.com/kavinmaranravi/HalanoiApp/issues) with detailed reproduction steps and device logs.
- 💡 **Suggest Features**: Have an idea to improve focus locks, DNS blocking, or battery efficiency? Open a feature request.
- 🧠 **Improve Edge AI / Datasets**: Help refine TFLite keyword classification, expand ground-truth dataset samples, or train models in [Halanoi_AI](https://github.com/kavinmaranravi/Halanoi_AI).
- 📝 **Improve Documentation**: Fix inaccuracies, clarify setup steps in `TROUBLESHOOTING.md` or `CODE_BREAKDOWN.md`, or write tutorials.
- 💻 **Contribute Code**: Fix known issues or build requested features.

---

## ⚡ Development Setup & Quick Start

### Prerequisites
- **Android Studio**: Ladybug / Meerkat (or latest recommended version)
- **JDK**: Java 11 or 17 (project uses Java 11 toolchain)
- **Android SDK**: `compileSdk = 36`, `minSdk = 29` (Android 10+)
- **Android NDK & CMake**: Version `3.22.1` (required for native C++ builds in `src/main/cpp`)
- **Git**

### Setup Steps

1. **Fork & Clone the Repository**:
   ```bash
   git clone https://github.com/<your-username>/HalanoiApp.git
   cd HalanoiApp
   ```

2. **Open in Android Studio**:
   - Open Android Studio and select **Open** -> Navigate to the cloned `HalanoiApp` directory.
   - Let Gradle sync dependencies automatically.

3. **Build the Debug APK**:
   ```bash
   # On Windows (PowerShell)
   .\gradlew.bat assembleDebug

   # On macOS / Linux
   ./gradlew assembleDebug
   ```

4. **Run Unit & Instrumentation Tests**:
   ```bash
   # Run local unit tests
   .\gradlew.bat test

   # Run Android instrumentation tests (device/emulator connected)
   .\gradlew.bat connectedAndroidTest
   ```

---

## 🔒 Working with Device Owner & ADB

Halanoi uses **Device Owner (DPC)** privileges for its core system lockdown capabilities. 

> [!CAUTION]
> **Do not provision Device Owner on your primary personal phone** without understanding that Device Owner prevents normal uninstallation until de-provisioned or factory reset. Always use a dedicated test device or an Android Virtual Device (AVD).

### Setting up on an Emulator or Test Device:
1. Ensure **no Google accounts or secondary user accounts** are registered on the device (*Settings -> Accounts*).
2. Install the debug build:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Grant Device Owner status:
   ```bash
   adb shell dpm set-device-owner com.halanoi.app/.HalanoiDeviceAdminReceiver
   ```
4. Verify active admin status:
   ```bash
   adb shell dumpsys device_policy
   ```

---

## 🌿 Branching & Pull Request Workflow

We follow a standard fork-and-pull-request model:

1. **Create a Feature Branch**:
   ```bash
   git checkout -b feature/your-feature-name
   # or for bug fixes:
   git checkout -b fix/issue-description
   ```

2. **Make Focused Changes**:
   - Keep commits small, atomic, and well-described.
   - Follow standard commit message formatting: `feat: add custom keyword regex support` or `fix: handle null accessibility event in address bar scraper`.

3. **Test Thoroughly**:
   - Verify the app builds without errors or warnings.
   - Check that existing focus and VPN features still behave as expected.

4. **Push & Open a Pull Request**:
   ```bash
   git push origin feature/your-feature-name
   ```
   - Open a PR against the `main` (or default) branch of `kavinmaranravi/HalanoiApp`.
   - Provide a clear PR summary explaining:
     - What problem this solves
     - How it was tested (device, Android version)
     - Any relevant screenshots or screen recordings

---

## 📏 Coding Guidelines & Standards

- **Language**: Kotlin is preferred for all Android code.
- **UI Framework**: Modern UI should use **Jetpack Compose**.
- **Architecture**:
  - Keep business logic decoupled from Android lifecycle where feasible.
  - Review [CODE_BREAKDOWN.md](CODE_BREAKDOWN.md) to understand the role of `HalanoiAccessibilityService`, `HalanoiVpnService`, and `TFLiteClassifier`.
- **Formatting**:
  - Adhere to the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) and [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide).
  - Use `ktlint` or Android Studio's default code reformatter (`Ctrl+Alt+L` / `Cmd+Option+L`).
- **Resource Management**:
  - Be mindful of battery and memory footprint. `HalanoiAccessibilityService` runs continuously, so avoid unnecessary allocations or tight loops inside `onAccessibilityEvent`.

---

## 🐛 Reporting Bugs & Suggesting Features

Before creating an issue, search existing [Issues](https://github.com/kavinmaranravi/HalanoiApp/issues) to ensure it hasn't already been reported.

When filing a bug report, please include:
- **Device Details**: Device model, manufacturer (e.g., Vivo, Samsung, Pixel), Android OS version.
- **Halanoi Version**: Installed version or git commit hash.
- **Steps to Reproduce**: Detailed numbered steps to trigger the bug.
- **Expected vs Actual Behavior**.
- **Logcat Logs**: Relevant `adb logcat` output (sanitize any personal info before posting).

---

## 🛡️ Security Vulnerabilities

Please do **NOT** post sensitive security vulnerabilities or zero-day bypass exploits publicly on GitHub Issues. Instead, review our [SECURITY.md](SECURITY.md) for instructions on responsible, private disclosure.

---

Thank you for helping keep the digital space focused and distraction-free! 🚀
