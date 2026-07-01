package com.halanoi.app

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import android.util.Log

class HalanoiBootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.e("HalanoiBoot", "Broadcast received with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            action == Intent.ACTION_REBOOT) {
            
            Log.e("HalanoiBoot", "📱 Device booted! Awakening the Hydra VPN...")
            
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent == null) {
                val startVpnIntent = Intent(context, HalanoiVpnService::class.java)
                try {
                    context.startService(startVpnIntent)
                } catch (e: Exception) {
                    Log.e("HalanoiBoot", "Failed to auto-start VPN: ${e.message}")
                }
            }

            // 🛡️ PERMANENT LIFETIME PROTECTION RE-ENFORCEMENT ON BOOT
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, HalanoiDeviceAdminReceiver::class.java)

            if (dpm.isDeviceOwnerApp(context.packageName)) {
                val sharedPrefs = context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
                
                // Dynamically re-apply browser blocking on boot
                val browserMode = sharedPrefs.getString("BROWSER_BLOCK_MODE", "STANDARD") ?: "STANDARD"
                BrowserHelper.applyBrowserBlockMode(context, browserMode)
                Log.e("HalanoiBoot", "🔒 Enforced Browser Shield mode '$browserMode' on reboot.")

                val lockedApps = sharedPrefs.getStringSet("LOCKED_APPS", emptySet()) ?: emptySet()
                if (lockedApps.isNotEmpty()) {
                    try {
                        val suspendedApps = lockedApps.toTypedArray()
                        // Instantly re-freeze apps the moment the home screen loads
                        dpm.setPackagesSuspended(adminComponent, suspendedApps, true)
                        Log.e("HalanoiBoot", "🔒 Lifetime app suspension re-enforced after reboot.")
                    } catch (e: Exception) {
                        Log.e("HalanoiBoot", "Failed to re-enforce suspension: ${e.message}")
                    }
                }

                // Force-enable Halanoi Accessibility Service on Boot
                try {
                    val serviceComponent = "${context.packageName}/${HalanoiAccessibilityService::class.java.name}"
                    dpm.setSecureSetting(adminComponent, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, serviceComponent)
                    dpm.setSecureSetting(adminComponent, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                    Log.e("HalanoiBoot", "🔒 Halanoi Accessibility Service auto-enabled on boot via Device Owner.")
                } catch (e: Exception) {
                    Log.e("HalanoiBoot", "Failed to force enable Accessibility Service on boot: ${e.message}")
                }
            }
        } else if (action == Intent.ACTION_PACKAGE_ADDED || 
                   action == Intent.ACTION_PACKAGE_REPLACED) {
            
            val packageName = intent.data?.schemeSpecificPart ?: return
            Log.e("HalanoiBoot", "📦 Package installed/updated: $packageName")
            
            if (BrowserHelper.isBrowser(context, packageName)) {
                val sharedPrefs = context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
                val mode = sharedPrefs.getString("BROWSER_BLOCK_MODE", "STANDARD") ?: "STANDARD"
                
                Log.e("HalanoiBoot", "🛡️ Detected new browser installation: $packageName. Active mode is: $mode")
                
                // 1. Direct block/hide targeting the package specifically to bypass global PM index update delays
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, HalanoiDeviceAdminReceiver::class.java)
                if (dpm.isDeviceOwnerApp(context.packageName)) {
                    val hide = when (mode) {
                        "ZERO_BROWSER" -> true
                        "CHROME_ONLY" -> packageName != "com.android.chrome"
                        else -> { // "STANDARD"
                            if (packageName == "com.android.chrome") {
                                val lockedApps = sharedPrefs.getStringSet("LOCKED_APPS", setOf()) ?: setOf()
                                lockedApps.contains(packageName)
                            } else {
                                true // Alternative browsers are always blocked
                            }
                        }
                    }
                    try {
                        dpm.setApplicationHidden(adminComponent, packageName, hide)
                        Log.e("HalanoiBoot", "🔒 Direct browser hide applied to new package: $packageName -> $hide")
                    } catch (e: Exception) {
                        Log.e("HalanoiBoot", "Failed to hide new package directly: ${e.message}")
                    }
                }
                
                // 2. Perform full sync to ensure the global lists and UI state remain correct
                BrowserHelper.applyBrowserBlockMode(context, mode)
                Log.e("HalanoiBoot", "🔒 Applied Browser Shield mode '$mode' sync for new package: $packageName")
            }
        }
    }
}