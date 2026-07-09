package com.halanoi.app

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.app.admin.DevicePolicyManager

class HalanoiVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isVpnRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastRedirectTime = 0L
    
    // THE FIX: The Thread Pool is the real hero that stops the 4-minute crash.
    // It prevents the app from opening hundreds of network threads.
    private val dnsThreadPool = Executors.newFixedThreadPool(15)

    override fun onCreate() {
        super.onCreate()
        // Removed manual startForeground() to prevent SecurityException crashes.
        // Android natively handles the VPN notification icon!
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("HalanoiVPN", "Starting Halanoi Focus Shield...")
        setupVpn()
        startAccessibilityEnforcerLoop()
        return START_STICKY
    }

    private fun setupVpn() {
        try {
            val builder = Builder()
            builder.setSession("Halanoi Focus Shield")
            builder.addAddress("10.0.0.2", 24)
            
            // IPv4 Routing
            builder.addRoute("10.0.0.3", 32)
            builder.addDnsServer("10.0.0.3") 
            
            // ❌ REMOVE OR COMMENT OUT THESE 3 LINES TO STOP THE IPV6 DROP LOOP:
            // builder.addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
            // builder.addRoute("fd00:1:fd00:1:fd00:1:fd00:2", 128)
            // builder.addDnsServer("fd00:1:fd00:1:fd00:1:fd00:2")
            
            builder.addDisallowedApplication(packageName)

            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                Log.e("HalanoiVPN", "❌ VPN Establish failed! Check VpnService.prepare()")
                isVpnRunning = false
                // Keep the service alive even if the VPN is not established yet,
                // so that the accessibility enforcer loop continues to protect the device.
                return
            }

            isVpnRunning = true
            Log.d("HalanoiVPN", "✅ VPN Established! Routing active.")

            Thread {
                processNetworkTraffic()
            }.start()

        } catch (e: Exception) {
            Log.e("HalanoiVPN", "Error setting up VPN: ${e.message}")
        }
    }

    private fun startAccessibilityEnforcerLoop() {
        val runnable = object : Runnable {
            override fun run() {
                checkAndEnforceAccessibility()
                mainHandler.postDelayed(this, 1000L) // Check and enforce every 1 second
            }
        }
        mainHandler.post(runnable)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, HalanoiAccessibilityService::class.java)
        val enabledServicesSetting = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServicesSetting.contains(expectedComponentName.flattenToString()) || 
               enabledServicesSetting.contains(expectedComponentName.flattenToShortString())
    }

    private fun isSettingsInForeground(): Boolean {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = am.getRunningTasks(1)
            if (tasks.isNotEmpty()) {
                val topPackage = tasks[0].topActivity?.packageName ?: ""
                return topPackage.contains("settings", ignoreCase = true)
            }
        } catch (e: Exception) {
            // Ignore and fallback
        }
        return false
    }

    private fun checkAndEnforceAccessibility() {
        if (!isAccessibilityServiceEnabled()) {
            val currentTime = System.currentTimeMillis()
            val inSettings = isSettingsInForeground()
            
            // If the user is in the Settings app, give them 8 seconds to toggle it.
            // If they are in any other app, redirect them immediately (every 1 second)!
            val cooldown = if (inSettings) 8000L else 1000L
            
            if (currentTime - lastRedirectTime > cooldown) {
                lastRedirectTime = currentTime
                Log.w("HalanoiVPN", "⚠️ Accessibility Service is DISABLED! Force-redirecting (cooldown: ${cooldown}ms)...")
                
                // 1. Try to self-heal (works on Android 12 and below)
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(this, HalanoiDeviceAdminReceiver::class.java)
                if (dpm.isDeviceOwnerApp(packageName)) {
                    try {
                        val serviceComponent = "${packageName}/${HalanoiAccessibilityService::class.java.name}"
                        dpm.setSecureSetting(adminComponent, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, serviceComponent)
                        dpm.setSecureSetting(adminComponent, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                        Log.d("HalanoiVPN", "🔒 Self-healed Accessibility Service via Device Owner Secure Settings.")
                    } catch (e: Exception) {
                        Log.e("HalanoiVPN", "Self-healing failed (expected on Android 13+): ${e.message}")
                    }
                }

                // 2. Force redirect to settings
                if (!isAccessibilityServiceEnabled()) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("HalanoiVPN", "Failed to force redirect to accessibility settings: ${e.message}")
                    }
                }
            }
        }
    }

    fun forceImmediateRedirect() {
        if (!isAccessibilityServiceEnabled()) {
            val currentTime = System.currentTimeMillis()
            // Only redirect if at least 1 second has passed since last redirect, to prevent spamming intents
            if (currentTime - lastRedirectTime > 1000L) {
                lastRedirectTime = currentTime
                Log.w("HalanoiVPN", "🚨 Network activity detected while Accessibility is disabled! Instant redirecting...")
                try {
                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("HalanoiVPN", "Failed to force redirect to accessibility settings: ${e.message}")
                }
            }
        }
    }

    // 🚨 THE DEATH RATTLE EXPLOIT 🚨
    // This function is called by the Android OS the millisecond the user 
    // manually disconnects the VPN, or tries to connect to a different VPN app.
    override fun onRevoke() {
        super.onRevoke()
        isVpnRunning = false
        Log.e("HalanoiVPN", "🚨 VPN REVOKED BY OS OR USER! Fighting back...")

        try {
            // Instantly deploy the un-exitable Block Screen!
            val lockIntent = Intent().apply {
                setClassName(this@HalanoiVpnService, "com.halanoi.app.BlockScreenActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                putExtra("BLOCK_REASON", "🚨 SHIELD BREACH DETECTED 🚨\n\nYou disabled the VPN or opened another one.\n\nReconnect Halanoi immediately.")
            }
            startActivity(lockIntent)
        } catch (e: Exception) {
            Log.e("HalanoiVPN", "Failed to launch block screen on revoke: ${e.message}")
        }

        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
    }

    private fun processNetworkTraffic() {
        val vpnFileDescriptor = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(vpnFileDescriptor)
        val outputStream = FileOutputStream(vpnFileDescriptor)
        
        // Connect to the Vault database to read dynamically added custom sites
        val sharedPrefs = getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
        val packet = ByteArray(32767) 

        try {
            while (isVpnRunning) {
                val length = inputStream.read(packet) 
                
                if (length > 0) {
                    val versionAndIHL = packet[0].toInt()
                    
                    if ((versionAndIHL shr 4) == 4) {
                        val ihl = versionAndIHL and 0x0F
                        val ipHeaderLength = ihl * 4
                        val protocol = packet[9].toInt() and 0xFF

                        if (protocol == 17) {
                            val destPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
                                           (packet[ipHeaderLength + 3].toInt() and 0xFF)

                            if (destPort == 53) {
                                val requestPacket = packet.copyOf(length)
                                
                                // Fetch any custom sites the user added via UI
                                val customBlockedSites = sharedPrefs.getStringSet("CUSTOM_SITES", setOf())?.toList() ?: emptyList()

                                // Use the Thread Pool so we don't melt the phone
                                dnsThreadPool.execute {
                                    HalanoiPacketEngine.handleDnsRequest(this, requestPacket, length, ipHeaderLength, outputStream, customBlockedSites)
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            Log.e("HalanoiVPN", "Tunnel closed gracefully.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isVpnRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        dnsThreadPool.shutdown() // Clean up the threads to prevent memory leaks
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
    }
}

object HalanoiPacketEngine {

    // 🛡️ PERMANENT SAFESEARCH IP MAPPING 🛡️
    // Maps search engine domains to their locked SafeSearch servers.
    private val safeSearchMap = mapOf(
        "google." to "216.239.38.120",      // forcesafesearch.google.com
        "youtube.com" to "216.239.38.120",  // restrict.youtube.com (Strict Mode)
        "bing.com" to "204.79.197.220",     // strict.bing.com
        "duckduckgo.com" to "107.20.240.232",// safe.duckduckgo.com
        "yandex." to "213.180.193.56",      // familysearch.yandex.ru
        "brave.com" to "0.0.0.0",           // Blocked: No official DNS SafeSearch IP available yet
        "yahoo." to "0.0.0.0",              // Sinkhole loophole
        "qwant.com" to "78.47.125.180"      // forcesafesearch.qwant.com
    )

    fun handleDnsRequest(vpnService: VpnService, packet: ByteArray, length: Int, ipHeaderLength: Int, outputStream: FileOutputStream, customBlockedSites: List<String>) {
        try {
            // Trigger instant redirect if accessibility is disabled
            if (vpnService is HalanoiVpnService) {
                vpnService.forceImmediateRedirect()
            }

            val udpStart = ipHeaderLength
            val dnsStart = udpStart + 8

            var i = dnsStart + 12
            var domain = ""
            while (packet[i] > 0) {
                val len = packet[i].toInt()
                for (j in 1..len) {
                    domain += packet[i + j].toInt().toChar()
                }
                domain += "."
                i += len + 1
            }
            domain = domain.dropLast(1)
            
            // 🎯 CRITICAL FIX: Find exactly where the DNS Question ends (Null byte + 2 bytes Type + 2 bytes Class)
            val questionEnd = i + 5 

            // Extract the DNS Query Type (A = 1, AAAA = 28)
            val qType = ((packet[i + 1].toInt() and 0xFF) shl 8) or (packet[i + 2].toInt() and 0xFF)

            // If it's an IPv6 query, cleanly return an empty response immediately to force IPv4
            if (qType != 1) {
                sendDnsReply(packet, questionEnd, ipHeaderLength, "0.0.0.0", qType, outputStream)
                return
            }

            // 🔥 BREAK THE INFINITE LOOP: Short-circuit Cloudflare lookups instantly
            if (domain.contains("cloudflare-dns.com", ignoreCase = true)) {
                sendDnsReply(packet, questionEnd, ipHeaderLength, "1.1.1.3", qType, outputStream)
                return
            }

            val blockedWebsites = mutableListOf(

                "twitter.com", "x.com", "instagram.com", "facebook.com", "meta.com", "tiktok.com",
                "netflix.com", "reddit.com", "primevideo.com", "twitch.tv", "hulu.com", "disneyplus.com",
                "pinterest.com", "pinimg.com", "tumblr.com", "flickr.com", "deviantart.com", "imgur.com", "vsco.co", "onlyfans.com", "fansly.com"
            )
            blockedWebsites.addAll(customBlockedSites)

            val isBlocked = blockedWebsites.any { domain.contains(it, ignoreCase = true) }
            var resolvedIp = "0.0.0.0"

            if (isBlocked) {
                Log.d("HalanoiVPN", "🚫 SINKHOLE: $domain")
                resolvedIp = "0.0.0.0"
            } else {
                
                // 🛡️ STEP 1: CHECK FOR SAFESEARCH FORCING 🛡️
                var forcedSafeSearchIp: String? = null
                for ((engine, ip) in safeSearchMap) {
                    if (domain.contains(engine, ignoreCase = true) && !domain.contains("firebase")) {
                        forcedSafeSearchIp = ip
                        break
                    }
                }

                if (forcedSafeSearchIp != null) {
                    Log.d("HalanoiVPN", "🛡️ FORCING SAFESEARCH: $domain -> $forcedSafeSearchIp")
                    resolvedIp = forcedSafeSearchIp
                } else {
                    // 🛡️ STEP 2: CLOUDFLARE FAMILY SHIELD FOR EVERYTHING ELSE WITH ROBUST NATIVE FALLBACK 🛡️
                    try {
                        val workerUrl = "https://family.cloudflare-dns.com/dns-query?name=$domain&type=A"
                        val url = URL(workerUrl)
                        val connection = url.openConnection() as HttpURLConnection
                        
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Accept", "application/dns-json")
                        connection.connectTimeout = 1200 
                        connection.readTimeout = 1200    

                        val response = connection.inputStream.bufferedReader().readText()
                        val jsonObject = JSONObject(response)
                        
                        val answers = jsonObject.optJSONArray("Answer")
                        if (answers != null && answers.length() > 0) {
                            for (k in 0 until answers.length()) {
                                val answer = answers.getJSONObject(k)
                                if (answer.optInt("type") == 1) { 
                                    resolvedIp = answer.getString("data")
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("HalanoiVPN", "DoH failed for $domain. Fail-safe: Blocking to prevent bypass.")
                        // BULLETPROOF FIX: If we can't resolve securely via Cloudflare Family, 
                        // we block the request. This prevents the "native DNS" loophole.
                        resolvedIp = "0.0.0.0"
                    }
                }
            }
            
            sendDnsReply(packet, questionEnd, ipHeaderLength, resolvedIp, qType, outputStream)

        } catch (e: Exception) {
            // Drop silently on error or timeout
        }
    }

    private fun sendDnsReply(requestPacket: ByteArray, questionEnd: Int, ipHeaderLength: Int, ipString: String, qType: Int, outputStream: FileOutputStream) {
        val udpStart = ipHeaderLength
        val dnsStart = udpStart + 8

        if (qType != 1) {
            // Send empty success payload for non-IPv4 records
            val responseSize = questionEnd
            val reply = ByteArray(responseSize)
            System.arraycopy(requestPacket, 0, reply, 0, questionEnd)

            swapIpAndPorts(reply, udpStart)

            // DNS Header Setup
            reply[dnsStart + 2] = 0x81.toByte()
            reply[dnsStart + 3] = 0x80.toByte()
            reply[dnsStart + 6] = 0x00.toByte() // Answers = 0
            reply[dnsStart + 7] = 0x00.toByte()
            clearAdditionalRecordsHeaders(reply, dnsStart)

            finalizeAndWritePacket(reply, responseSize, ipHeaderLength, udpStart, outputStream)
            return
        }

        // 🎯 CRITICAL FIX: Construct the 16-byte IPv4 answer directly after the Question section
        val responseSize = questionEnd + 16
        val reply = ByteArray(responseSize)
        System.arraycopy(requestPacket, 0, reply, 0, questionEnd)

        swapIpAndPorts(reply, udpStart)

        // DNS Header Setup
        reply[dnsStart + 2] = 0x81.toByte()
        reply[dnsStart + 3] = 0x80.toByte()
        reply[dnsStart + 6] = 0x00.toByte() // Answers = 1
        reply[dnsStart + 7] = 0x01.toByte()
        clearAdditionalRecordsHeaders(reply, dnsStart)

        // Write Answer Record structure exactly at questionEnd
        var offset = questionEnd
        reply[offset++] = 0xC0.toByte() // Name Pointer trick
        reply[offset++] = 0x0C.toByte() // Points directly back to domain name at byte offset 12
        reply[offset++] = 0x00.toByte() // Type: A
        reply[offset++] = 0x01.toByte()
        reply[offset++] = 0x00.toByte() // Class: IN
        reply[offset++] = 0x01.toByte()
        reply[offset++] = 0x00.toByte() // TTL: 300 Seconds
        reply[offset++] = 0x00.toByte()
        reply[offset++] = 0x01.toByte()
        reply[offset++] = 0x2C.toByte()
        reply[offset++] = 0x00.toByte() // Data Length: 4 bytes
        reply[offset++] = 0x04.toByte()

        val ipParts = ipString.split(".")
        if (ipParts.size == 4) {
            for (part in ipParts) {
                reply[offset++] = part.toInt().toByte()
            }
        } else {
            for (i in 0..3) reply[offset++] = 0
        }

        finalizeAndWritePacket(reply, responseSize, ipHeaderLength, udpStart, outputStream)
    }

    private fun swapIpAndPorts(reply: ByteArray, udpStart: Int) {
        // Swap Source & Destination IPs
        for (i in 0..3) {
            val temp = reply[12 + i]
            reply[12 + i] = reply[16 + i]
            reply[16 + i] = temp
        }
        // Swap Source & Destination Ports
        for (i in 0..1) {
            val temp = reply[udpStart + i]
            reply[udpStart + i] = reply[udpStart + 2 + i]
            reply[udpStart + 2 + i] = temp
        }
    }

    private fun clearAdditionalRecordsHeaders(reply: ByteArray, dnsStart: Int) {
        reply[dnsStart + 8] = 0x00.toByte()  // Authority RRs High
        reply[dnsStart + 9] = 0x00.toByte()  // Authority RRs Low
        reply[dnsStart + 10] = 0x00.toByte() // Additional RRs High (Strips EDNS0 flags)
        reply[dnsStart + 11] = 0x00.toByte() // Additional RRs Low
    }

    private fun finalizeAndWritePacket(reply: ByteArray, responseSize: Int, ipHeaderLength: Int, udpStart: Int, outputStream: FileOutputStream) {
        // Calculate and allocate overall UDP lengths
        val udpLength = responseSize - ipHeaderLength
        reply[udpStart + 4] = (udpLength shr 8).toByte()
        reply[udpStart + 5] = (udpLength and 0xFF).toByte()
        reply[udpStart + 6] = 0x00.toByte()
        reply[udpStart + 7] = 0x00.toByte()

        // Assign global IP payload size headers
        reply[2] = (responseSize shr 8).toByte()
        reply[3] = (responseSize and 0xFF).toByte()

        // Calculate explicit IP Checksum structure
        reply[10] = 0x00.toByte()
        reply[11] = 0x00.toByte()
        var ipChecksum = 0
        for (i in 0 until ipHeaderLength step 2) {
            val word = ((reply[i].toInt() and 0xFF) shl 8) or (reply[i+1].toInt() and 0xFF)
            ipChecksum += word
        }
        while ((ipChecksum shr 16) > 0) {
            ipChecksum = (ipChecksum and 0xFFFF) + (ipChecksum shr 16)
        }
        ipChecksum = ipChecksum.inv() and 0xFFFF
        reply[10] = (ipChecksum shr 8).toByte()
        reply[11] = (ipChecksum and 0xFF).toByte()

        outputStream.write(reply)
    }
}