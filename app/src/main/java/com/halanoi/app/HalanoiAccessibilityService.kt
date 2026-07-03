package com.halanoi.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.StringBuilder

class HalanoiAccessibilityService : AccessibilityService() {

    private val TAG = "HalanoiService"
    private var classifier: TFLiteClassifier? = null
    
    private var windowManager: WindowManager? = null
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    private val aiMutex = Mutex()
    
    private val MIN_CONFIDENCE_THRESHOLD = 0.70f 
    private val DANGER_LABELS = listOf("nsfw", "sports", "entertainment", "politics")
    private val EXTREME_BANNED_WORDS = emptyList<String>()

    private var blockView: View? = null
    private var isOverlayShowing = false
    private val forbiddenPhrases = emptyList<String>()

    private val blockedApps = listOf(
        "com.twitter.android", "com.zhiliaoapp.musically", "com.android.vpndialogs",
        "org.torproject.torbrowser", "org.torproject.torbrowser_alpha", "org.torproject.android",
        "com.ornet.browser", "com.opera.browser", "ch.grid.invizible", "com.brave.browser",
        "com.opera.mini.native", "com.instagram.android", "com.facebook.katana",
        "com.facebook.lite", "in.startv.hotstar", "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient", "com.snapchat.android", "com.reddit.frontpage",
        "com.jio.media.ondemand", "com.UCMobile.intl",
        "com.android.packageinstaller", "com.google.android.packageinstaller"
    )
    
    private val targetRiskApps = listOf(
        "com.android.chrome", "com.microsoft.emmx", "org.mozilla.firefox",                 
        "com.sec.android.app.sbrowser", "com.duckduckgo.mobile.android", 
        "com.google.android.googlequicksearchbox", "com.google.android.youtube"
    )

    private var lastProcessedText = ""
    private val debounceDelayMs = 300L 
    
    private var isScanning = false
    private var isOnCooldown = false
    
    private var lastFullScanTime = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupHydraBlockView()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting to load 64MB AI Brain in background...")
                classifier = TFLiteClassifier(this@HalanoiAccessibilityService)
                Log.d(TAG, "✅ TFLite Model loaded successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load TFLite Model: ${e.message}")
            }
        }
    }

    private fun setupHydraBlockView() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setBackgroundColor(android.graphics.Color.parseColor("#E6121212"))

        val tv = TextView(this)
        tv.text = "🛡️ HALANOI VAULT LOCKED 🛡️\n\nSettings and Uninstalls are currently blocked to protect your study streak."
        tv.setTextColor(android.graphics.Color.parseColor("#CF6679"))
        tv.textSize = 22f
        tv.gravity = Gravity.CENTER
        tv.setPadding(64, 64, 64, 64)

        layout.addView(tv)
        blockView = layout
    }

    private fun isUIGarbage(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        val exactMatches = listOf("type a message", "add a caption", "type here", "incognito tab", "new tab", "search or type")
        if (exactMatches.contains(lowerText)) return true
        val garbageList = listOf("mb left", "gb left", "downloading", "err_connection", "site can't be reached", "refused to connect")
        return garbageList.any { lowerText.contains(it) }
    }

    private fun isSafeContext(text: String): Boolean {
        val lowerText = text.lowercase()
        val safeWords = listOf("weather", "climate", "temperature", "forecast", "latex formation", "math", "calculus", "equation", "code", "programming", "how can i", "i am not able to", "can you make", "bro", "give as", "guv")
        return safeWords.any { lowerText.contains(it) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        if (packageName == applicationContext.packageName || packageName == "com.halanoi.app") return

        val sharedPrefs = getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
        val unlockEndTime = sharedPrefs.getLong("STUDY_UNLOCK_END_TIME", 0L)
        if (System.currentTimeMillis() < unlockEndTime) {
            hideOverlay() 
            return 
        }

        // 🛡️ ACTIVE STORE SCANNER: If user is downloading/installing apps, run background sync to hide browsers immediately
        val currentTime = System.currentTimeMillis()
        val isInstallerApp = packageName.contains("vending", ignoreCase = true) || 
                             packageName.contains("packageinstaller", ignoreCase = true) || 
                             packageName.contains("appstore", ignoreCase = true) || 
                             packageName.contains("market", ignoreCase = true) || 
                             packageName.contains("store", ignoreCase = true)
        if (isInstallerApp) {
            val lastSync = sharedPrefs.getLong("LAST_BROWSER_SYNC_TIME", 0L)
            if (currentTime - lastSync > 8000L) { // Scan every 8 seconds when active in app stores
                sharedPrefs.edit().putLong("LAST_BROWSER_SYNC_TIME", currentTime).apply()
                val activeMode = sharedPrefs.getString("BROWSER_BLOCK_MODE", "STANDARD") ?: "STANDARD"
                serviceScope.launch(Dispatchers.IO) {
                    Log.e(TAG, "🔒 App Store active. Triggering background Browser Shield sync...")
                    BrowserHelper.applyBrowserBlockMode(applicationContext, activeMode)
                }
            }
        }

        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            try {
                // UNIVERSAL MATCH: Match custom manufacturer package names (like com.samsung.android.settings or com.coloros.safecenter)
                val isGuardedApp = packageName.contains("settings", ignoreCase = true) || 
                                   packageName.contains("accessibility", ignoreCase = true) || 
                                   packageName == "com.android.vending" || 
                                   packageName.contains("packageinstaller") || 
                                   packageName == "com.android.vpndialogs"

                if (isGuardedApp) {
                    if (scanScreenForForbiddenText(rootNode)) {
                        showOverlay()
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return 
                    } else {
                        hideOverlay()
                    }
                } 

                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    val lockedApps = sharedPrefs.getStringSet("LOCKED_APPS", setOf()) ?: setOf()
                    val isUnauthorized = lockedApps.contains(packageName) || 
                                         blockedApps.contains(packageName) || 
                                         BrowserHelper.isBrowserUnauthorized(applicationContext, packageName)
                    if (isUnauthorized) {
                        // 🛡️ HYDRA AUTO-HIDE ENFORCEMENT: 
                        // If an unauthorized browser is opened, hide/disable it immediately using Device Owner.
                        // This kills the browser app instantly and removes its launcher icon.
                        if (BrowserHelper.isBrowser(applicationContext, packageName)) {
                            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                            val adminComponent = ComponentName(applicationContext, HalanoiDeviceAdminReceiver::class.java)
                            if (dpm.isDeviceOwnerApp(applicationContext.packageName)) {
                                try {
                                    dpm.setApplicationHidden(adminComponent, packageName, true)
                                    Log.e(TAG, "🔒 Aggressive Shield: Instantly hid unauthorized browser $packageName on launch.")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to hide browser on launch: ${e.message}")
                                }
                            }
                        }

                        if (!isOnCooldown) {
                            launchBlockScreen("Halanoi App Bouncer: This app/browser is blocked under your active security mode.", packageName)
                        } else {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                        return 
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in immediate checks: ${e.message}")
            } finally {
                rootNode.recycle() 
            }
        }

        if (classifier == null || isScanning || isOnCooldown) return
        if (packageName.contains("com.android.systemui") || packageName.contains("inputmethod")) return

        val isTargetRiskApp = targetRiskApps.any { packageName.contains(it) } || 
                              BrowserHelper.isBrowser(applicationContext, packageName)

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isTargetRiskApp) {
            val root = rootInActiveWindow
            if (root != null) {
                val headlineText = extractHeadlineOrUrl(root)
                val headlineWordCount = headlineText.trim().split("\\s+".toRegex()).size
                if (headlineWordCount >= 2 && !isUIGarbage(headlineText) && headlineText != lastProcessedText) {
                    lastProcessedText = headlineText
                    analyzeTextForDistractions(headlineText, packageName)
                }
                root.recycle()
            }
        }

        isScanning = true
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val delayedRoot = rootInActiveWindow
                if (delayedRoot != null) {
                    if (isTargetRiskApp) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastFullScanTime > 2500L) { 
                            lastFullScanTime = currentTime
                            val sb = StringBuilder()
                            getAllTextFromScreen(delayedRoot, sb)
                            val combinedText = sb.toString().take(1000) 
                            val wordCount = combinedText.trim().split("\\s+".toRegex()).size
                            if (wordCount >= 2 && combinedText != lastProcessedText) {
                                lastProcessedText = combinedText
                                analyzeTextForDistractions(combinedText, packageName)
                            }
                        }
                    }
                    scanScreenRecursivelyForSearchBars(delayedRoot, packageName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delayed scan crashed: ${e.message}")
            } finally {
                isScanning = false
            }
        }, debounceDelayMs)
    }

    private fun extractHeadlineOrUrl(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val isUrlBar = node.viewIdResourceName?.lowercase()?.contains("url") == true
        if (isUrlBar) return node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        var foundText = ""
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = extractHeadlineOrUrl(child)
            child.recycle() 
            if (childText.isNotBlank()) {
                foundText = childText
                break 
            }
        }
        return foundText
    }

    private fun scanScreenRecursivelyForSearchBars(node: AccessibilityNodeInfo?, packageName: String) {
        if (node == null) return
        if (!node.isVisibleToUser) {
            node.recycle() 
            return
        }
        val isSearchBar = node.isEditable || node.className?.toString()?.contains("EditText") == true
        val isUrlBar = node.viewIdResourceName?.lowercase()?.contains("url") == true
        if (isSearchBar || isUrlBar) {
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            val wordCount = text.trim().split("\\s+".toRegex()).size
            if ((wordCount >= 2 || text.length >= 8) && text != lastProcessedText) {
                lastProcessedText = text
                analyzeTextForDistractions(text, packageName)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scanScreenRecursivelyForSearchBars(child, packageName)
        }
        node.recycle() 
    }

    private fun analyzeTextForDistractions(text: String, packageName: String) {
        Log.d(TAG, "🔍 SCRAPED TEXT: '$text'")
        val lowerText = text.lowercase()
        val sharedPrefs = getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
        val customKeywords = sharedPrefs.getStringSet("CUSTOM_KEYWORDS", setOf())?.toList() ?: emptyList()
        val allKeywords = EXTREME_BANNED_WORDS + customKeywords
        
        val matchedWord = allKeywords.find { it.isNotBlank() && lowerText.contains(it.lowercase()) }
        if (matchedWord != null) {
            isOnCooldown = true
            serviceScope.launch(Dispatchers.Main) {
                launchBlockScreen("Matched keyword: '$matchedWord'", packageName)
            }
            return
        }

        if (text.trim().split("\\s+".toRegex()).size < 2 || isUIGarbage(text)) return 
        if (isSafeContext(text)) return

        serviceScope.launch {
            aiMutex.withLock {
                if (isOnCooldown) return@launch
                try {
                    val results = classifier?.classifyText(text)
                    if (results != null && results.isNotEmpty()) {
                        val topResult = results.entries.first()
                        val topLabel = topResult.key
                        val topScore = topResult.value
                        Log.d(TAG, "AI Analyzed: '$text' -> $topLabel ($topScore)")
                        if (DANGER_LABELS.contains(topLabel) && topScore >= MIN_CONFIDENCE_THRESHOLD) {
                            isOnCooldown = true
                            withContext(Dispatchers.Main) {
                                launchBlockScreen("The AI caught you looking at: $text (Label: $topLabel)", packageName)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Classification crashed: ${e.message}")
                }
            }
        }
    }

    private fun scanScreenForForbiddenText(node: AccessibilityNodeInfo): Boolean {
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val nodeTextDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (forbiddenPhrases.any { nodeText.contains(it) || nodeTextDesc.contains(it) }) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = scanScreenForForbiddenText(child)
                child.recycle() 
                if (found) return true
            }
        }
        return false
    }

    private fun getAllTextFromScreen(node: AccessibilityNodeInfo?, stringBuilder: StringBuilder) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && !isUIGarbage(text)) stringBuilder.append(text).append(" ")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                getAllTextFromScreen(child, stringBuilder)
                child.recycle() 
            }
        }
    }

    private fun showOverlay() {
        if (!isOverlayShowing && blockView != null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER
            try {
                windowManager?.addView(blockView, params)
                isOverlayShowing = true
            } catch (e: Exception) { Log.e(TAG, "Failed to draw overlay: ${e.message}") }
        }
    }

    private fun hideOverlay() {
        if (isOverlayShowing && blockView != null) {
            try { windowManager?.removeView(blockView); isOverlayShowing = false } catch (e: Exception) {}
        }
    }

    private fun launchBlockScreen(reason: String, packageName: String? = null) {
        try {
            isOnCooldown = true 
            
            // 1. OVERWRITE: Redirect to local loopback page (guaranteed to be supported by http intent filter)
            if (packageName != null && (targetRiskApps.any { packageName.contains(it) } || BrowserHelper.isBrowser(applicationContext, packageName))) {
                try {
                    val sanitizeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1"))
                    sanitizeIntent.setPackage(packageName)
                    // FLAG_ACTIVITY_CLEAR_TASK is the key to "resetting" the task
                    sanitizeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                    startActivity(sanitizeIntent)
                    Log.e(TAG, "Browser sanitized: Redirected to http://127.0.0.1 and task cleared.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sanitize browser: ${e.message}")
                }
            }

            // 2. DISMISS: Perform multiple BACK actions to exit the activity stack
            performGlobalAction(GLOBAL_ACTION_BACK)
            Handler(Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
                
                // 3. KICK: Go to home screen
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 100)

            // 4. BLOCK: Show the custom block screen
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val intent = Intent(this@HalanoiAccessibilityService, BlockScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        putExtra("BLOCK_REASON", reason) 
                    }
                    startActivity(intent)
                } catch (e: Exception) { Log.e(TAG, "Failed to start BlockScreenActivity: ${e.message}") }
            }, 500L)
            
            startCooldown()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockScreen sequence: ${e.message}")
            isOnCooldown = false 
        }
    }

    private fun startCooldown() {
        isOnCooldown = true
        Handler(Looper.getMainLooper()).postDelayed({ isOnCooldown = false }, 5000L) 
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        hideOverlay()
        classifier?.close()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, HalanoiDeviceAdminReceiver::class.java)
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                val serviceComponent = "${packageName}/${HalanoiAccessibilityService::class.java.name}"
                dpm.setSecureSetting(adminComponent, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, serviceComponent)
                dpm.setSecureSetting(adminComponent, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                Log.d(TAG, "🔒 Auto-re-enabled Accessibility Service via Device Owner Secure Settings.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-re-enable accessibility service: ${e.message}")
            }
        }
        return super.onUnbind(intent)
    }
}