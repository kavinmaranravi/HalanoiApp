package com.halanoi.app

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class HalanoiScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, HalanoiDeviceAdminReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e("HalanoiSchedule", "⚠️ Cannot enforce schedule: App is not Device Owner!")
            return
        }

        val sharedPrefs = context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
        val lockedApps = sharedPrefs.getStringSet("LOCKED_APPS", emptySet()) ?: emptySet()
        if (lockedApps.isEmpty()) return

        when (action) {
            "com.halanoi.app.START_SESSION" -> {
                Log.d("HalanoiSchedule", "🔒 Time's up! Activating scheduled lock...")
                try {
                    val suspendedApps = lockedApps.toTypedArray()
                    dpm.setPackagesSuspended(adminComponent, suspendedApps, true)
                    dpm.setUninstallBlocked(adminComponent, context.packageName, true)
                    Toast.makeText(context, "🛡️ Study Session Started! Apps Locked.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("HalanoiSchedule", "Failed to suspend apps: ${e.message}")
                }
            }

            "com.halanoi.app.END_SESSION" -> {
                Log.d("HalanoiSchedule", "🔓 Session over! Restoring access...")
                try {
                    val suspendedApps = lockedApps.toTypedArray()
                    dpm.setPackagesSuspended(adminComponent, suspendedApps, false)
                    dpm.setUninstallBlocked(adminComponent, context.packageName, false)
                    Toast.makeText(context, "🎉 Session Complete! Enjoy your break.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("HalanoiSchedule", "Failed to lift suspension: ${e.message}")
                }
            }
        }
    }
}
