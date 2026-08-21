# User Disclosures & System Notes ⚠️

Please read this document carefully **before** installing Halanoi Sovereign on your primary device. Because this application requests enterprise-level **Device Owner** administrative privileges, it modifies and restricts standard Android system settings to prevent focus bypasses.

---

## 🎯 1. Choose Your Experience: Debug (Flexible) vs. Release (Ambition Mode)

Before installing, choose the build flavor matching your commitment level:

### 🧪 Debug Build (Flexible / Casual Focus)
* **Editable & Deletable Blocks**: You can freely add, edit, or delete blocked websites, keywords, and locked applications.
* **Switchable Controls**: You can toggle or turn off the VPN and Accessibility service when needed.
* **In-App Deactivation**: Includes an in-app "Deactivate Device Owner 🔓" button.
* **Who it's for**: Ideal if you only occasionally need to focus or want a flexible setup so you don't feel frustrated on busy or unpredictable days.

### 🔒 Release Build (Ambition Mode — 100% Commitment)
* **No Edit/Delete Controls**: We intentionally stripped out all edit and delete buttons for blocked websites, keywords, and apps so you cannot sabotage your own focus during weak moments.
* **Unbreakable VPN Policy**: Device Owner enforces `DISALLOW_CONFIG_VPN` and Always-On VPN. You **cannot switch over to another VPN or turn off the VPN** in Android system settings or Admin settings.
* **No On-Device Admin Deactivation**: Removed the in-app deactivate button.
* **Who it's for**: Only install the Release build when you are 100% ready for Ambition Mode to achieve unbreakable focus on your goals!

> [!IMPORTANT]
> **Why Release APK is NOT pre-compiled in GitHub Releases**:
> Halanoi is an advanced, enterprise-grade focus engine. To ensure users understand ADB commands and Device Owner mechanics before entering Ambition Mode, we provide all source code on GitHub. You can download or fork the repository and compile the Release APK on your PC once you are confident.

---

## 🔒 2. Restricted & Locked Settings (Device Owner)

Once activated as a Device Owner via ADB, Halanoi Sovereign enforces the following restrictions at the OS level:

*   **Anti-Uninstall Guard**: You cannot uninstall the app from the launcher or settings. The uninstall button will be disabled.
*   **Settings Freeze**: The Android accessibility settings menu is locked. You cannot toggle the **Halanoi Accessibility Service** off.
*   **Force Stop & Clear Data Disabled**: In the Android App Info page, the "Force Stop" and "Clear Data" buttons are greyed out.
*   **Anti-Factory-Reset Guard**: The system "Factory Reset" option in settings is disabled to prevent wiping the device to bypass focus restrictions.
*   **VPN Lockdown**: In Release builds, the local VPN cannot be disabled or bypassed via system settings (`DISALLOW_CONFIG_VPN`).
*   **APK Installation Block**: Installing apps from unknown sources (manual APK sideloading) is disabled at the system level.

---

## 🛡️ 3. Accessibility Service & 30-Second Throttle Fix

* **Accessibility Alert Fix**: We fixed the accessibility alert loops to ensure warning notifications are throttled to **at most once every 30 seconds**. This gives you ample time to configure settings or uninstall without being overwhelmed by alert loops.
* **Automatic Screen Classification**: When using web browsers, the on-device AI scans text in real-time. If it classifies content as distracting (`nsfw`, `sports`, `entertainment`, `politics`) with $\ge 70\%$ confidence, it immediately redirects and returns home.

---

## 🌐 4. Default Inbuilt Blocks & DNS

*   **Browser Blackout (Standard Mode)**: By default, the app automatically disables and hides **all alternative web browsers** (Brave, Opera, Firefox, Edge, UC Browser, Vivaldi, Tor Browser, etc.) to prevent users from accessing unfiltered web pages. Only Google Chrome remains active and is monitored.
*   **DNS Filtering**: The local loopback VPN redirects DNS queries to **Cloudflare Family DNS** (`1.1.1.3` / `1.0.0.3`) to block adult/NSFW traffic at the network level.

---

## 📝 5. Activity & Scratchpad Studio with Permanent Backup

* **Permanent 3-Tier Backup**: Notes, tasks, and scratchpad pads automatically save to `MediaStore.Downloads` (`Download/Halanoi/halanoi_notes_backup.json`) and public Documents. Backups survive app uninstallation.
* **1-Tap Restore**: In Admin Settings, tap **"Restore Notes & Tasks 📥"** to pick your backup JSON via Android's native file picker and instantly reload your notes without permission errors.
* **1-Tap Export**: Tap **"Export Notes & Tasks 📤"** to generate fresh snapshots on demand.

---

## 🛠️ 6. Safe Recovery & Uninstallation Guide

To safely remove the app, connect your phone to your PC via USB with ADB:

1.  Open a terminal on your PC and run:
    ```bash
    adb shell dpm remove-active-admin com.halanoi.app/.HalanoiDeviceAdminReceiver
    ```
2.  Once this command runs, administrative locks are lifted.
3.  You can now uninstall the app from your phone normally through `Settings > Apps > Halanoi > Uninstall`.

---

**Stay focused and good luck on your journey!**

**Author: Kavinmaran Ravi ([@kavinmaranravi](https://github.com/kavinmaranravi)) — Halanoi**
