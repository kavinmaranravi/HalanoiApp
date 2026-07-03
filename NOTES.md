# User Disclosures & System Notes ⚠️

Please read this document carefully **before** installing Halanoi Sovereign on your primary device. Because this application requests enterprise-level **Device Owner** administrative privileges, it modifies and restricts standard Android system settings to prevent focus bypasses.

---

## 🔒 1. Restricted & Locked Settings

Once activated as a Device Owner via ADB, Halanoi Sovereign enforces the following restrictions at the OS level:

*   **Anti-Uninstall Guard**: You cannot uninstall the app from the launcher or settings. The uninstall button will be disabled.
*   **Settings Freeze**: The Android accessibility settings menu is locked. You cannot toggle the **Halanoi Accessibility Service** off.
*   **Force Stop & Clear Data Disabled**: In the Android App Info page, the "Force Stop" and "Clear Data" buttons are greyed out to prevent stopping the services.
*   **Anti-Factory-Reset Guard**: The system "Factory Reset" (Erase all data) option in your phone settings is disabled and greyed out. This prevents wiping the device to bypass focus restrictions.
*   **VPN Lockdown**: The local VPN cannot be disabled or bypassed by system settings.
*   **APK Installation Block**: Installing apps from unknown sources (manually downloading and opening `.apk` files) is disabled at the system level, and the Android package installer is blocked.

---

## 🌐 2. Default Inbuilt Blocks

The application contains the following pre-configured behaviors to prevent bypass loopholes:

*   **Browser Blackout (Standard Mode)**: By default, the app automatically disables and hides **all alternative web browsers** (Brave, Opera, Firefox, Edge, UC Browser, Vivaldi, Tor Browser, Ornet, etc.) to prevent users from accessing unfiltered web pages. Only Google Chrome remains active and is monitored.
*   **DNS Filtering**: The local VPN automatically redirects all network DNS queries to **Cloudflare Family DNS** (`1.1.1.3` / `1.0.0.3`) to block adult/NSFW web traffic at the network level.

---

## 📱 3. App Blocking & AI Content Monitoring

To keep you focused, Halanoi Sovereign uses a hybrid blocking approach:

### 🚫 Permanently Blocked Apps
These apps are completely blocked at the launch level to eliminate major distractions:
*   **Social Media**: Twitter/X, Instagram, Facebook, Facebook Lite, Snapchat, Reddit
*   **Streaming & Entertainment**: Netflix, Disney+ Hotstar, Amazon Prime Video, JioCinema, YouTube
*   **Bypass Tools**: Android VPN Settings (`com.android.vpndialogs`), Orbot/Tor Proxy, InviZible Pro
*   **System Protection**: Package Installer (to prevent bypassing admin controls)

### 🔍 Monitored Web Browsers (AI Scanned)
When using these browsers, the on-device AI scans the screen text. If it classifies the content under blocked categories (`nsfw`, `sports`, `entertainment`, or `politics`) with 70% or higher confidence, the screen is locked:
*   Google Chrome, Microsoft Edge, Mozilla Firefox, Samsung Internet Browser, DuckDuckGo Browser, Google Search App

### 🟢 Bypass Keywords (Always Allowed)
These keywords bypass the AI scanner to prevent false blocks while studying:
*   **Weather**: `weather`, `climate`, `temperature`, `forecast`
*   **Math & Science**: `latex formation`, `math`, `calculus`, `equation`
*   **Software**: `code`, `programming`
*   **Search Context**: `how can i`, `i am not able to`, `can you make`, `bro`, `give as`, `guv`
---

## 🛠️ 4. Safe Recovery & Uninstallation Guide

Since the app locks its own uninstallation and system settings, you cannot remove it directly from your phone. 

To safely remove the app, you must use a PC with ADB installed:

1.  Connect your phone to your PC via USB.
2.  Open a terminal/command prompt on your PC and run:
    ```bash
    adb shell dpm remove-active-admin com.halanoi.app/.HalanoiDeviceAdminReceiver
    ```
3.  Once this command runs, the administrative locks will be lifted.
4.  You can now uninstall the app from your phone normally through `Settings > Apps > Halanoi > Uninstall`.
