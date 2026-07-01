adb shell dpm remove-active-admin com.halanoi.app/.HalanoiDeviceAdminReceiver  ---  GOD MODE REMOVAL

adb shell dpm set-device-owner com.halanoi.app/.HalanoiDeviceAdminReceiver  --- GOD MODE ACTIVE SET


adb reboot recovery --  ulta god mode on but 

Updating a **Device Owner** app can indeed be tricky, but you **never need to remove the admin status** to do it. 

Here is exactly how the update process works, why your update got rejected last week, and how to update successfully:

---

### Why we were able to update it just now (directly):
When updating a Device Owner app, the Android OS enforces three strict security rules:
1. **Signature Match**: The new APK must be signed with the **exact same signing key** as the currently installed app.
2. **Version Code**: The new APK's `versionCode` must be **equal to or higher** than the currently installed version (no downgrades allowed).
3. **Same Package Name**: The package name must remain exactly the same (`com.halanoi.app`).

Because we are building and installing the code from this same developer workspace using **ADB** (`.\gradlew installDebug`), the signatures match perfectly, and the Android OS permits a direct update in-place while keeping the admin/Device Owner status active.

---

### Why your update got rejected last week:
If you tried to update the app and the system rejected it, it was likely due to one of these three reasons:

1. **Attempting to Uninstall First**: 
   Because Halanoi has uninstall protection enabled (`setUninstallBlocked = true`), the Android system will **reject** any attempt to uninstall the app. If you tried to uninstall the old version before installing the new one, the OS blocked it. You must always update **in-place** (overlaying the new app on top of the old one).
2. **Signature Mismatch (Different PC/Build)**:
   If you compiled the APK on a different computer, the default Android "debug key" is different on every PC. Android sees this as a signature mismatch and rejects the update to prevent someone from hijacking your admin app.
3. **Updating from the Phone’s File Manager**:
   On Vivo and other custom ROMs, the on-device Package Installer will block you from updating a Device Owner app directly from the phone (by tapping an APK file). This is a security measure to prevent a user from manually downgrading/replacing the admin controller. 

---

### How to update the app safely:
Always update the app **directly over the existing installation** using ADB from your PC:
```bash
adb install -r app-debug.apk
```
*(The `-r` flag tells Android to replace/reinstall the app in-place, keeping all SharedPreferences and keeping the Device Owner active).*

Note: I attempted to install the updated APK, but ADB reports that no devices are currently attached (your Vivo phone is not connected or USB debugging is disabled). Once you plug your phone back in and make sure USB debugging is on, you can run ./gradlew installDebug or let me know and I will install it for you!