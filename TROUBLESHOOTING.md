# Setup Troubleshooting Guide 🛠️

Setting up enterprise-level **Device Owner** privileges on Android via ADB is a powerful process, but it can trigger security protections in the Android OS. Below are the most common errors and how to solve them.

---

## 🚨 1. Error: "Already has accounts on the device"

### The Error Message:
```text
java.lang.IllegalStateException: Not allowed to start/set device owner because there are already accounts on the device.
```

### Why it happens:
Android security blocks setting a Device Owner if there is already an active Google Account, email, or messaging account logged in. This prevents malicious apps from hijacking a user's primary account.

### How to fix it:

#### **Option A: Temporary Account Removal (Recommended)**
1. On your phone, go to **Settings > Passwords & Accounts** (or *Accounts & Backup > Manage Accounts*).
2. Tap on each account (Google, WhatsApp, Email, etc.) and select **Remove Account**.
   * *Note: This will not delete your accounts; it just logs you out temporarily.*
3. Run the ADB command again:
   ```bash
   adb shell dpm set-device-owner com.halanoi.app/.HalanoiDeviceAdminReceiver
   ```
4. Once it says **Success**, go back to Settings and log back into your Google and other accounts.

#### **Option B: Fresh Setup / Factory Reset Method**
If you are setting up a dedicated focus phone:
1. Factory Reset your device.
2. In the initial setup wizard, **do not sign in to your Google Account** or connect to Wi-Fi yet. Skip all sign-in steps.
3. Enable **Developer Options** and **USB Debugging**.
4. Connect to your PC and run the ADB command.
5. Once configured, complete the setup wizard and sign into your accounts.

---

## 🚨 2. Error: "Admin is already active"

### The Error Message:
```text
java.lang.IllegalStateException: Not allowed to set device owner because the admin is already active.
```

### Why it happens:
You have already activated Halanoi Sovereign as a standard "Device Administrator" inside the app UI or settings, which blocks upgrading it to "Device Owner".

### How to fix it:
1. On your phone, go to **Settings > Security > Device Admin Apps** (or *Additional Settings > Privacy > Device Admin*).
2. Turn **OFF** the switch for Halanoi Sovereign.
3. Run the ADB command again on your PC.

---

## 🚨 3. Error: "adb: device/emulator not found"

### Why it happens:
Your computer cannot establish a connection with your phone.

### How to fix it:
1. Ensure **USB Debugging** is turned ON in *Settings > Developer Options*.
2. Unlock your phone screen and look for a popup asking: **"Allow USB Debugging?"**. Check the box *"Always allow from this computer"* and tap **Allow**.
3. Run this command on your PC to verify the connection:
   ```bash
   adb devices
   ```
   It should list your device serial number and say `device` (not `unauthorized` or `offline`).

---

## 🚨 4. Error: "Unknown admin component"

### Why it happens:
There is a typo in the package or receiver name in the command.

### How to fix it:
Make sure you copy and paste the command exactly, paying attention to the period (`.`) and slash (`/`):
```bash
adb shell dpm set-device-owner com.halanoi.app/.HalanoiDeviceAdminReceiver
```

---

## 🚨 5. Error: "INSTALL_PARSE_FAILED_NO_CERTIFICATES"

### The Error Message:
```text
Failure [INSTALL_PARSE_FAILED_NO_CERTIFICATES: Failed to collect certificates from /data/app/... Attempt to get length of null array]
```

### Why it happens:
Android security requires every installed app to be digitally signed with a certificate. This error happens when you try to manually install an **unsigned release build** (which has no certificate signature).

### How to fix it:
You must sign the release build before installing it. We have configured the project to automatically sign release builds using your local developer debug key.
1. Run a rebuild using Gradle to output a signed release build:
   ```bash
   ./gradlew assembleRelease
   ```
2. The compiler will now output a signed version: **`app-release.apk`** (located at `app/build/outputs/apk/release/`).
3. Install the signed APK instead of the unsigned one (remember to add the `-t` flag as explained below).

---

## 🚨 6. Error: "INSTALL_FAILED_TEST_ONLY"

### The Error Message:
```text
Failure [INSTALL_FAILED_TEST_ONLY: Failed to install test-only apk. Did you forget to add -t?]
```

### Why it happens:
By default, the debug build (and any release build signed with a developer debug key) is marked as a developer-only test build (`testOnly="true"`). Android blocks regular ADB installs of test builds to protect users.

### How to fix it:
You must explicitly authorize the installation by adding the **`-t`** parameter to your ADB command:
```bash
adb install -t app-debug.apk
# or for the release build:
adb install -t app-release.apk
```

---

## 🚨 7. How do I remove Halanoi Sovereign?

Because the app blocks standard uninstallation, you must use ADB to remove it:

1. Connect your phone to your PC and run:
   ```bash
   adb shell dpm remove-active-admin com.halanoi.app/.HalanoiDeviceAdminReceiver
   ```
2. Once the command completes, you can uninstall it like a normal app under `Settings > Apps > Halanoi > Uninstall`.

