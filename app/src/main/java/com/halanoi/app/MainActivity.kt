package com.halanoi.app

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halanoi.app.ui.theme.HalanoiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // BOOT THE C++ ENGINE
        Log.d("HalanoiBoot", HalanoiCore.initializeSovereignEngine())

        setContent {
            HalanoiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HalanoiDashboard()
                }
            }
        }
    }
}

@Composable
fun HalanoiDashboard() {
    val context = LocalContext.current
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, HalanoiDeviceAdminReceiver::class.java)
    val scrollState = rememberScrollState()
    val sharedPrefs = context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
    var browserBlockMode by remember { 
        mutableStateOf(sharedPrefs.getString("BROWSER_BLOCK_MODE", "STANDARD") ?: "STANDARD") 
    }
    
    var customSiteUrl by remember { mutableStateOf("") }
    var customKeyword by remember { mutableStateOf("") }

    // THE FIX: Automatically ask for Notification Permissions on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("HalanoiDashboard", "Notifications Enabled!")
        } else {
            Toast.makeText(context, "Please allow notifications to see the AI alerts!", Toast.LENGTH_SHORT).show()
        }
    }

    // Trigger the notification prompt the second the dashboard loads
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Check if we are Device Owner
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            Log.d("HalanoiAdmin", "✅ DEVICE OWNER STATUS CONFIRMED.")
            Toast.makeText(context, "Shield Mode: Device Owner (Max Security)", Toast.LENGTH_SHORT).show()
            try {
                // Hides uninstall button and locks the app as a system component
                dpm.setUninstallBlocked(adminComponent, context.packageName, true)

                // 🚫 freeze the accessibility settings (prevent disabling accessibility service)
                // UserManager.DISALLOW_CONFIG_ACCESSIBILITY corresponds to the string "no_config_accessibility"
                dpm.addUserRestriction(adminComponent, "no_config_accessibility")

                // 🚫 Disable Factory Reset (Grey out factory reset in settings)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                Log.d("HalanoiAdmin", "🔒 Factory Reset disabled and greyed out.")

                // 🚫 Disable Sideloading (APKs from WhatsApp, Chrome, etc.)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                Log.d("HalanoiAdmin", "🔒 Unknown sources installation disabled.")

                // 🔒 Disable control (force-stop, clear-data, etc.) for Halanoi via reflection to handle compile compatibility safely
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        val method = dpm.javaClass.getMethod("setControlDisabledPackages", ComponentName::class.java, List::class.java)
                        method.invoke(dpm, adminComponent, listOf(context.packageName))
                        Log.d("HalanoiAdmin", "🔒 Halanoi marked as system-managed package (Control Disabled) via reflection.")
                    } catch (e: Exception) {
                        Log.e("HalanoiAdmin", "Failed to set control disabled packages via reflection: ${e.message}")
                    }
                }

                // 🛡️ LOCK ACCESSIBILITY TOGGLES: Clear the whitelist to completely freeze and grey out Halanoi's toggle!
                try {
                    dpm.setPermittedAccessibilityServices(adminComponent, listOf())
                    Log.d("HalanoiAdmin", "🔒 Permitted accessibility services locked down (Clear Whitelist to force grey-out).")
                } catch (e: Exception) {
                    Log.e("HalanoiAdmin", "Failed to restrict permitted accessibility services: ${e.message}")
                }

                // 🛡️ Lock VPN - force always-on VPN package
                // Lockdown parameter is set to FALSE to allow internet to flow while VPN is active.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        dpm.setAlwaysOnVpnPackage(adminComponent, context.packageName, false)
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_VPN)
                        Log.d("HalanoiAdmin", "🔒 Always-On VPN enforced. VPN Settings greyed out. Internet should work.")
                    } catch (e: Exception) {
                        Log.e("HalanoiAdmin", "Failed to set Always-On VPN/Restriction: ${e.message}")
                    }
                }

                // Automatically activate VPN Shield if we are Device Owner
                startHalanoiVpn(context)

                // Automatically enforce browser shield on boot/load
                BrowserHelper.applyBrowserBlockMode(context, browserBlockMode)
            } catch (e: SecurityException) {
                Log.e("HalanoiAdmin", "Failed to secure app uninstallation/restrictions: ${e.message}")
            }
        } else {
            Log.e("HalanoiAdmin", "❌ NOT DEVICE OWNER. Grey-out features will not work.")
            Toast.makeText(context, "Shield Mode: Active Admin Only (Reduced Security)", Toast.LENGTH_LONG).show()
        }
    }

    // 1. VPN Launcher 
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startHalanoiVpn(context)
            Toast.makeText(context, "Network Shield Activated! 🔥", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Halanoi Sovereign",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ADD APPS TO VAULT BUTTON
        Button(
            onClick = {
                val intent = Intent(context, AppSelectorActivity::class.java)
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp)
        ) {
            Text("Add Apps to Vault 🔒", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        


        // NOTES & TIMELINE BUTTON
        Button(
            onClick = {
                val intent = Intent(context, NotesTimelineActivity::class.java)
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp)
        ) {
            Text("Activity & Notes 📝", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // START VPN BUTTON
        OutlinedButton(
            onClick = {
                // 🛡️ THE FIX: Grant 15 seconds of Safe Passage!
                val sharedPrefs = context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
                sharedPrefs.edit().putLong("SAFE_PASSAGE_TIME", System.currentTimeMillis()).apply()

                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    vpnPermissionLauncher.launch(vpnIntent)
                } else {
                    startHalanoiVpn(context)
                    Toast.makeText(context, "Network Shield Activated! 🔥", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp)
        ) {
            Text("Activate Network Shield 🛡️")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // TOTAL LOCKDOWN BUTTON
        Button(
            onClick = {
                if (dpm.isAdminActive(adminComponent)) {
                    try {
                        dpm.lockNow()
                        Toast.makeText(context, "Vault Locked.", Toast.LENGTH_SHORT).show()
                    } catch (e: SecurityException) {
                        Log.e("HalanoiAdmin", "Lock failed: ${e.message}")
                    }
                } else {
                    // If not admin, request it (Standard way as fallback)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Halanoi requires Admin rights to lock the vault.")
                    }
                    context.startActivity(intent)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp)
        ) {
            Text("Initiate Total Lockdown 🔒", color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(16.dp))

        // --- BROWSER LOCKDOWN SHIELD ---
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Browser Lockdown Shield 🛡️",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Control web browser access to block distractions.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                val modes = listOf(
                    Triple("STANDARD", "Standard Mode", "Block only selected vault apps"),
                    Triple("CHROME_ONLY", "Chrome-Only Mode", "Disable all browsers except Chrome"),
                    Triple("ZERO_BROWSER", "Zero-Browser Mode", "Disable all web browsers completely")
                )

                modes.forEach { (modeVal, modeTitle, modeDesc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (browserBlockMode == modeVal),
                            onClick = {
                                browserBlockMode = modeVal
                                BrowserHelper.applyBrowserBlockMode(context, modeVal)
                            }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(text = modeTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(text = modeDesc, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(16.dp))
        
        // --- CUSTOM SITE BLOCKER ---
        Text("Custom Site Blocker", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = customSiteUrl,
            onValueChange = { customSiteUrl = it },
            label = { Text("Enter domain (e.g. reddit.com)") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Button(
            onClick = {
                if (customSiteUrl.isNotBlank()) {
                    val sharedPrefs = context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
                    val customSites = sharedPrefs.getStringSet("CUSTOM_SITES", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                    customSites.add(customSiteUrl.trim().lowercase())
                    sharedPrefs.edit().putStringSet("CUSTOM_SITES", customSites).apply()
                    Toast.makeText(context, "Blocked Site: $customSiteUrl", Toast.LENGTH_SHORT).show()
                    customSiteUrl = ""
                }
            },
            modifier = Modifier.fillMaxWidth(0.7f).padding(top = 8.dp)
        ) {
            Text("Block Domain 🚫")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- CUSTOM KEYWORD BLOCKER ---
        Text("Custom Keyword Blocker", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = customKeyword,
            onValueChange = { customKeyword = it },
            label = { Text("Enter keyword (e.g. skibidi)") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Button(
            onClick = {
                if (customKeyword.isNotBlank()) {
                    val sharedPrefs = context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)
                    val customKeywords = sharedPrefs.getStringSet("CUSTOM_KEYWORDS", setOf())?.toMutableSet() ?: mutableSetOf()
                    customKeywords.add(customKeyword.trim().lowercase())
                    sharedPrefs.edit().putStringSet("CUSTOM_KEYWORDS", customKeywords).apply()
                    Toast.makeText(context, "Blocked Keyword: $customKeyword", Toast.LENGTH_SHORT).show()
                    customKeyword = ""
                }
            },
            modifier = Modifier.fillMaxWidth(0.7f).padding(top = 8.dp)
        ) {
            Text("Block Keyword 🎯")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kavinmaranravi/HalanoiApp"))
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292F)),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp)
        ) {
            Text("Star us on GitHub ⭐", color = Color.White, fontSize = 14.sp)
        }

        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    try {
                        // Remove all restrictions before removing device owner to restore device to normal
                        dpm.setUninstallBlocked(adminComponent, context.packageName, false)
                        dpm.clearUserRestriction(adminComponent, "no_config_accessibility")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                        }
                        dpm.clearDeviceOwnerApp(context.packageName)
                        Toast.makeText(context, "Device Owner Deactivated successfully! 🎉", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Deactivation failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp)
            ) {
                Text("Deactivate Device Owner 🔓", color = Color.White, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "AI Vision & Text Sniper are active via Accessibility Service.",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

fun startHalanoiVpn(context: Context) {
    val intent = Intent(context, HalanoiVpnService::class.java)
    context.startService(intent)
}