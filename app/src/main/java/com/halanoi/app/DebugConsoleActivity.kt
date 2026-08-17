package com.halanoi.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halanoi.app.ui.theme.HalanoiTheme

class DebugConsoleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HalanoiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DebugConsoleScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, HalanoiDeviceAdminReceiver::class.java)
    val sharedPrefs = context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE)

    var showDeactivateDialog by remember { mutableStateOf(false) }
    var liveLogs by remember { mutableStateOf(AppLogManager.getLogs()) }
    var isVpnActive by remember { mutableStateOf(false) }

    // Check if the service is running when this screen opens or refreshes
    LaunchedEffect(liveLogs) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        isVpnActive = manager.getRunningServices(Integer.MAX_VALUE).any { 
            HalanoiVpnService::class.java.name == it.service.className 
        }
    }

    val customSites = remember {
        mutableStateListOf<String>().apply {
            addAll(sharedPrefs.getStringSet("CUSTOM_SITES", setOf())?.toList()?.sorted() ?: emptyList())
        }
    }
    
    val customKeywords = remember {
        mutableStateListOf<String>().apply {
            addAll(sharedPrefs.getStringSet("CUSTOM_KEYWORDS", setOf())?.toList()?.sorted() ?: emptyList())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Debug Console", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val systemBlockedWebsites = remember {
                listOf(
                    "twitter.com", "x.com", "instagram.com", "facebook.com", "meta.com", "tiktok.com",
                    "netflix.com", "reddit.com", "primevideo.com", "twitch.tv", "hulu.com", "disneyplus.com",
                    "pinterest.com", "pinimg.com", "tumblr.com", "flickr.com", "deviantart.com", "imgur.com", "vsco.co"
                ).sorted()
            }

            // Section: Blocked Websites
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Blocked Websites (${customSites.size + systemBlockedWebsites.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (customSites.isNotEmpty()) {
                            Text("Custom Blocks:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            customSites.forEach { site ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• $site", fontSize = 14.sp)
                                    Button(
                                        onClick = {
                                            val updated = sharedPrefs.getStringSet("CUSTOM_SITES", setOf())?.toMutableSet() ?: mutableSetOf()
                                            updated.remove(site)
                                            sharedPrefs.edit().putStringSet("CUSTOM_SITES", updated).apply()
                                            customSites.remove(site)
                                            Toast.makeText(context, "Removed site: $site", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Delete", fontSize = 11.sp)
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        Text("System Defaults (Permanent):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
                        systemBlockedWebsites.forEach { site ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("• $site", fontSize = 13.sp, color = Color.Gray)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("System", fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Section: Blocked Keywords
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Blocked Keywords & Topics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (customKeywords.isNotEmpty()) {
                            Text("Custom Keywords:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            customKeywords.forEach { keyword ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• $keyword", fontSize = 14.sp)
                                    Button(
                                        onClick = {
                                            val updated = sharedPrefs.getStringSet("CUSTOM_KEYWORDS", setOf())?.toMutableSet() ?: mutableSetOf()
                                            updated.remove(keyword)
                                            sharedPrefs.edit().putStringSet("CUSTOM_KEYWORDS", updated).apply()
                                            customKeywords.remove(keyword)
                                            Toast.makeText(context, "Removed keyword: $keyword", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Delete", fontSize = 11.sp)
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        Text("System AI Vision Scanner Topics:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
                        listOf("NSFW (Adult content/Porn)", "Sports (News/Scores)", "Entertainment (Gaming/Movies)", "Politics (Debates)").forEach { category ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("• $category", fontSize = 13.sp, color = Color.Gray)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("AI Brain", fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Section: Interactive Logs Console
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Interactive Logs Console 📜",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Button(
                            onClick = { liveLogs = AppLogManager.getLogs() },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Refresh 🔄", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        val logScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(logScrollState)
                        ) {
                            if (!isVpnActive) {
                                Text(
                                    text = "⚠️ VPN Network Shield is NOT Active!\n\nPlease go back to the main screen and click \"Activate Network Shield 🛡️\" to start the VPN. Once active, browser DNS queries will record logs here.",
                                    color = Color(0xFFFBBF24), // Orange/yellow alert
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else if (liveLogs.isEmpty()) {
                                Text(
                                    text = "Console active. Open Chrome/Websites to see live logs.",
                                    color = Color(0xFF10B981),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                liveLogs.forEach { logLine ->
                                    Text(
                                        text = logLine,
                                        color = if (logLine.contains("SINKHOLE")) Color(0xFFEF4444) else Color(0xFF34D399),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action: Deactivate Device Owner
            Button(
                onClick = { showDeactivateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Deactivate Device Owner 🔓", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Confirmation dialog to prevent accidental deactivation
    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = { Text("Confirm Deactivation") },
            text = { Text("Are you sure you want to deactivate Device Owner privileges? This will clear all focus locks and administrative restrictions.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeactivateDialog = false
                        try {
                            dpm.setUninstallBlocked(adminComponent, context.packageName, false)
                            dpm.clearUserRestriction(adminComponent, "no_config_accessibility")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                            }
                            dpm.clearDeviceOwnerApp(context.packageName)
                            Toast.makeText(context, "Device Owner Deactivated successfully! 🎉", Toast.LENGTH_LONG).show()
                            onBack()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Deactivation failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Deactivate", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
