package com.halanoi.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast

class HalanoiDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        enforceHalanoiPolicies(context)
        Toast.makeText(context, "Halanoi Shield Engaged. System Locked.", Toast.LENGTH_LONG).show()
    }

    companion object {
        /**
         * Enforces strict policies for Android 16 and OriginOS.
         * Resets accessibility whitelist to null (allowing services to run),
         * auto-enables HalanoiAccessibilityService, and locks uninstallation.
         */
        fun enforceHalanoiPolicies(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, HalanoiDeviceAdminReceiver::class.java)

            if (dpm.isDeviceOwnerApp(context.packageName)) {
                try {
                    // 1. 🛡️ PERMIT ACCESSIBILITY SERVICES: Pass null to unblock all accessibility services (clears "Controlled by admin")
                    dpm.setPermittedAccessibilityServices(admin, null)

                    // 2. Remove user restriction on configuring accessibility so it doesn't stay greyed out in OFF state
                    try {
                        dpm.clearUserRestriction(admin, "no_config_accessibility")
                    } catch (_: Exception) {}

                    // 2b. Disable Factory Reset
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
                    Log.d("HalanoiAdmin", "🔒 Factory Reset restriction applied.")

                    // 3. Prevent OS from suspending or killing the app (Critical for OriginOS/Vivo)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        try {
                            val method = dpm.javaClass.getMethod("setControlDisabledPackages", ComponentName::class.java, List::class.java)
                            method.invoke(dpm, admin, listOf(context.packageName))
                            Log.d("HalanoiAdmin", "🔒 Halanoi marked as system-managed package (Control Disabled) via reflection.")
                        } catch (e: Exception) {
                            Log.e("HalanoiAdmin", "Failed to set control disabled packages via reflection: ${e.message}")
                        }
                    }

                    // 4. Block uninstallation to ensure persistence
                    dpm.setUninstallBlocked(admin, context.packageName, true)

                    // 4b. Auto-grant storage permissions as Device Owner
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            dpm.setPermissionGrantState(admin, context.packageName, "android.permission.READ_EXTERNAL_STORAGE", DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                            dpm.setPermissionGrantState(admin, context.packageName, "android.permission.WRITE_EXTERNAL_STORAGE", DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                        } catch (_: Exception) {}
                    }

                    // 5. Force-enable the Accessibility Service via Secure Settings
                    val serviceName = ComponentName(context, HalanoiAccessibilityService::class.java).flattenToString()
                    dpm.setSecureSetting(admin, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, serviceName)
                    dpm.setSecureSetting(admin, Settings.Secure.ACCESSIBILITY_ENABLED, "1")

                    // 6. Force VPN Lockdown (Prevents turning it off)
                    try {
                        dpm.setAlwaysOnVpnPackage(admin, context.packageName, false)
                        dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN)
                        Log.d("HalanoiAdmin", "🔒 VPN Always-On Engaged (Lockdown: OFF for Internet).")
                    } catch (e: Exception) {
                        Log.e("HalanoiAdmin", "Failed to set VPN Lockdown: ${e.message}")
                    }

                    Log.d("HalanoiAdmin", "✅ DPM policies enforced for Android 16/OriginOS.")
                } catch (e: Exception) {
                    Log.e("HalanoiAdmin", "Error enforcing DPM policies: ${e.message}")
                }
            }
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // This message pops up if they somehow try to deactivate it
        return "Vault is Locked. You cannot deactivate the Halanoi Focus Shield."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Halanoi Admin Disabled", Toast.LENGTH_SHORT).show()
    }
}
