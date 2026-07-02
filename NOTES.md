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

---

## 🌐 2. Default Inbuilt Blocks

The application contains the following pre-configured behaviors to prevent bypass loopholes:

*   **Browser Blackout (Standard Mode)**: By default, the app automatically disables and hides **all alternative browsers** (Brave, Opera, Firefox, Edge, UC, Vivaldi, etc.) to prevent users from bypassing Chrome filters. Only Google Chrome remains active and is controlled via the Vault.
*   **DNS Filtering**: The local VPN automatically redirects all network DNS queries to **Cloudflare Family DNS** (`1.1.1.3` / `1.0.0.3`) to block adult/NSFW web traffic at the socket level.

---

## 🛠️ 3. Safe Recovery & Uninstallation Guide

Since the app locks its own uninstallation and system settings, you cannot remove it directly from your phone. 

To safely remove the app, you must use a PC with ADB installed:

1.  Connect your phone to your PC via USB.
2.  Open a terminal/command prompt on your PC and run:
    ```bash
    adb shell dpm remove-active-admin com.halanoi.app/.HalanoiDeviceAdminReceiver
    ```
3.  Once this command runs, the administrative locks will be lifted.
4.  You can now uninstall the app from your phone normally through `Settings > Apps > Halanoi > Uninstall`.
