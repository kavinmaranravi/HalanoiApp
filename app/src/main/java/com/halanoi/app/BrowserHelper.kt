package com.halanoi.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.util.Log
import android.os.Build

object BrowserHelper {
    
    /**
     * Dynamically queries the Package Manager to find all installed web browsers.
     * It uses a dual-query strategy to bypass device-specific intent resolution limits:
     * 1. Queries apps categorized under ACTION_MAIN and CATEGORY_APP_BROWSER.
     * 2. Queries apps resolving a standard web link with the MATCH_ALL flag.
     * 3. Scans all installed packages for browser keywords to ensure 100% detection.
     */
    fun getInstalledBrowsers(context: Context): List<String> {
        val pm = context.packageManager
        val browsers = mutableSetOf<String>()
        
        // Query 1: APP_BROWSER category (highly reliable on modern Android)
        try {
            val browserIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_BROWSER)
            }
            val list1 = pm.queryIntentActivities(browserIntent, 0)
            for (info in list1) {
                browsers.add(info.activityInfo.packageName)
            }
        } catch (e: Exception) {
            Log.e("HalanoiAdmin", "Error querying APP_BROWSER: ${e.message}")
        }
        
        // Query 2: Generic web link resolution
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            // Use MATCH_ALL to find all activities, ignoring default preference overrides
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            val list2 = pm.queryIntentActivities(webIntent, flags)
            for (info in list2) {
                browsers.add(info.activityInfo.packageName)
            }
        } catch (e: Exception) {
            Log.e("HalanoiAdmin", "Error querying web resolution: ${e.message}")
        }

        // Query 3: Keyword check on all installed packages to cover any edge cases
        try {
            val packages = pm.getInstalledPackages(0)
            val keywords = listOf(
                "browser", "chrome", "opera", "firefox", "brave", "edge", 
                "ucmobile", "ucbrowser", "kiwi", "yandex", "duckduckgo", 
                "puffin", "maxthon", "dolphin", "sleipnir", "vivaldi", "torproject", "bromite", "focus", "fennec", "ornet", "via"
            )
            for (pkg in packages) {
                val pkgName = pkg.packageName
                if (pkgName == context.packageName) continue
                val lowerPkg = pkgName.lowercase()
                if (keywords.any { lowerPkg.contains(it) }) {
                    browsers.add(pkgName)
                }
            }
        } catch (e: Exception) {
            Log.e("HalanoiAdmin", "Error listing all packages: ${e.message}")
        }
        
        return browsers.filter { it != context.packageName }.toList()
    }

    /**
     * Checks if a package name belongs to an installed browser.
     * Uses keyword checks and targeted intent queries to ensure it works instantly,
     * even before the Package Manager finishes indexing a newly installed app.
     */
    fun isBrowser(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        
        // 1. Keyword check (instant, bypasses Package Manager query delays)
        val lowerPkg = packageName.lowercase()
        val keywords = listOf(
            "browser", "chrome", "opera", "firefox", "brave", "edge", 
            "ucmobile", "ucbrowser", "kiwi", "yandex", "duckduckgo", 
            "puffin", "maxthon", "dolphin", "sleipnir", "vivaldi", "torproject", "bromite", "focus", "fennec", "ornet", "via"
        )
        if (keywords.any { lowerPkg.contains(it) }) {
            return true
        }
        
        // 2. Direct web intent test targeting the package specifically (no caching/indexing delays)
        val pm = context.packageManager
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(packageName)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            val list = pm.queryIntentActivities(webIntent, flags)
            if (list.isNotEmpty()) {
                return true
            }
        } catch (e: Exception) {}
        
        // 3. Direct APP_BROWSER category test targeting the package specifically
        try {
            val browserIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_BROWSER)
                setPackage(packageName)
            }
            val list = pm.queryIntentActivities(browserIntent, 0)
            if (list.isNotEmpty()) {
                return true
            }
        } catch (e: Exception) {}
        
        return false
    }

    /**
     * Determines if a package name is an unauthorized browser based on the active
     * BROWSER_BLOCK_MODE stored in SharedPreferences.
     * 
     * In Standard Mode: Google Chrome is controllable via the Vault (LOCKED_APPS),
     * but all alternative browsers (Opera, Brave, Firefox, etc.) are permanently unauthorized/blocked.
     */
    fun isBrowserUnauthorized(context: Context, packageName: String): Boolean {
        if (!isBrowser(context, packageName)) return false
        
        val sharedPrefs = context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
        val mode = sharedPrefs.getString("BROWSER_BLOCK_MODE", "STANDARD") ?: "STANDARD"
        
        return when (mode) {
            "ZERO_BROWSER" -> true
            "CHROME_ONLY" -> packageName != "com.android.chrome"
            else -> { // "STANDARD"
                if (packageName == "com.android.chrome") {
                    val lockedApps = sharedPrefs.getStringSet("LOCKED_APPS", setOf()) ?: setOf()
                    lockedApps.contains(packageName)
                } else {
                    true // Alternative browsers are always blocked to prevent bypasses
                }
            }
        }
    }

    /**
     * Enforces browser block mode policies dynamically by hiding/disabling browser
     * apps via the DevicePolicyManager.
     */
    fun applyBrowserBlockMode(context: Context, mode: String) {
        val sharedPrefs = context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("BROWSER_BLOCK_MODE", mode).apply()
        
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, HalanoiDeviceAdminReceiver::class.java)
        
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            try {
                val browsers = getInstalledBrowsers(context)
                Log.e("HalanoiAdmin", "applyBrowserBlockMode: mode = $mode, browsers count = ${browsers.size}")
                for (packageName in browsers) {
                    val hide = when (mode) {
                        "ZERO_BROWSER" -> true
                        "CHROME_ONLY" -> packageName != "com.android.chrome"
                        else -> { // "STANDARD"
                            if (packageName == "com.android.chrome") {
                                val lockedApps = sharedPrefs.getStringSet("LOCKED_APPS", setOf()) ?: setOf()
                                lockedApps.contains(packageName)
                            } else {
                                true // Alternative browsers are permanently blocked/hidden
                            }
                        }
                    }
                    dpm.setApplicationHidden(adminComponent, packageName, hide)
                    Log.e("HalanoiAdmin", "Browser hiding applied: $packageName -> $hide")
                }
            } catch (e: Exception) {
                Log.e("HalanoiAdmin", "Failed to apply browser hiding: ${e.message}")
            }
        } else {
            Log.e("HalanoiAdmin", "applyBrowserBlockMode: NOT DEVICE OWNER!")
        }
    }
}
