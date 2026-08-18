package com.halanoi.app

import com.halanoi.app.BuildConfig
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halanoi.app.ui.BrandIconHelper
import com.halanoi.app.ui.SovereignAdminPolicyCard
import com.halanoi.app.ui.WebsiteFavicon
import com.halanoi.app.ui.theme.*

enum class DebugSubScreen {
    HUB, SITES, KEYWORDS, AI_TOPICS, LOGS
}

class DebugConsoleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HalanoiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DebugConsoleScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminComponent = remember { ComponentName(context, HalanoiDeviceAdminReceiver::class.java) }
    val sharedPrefs = remember { context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE) }

    var currentSubScreen by remember { mutableStateOf(DebugSubScreen.HUB) }
    var isVpnActive by remember { mutableStateOf(false) }
    var liveLogs by remember { mutableStateOf(emptyList<String>()) }
    var showDeactivateDialog by remember { mutableStateOf(false) }

    val systemBlockedWebsites = remember {
        listOf(
            "twitter.com", "x.com", "instagram.com", "facebook.com", "meta.com", "tiktok.com",
            "netflix.com", "reddit.com", "primevideo.com", "twitch.tv", "hulu.com", "disneyplus.com",
            "pinterest.com", "pinimg.com", "tumblr.com", "flickr.com", "deviantart.com", "imgur.com", "vsco.co"
        ).sorted()
    }

    LaunchedEffect(Unit) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        isVpnActive = manager.getRunningServices(Integer.MAX_VALUE).any {
            HalanoiVpnService::class.java.name == it.service.className
        }
        liveLogs = AppLogManager.getLogs()
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

    Crossfade(targetState = currentSubScreen, label = "DebugSubScreenTransition") { screen ->
        when (screen) {
            DebugSubScreen.HUB -> {
                DebugConsoleHubScreen(
                    totalSitesCount = customSites.size + systemBlockedWebsites.size,
                    customKeywordsCount = customKeywords.size,
                    isVpnActive = isVpnActive,
                    onBack = onBack,
                    onNavigateToSites = { currentSubScreen = DebugSubScreen.SITES },
                    onNavigateToKeywords = { currentSubScreen = DebugSubScreen.KEYWORDS },
                    onNavigateToAiTopics = { currentSubScreen = DebugSubScreen.AI_TOPICS },
                    onNavigateToLogs = { 
                        liveLogs = AppLogManager.getLogs()
                        currentSubScreen = DebugSubScreen.LOGS 
                    },
                    onDeactivateClicked = { showDeactivateDialog = true }
                )
            }
            DebugSubScreen.SITES -> {
                DebugSitesInspectorScreen(
                    customSites = customSites,
                    systemBlockedWebsites = systemBlockedWebsites,
                    onBack = { currentSubScreen = DebugSubScreen.HUB },
                    onDeleteSite = { site ->
                        val updated = sharedPrefs.getStringSet("CUSTOM_SITES", setOf())?.toMutableSet() ?: mutableSetOf()
                        updated.remove(site)
                        sharedPrefs.edit().putStringSet("CUSTOM_SITES", updated).apply()
                        customSites.remove(site)
                        Toast.makeText(context, "Removed site: $site", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            DebugSubScreen.KEYWORDS -> {
                DebugKeywordsInspectorScreen(
                    customKeywords = customKeywords,
                    onBack = { currentSubScreen = DebugSubScreen.HUB },
                    onDeleteKeyword = { keyword ->
                        val updated = sharedPrefs.getStringSet("CUSTOM_KEYWORDS", setOf())?.toMutableSet() ?: mutableSetOf()
                        updated.remove(keyword)
                        sharedPrefs.edit().putStringSet("CUSTOM_KEYWORDS", updated).apply()
                        customKeywords.remove(keyword)
                        Toast.makeText(context, "Removed keyword: $keyword", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            DebugSubScreen.AI_TOPICS -> {
                DebugAiTopicsInspectorScreen(
                    onBack = { currentSubScreen = DebugSubScreen.HUB }
                )
            }
            DebugSubScreen.LOGS -> {
                DebugLogsFullScreenConsole(
                    isVpnActive = isVpnActive,
                    logs = liveLogs,
                    onRefresh = { liveLogs = AppLogManager.getLogs() },
                    onBack = { currentSubScreen = DebugSubScreen.HUB }
                )
            }
        }
    }

    // Confirmation dialog
    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = { Text("Confirm Deactivation", fontWeight = FontWeight.Bold) },
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
                    Text("Deactivate", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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

// --- HUB SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleHubScreen(
    totalSitesCount: Int,
    customKeywordsCount: Int,
    isVpnActive: Boolean,
    onBack: () -> Unit,
    onNavigateToSites: () -> Unit,
    onNavigateToKeywords: () -> Unit,
    onNavigateToAiTopics: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onDeactivateClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Developer Debug Console", 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "System Diagnostics & Telemetry",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Button 1: Blocked Websites Inspector
            DebugActionCard(
                title = "Blocked Websites Inspector",
                description = "Inspect active DNS sinkholed domains, custom blocks & permanent rules.",
                badgeText = "$totalSitesCount Domains",
                badgeColor = NeonCyan,
                iconEmoji = "🌐",
                iconBgColor = NeonCyan.copy(alpha = 0.15f),
                onClick = onNavigateToSites
            )

            // Button 2: Blocked Keywords Inspector
            DebugActionCard(
                title = "Blocked Keywords Inspector",
                description = "Inspect OCR screen sniper phrase triggers and match patterns.",
                badgeText = "$customKeywordsCount Keywords",
                badgeColor = RoyalViolet,
                iconEmoji = "🎯",
                iconBgColor = RoyalViolet.copy(alpha = 0.15f),
                onClick = onNavigateToKeywords
            )

            // Button 3: AI Vision Scanner Topics
            DebugActionCard(
                title = "AI Vision Scanner Topics",
                description = "Inspect on-device neural vision classifier models & threshold metrics.",
                badgeText = "4 Models Active",
                badgeColor = CyberEmerald,
                iconEmoji = "🧠",
                iconBgColor = CyberEmerald.copy(alpha = 0.15f),
                onClick = onNavigateToAiTopics
            )

            // Button 4: Interactive Live Logs Console
            DebugActionCard(
                title = "Interactive Logs Console 📜",
                description = "Open real-time cyber terminal showing live DNS requests, OCR scrapes & AI logs.",
                badgeText = if (isVpnActive) "VPN Stream Active" else "VPN Inactive",
                badgeColor = if (isVpnActive) CyberEmerald else AmberWarning,
                iconEmoji = "💻",
                iconBgColor = (if (isVpnActive) CyberEmerald else AmberWarning).copy(alpha = 0.15f),
                onClick = onNavigateToLogs
            )

            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(10.dp))
                // Deactivate Device Owner
                Button(
                    onClick = onDeactivateClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Deactivate Device Owner 🔓", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
            } else {
                Spacer(modifier = Modifier.height(14.dp))
                SovereignAdminPolicyCard()
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun DebugActionCard(
    title: String,
    description: String,
    badgeText: String,
    badgeColor: Color,
    iconEmoji: String,
    iconBgColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(text = iconEmoji, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// --- FULL SCREEN SITES INSPECTOR ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSitesInspectorScreen(
    customSites: List<String>,
    systemBlockedWebsites: List<String>,
    onBack: () -> Unit,
    onDeleteSite: (String) -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = { Text("Blocked Websites Telemetry", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NeonCyan.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${customSites.size + systemBlockedWebsites.size} Total",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            if (customSites.isNotEmpty()) {
                if (BuildConfig.DEBUG) {
                    item {
                        Text(
                            text = "Custom User Overrides (${customSites.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(customSites) { site ->
                        val brand = BrandIconHelper.getWebsiteBrand(site)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, brand.brandColor.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(brand.iconEmoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(brand.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(site, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteSite(site) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = DangerCrimson, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    // Release Mode: Summary Card (No delete buttons)
                    item {
                        val context = LocalContext.current
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("🔒", fontSize = 18.sp)
                                        Text(
                                            text = "${customSites.size} Custom Websites Blocked",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = CyberEmerald
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CyberEmerald.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "RELEASE ENFORCED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberEmerald,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "To ensure unbreakable focus, custom URL deletion is locked in the Release build. To inspect full URLs or delete items, please install the Halanoi Debug APK.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                FilledTonalButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kavinmaranravi/HalanoiApp/releases"))
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Download Debug APK for Editing ⬇️", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Permanent System Core Rules (${systemBlockedWebsites.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            items(systemBlockedWebsites) { site ->
                val brand = BrandIconHelper.getWebsiteBrand(site)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(brand.iconEmoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(brand.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(site, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = brand.category,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- FULL SCREEN KEYWORDS INSPECTOR ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugKeywordsInspectorScreen(
    customKeywords: List<String>,
    onBack: () -> Unit,
    onDeleteKeyword: (String) -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = { Text("Blocked Keywords Telemetry", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = RoyalViolet.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${customKeywords.size} Custom",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoyalViolet,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            if (customKeywords.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No custom keywords currently configured.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            } else if (BuildConfig.DEBUG) {
                items(customKeywords) { kw ->
                    val tag = BrandIconHelper.getKeywordTag(kw)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, tag.tagColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tag.iconEmoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(kw, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(tag.category, fontSize = 10.sp, color = tag.tagColor)
                            }
                            IconButton(onClick = { onDeleteKeyword(kw) }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = DangerCrimson, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else {
                // Release Mode: Summary Card (No delete buttons)
                item {
                    val context = LocalContext.current
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RoyalViolet.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🎯", fontSize = 18.sp)
                                    Text(
                                        text = "${customKeywords.size} Custom Keywords Active",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = RoyalViolet
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = RoyalViolet.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "AI VISION ACTIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoyalViolet,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "All screen keywords are active in the OCR sniper. Custom keyword deletion is restricted in Release mode. To edit or delete keyword triggers, please install the Halanoi Debug APK.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kavinmaranravi/HalanoiApp/releases"))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Download Debug APK for Editing ⬇️", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- FULL SCREEN AI TOPICS INSPECTOR ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugAiTopicsInspectorScreen(onBack: () -> Unit) {
    BackHandler(enabled = true) { onBack() }

    val aiTopics = listOf(
        Triple("🔞 Adult / NSFW Vision Engine", "High-precision on-device mobile neural classifier detecting erotic visual features.", "Confidence Threshold: 85%"),
        Triple("🎮 Gaming & Streaming Engine", "HUD gameplay detection scanning canvas framerates and UI health bars.", "Confidence Threshold: 90%"),
        Triple("⚽ Sports Radar Engine", "Live scoreboard detector scanning cricket and football tickers.", "Confidence Threshold: 80%"),
        Triple("📰 Political Rhetoric Engine", "Partisan headline and polarizing political talk detection.", "Confidence Threshold: 80%")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = { Text("AI Vision Topics Telemetry", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            items(aiTopics) { (title, desc, status) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- FULL SCREEN LIVE LOGS CONSOLE ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsFullScreenConsole(
    isVpnActive: Boolean,
    logs: List<String>,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = { Text("Live Terminal Stream 📜", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberEmerald)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF070B14))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            val logScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(logScrollState)
            ) {
                if (!isVpnActive) {
                    Text(
                        text = "⚠️ VPN Network Shield is NOT Active!\n\nPlease engage the Master Shield Orb on the home screen to activate DNS logging.",
                        color = AmberWarning,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (logs.isEmpty()) {
                    Text(
                        text = "Console active. Open Chrome/Websites to see live network telemetry.",
                        color = CyberEmerald,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    logs.forEach { logLine ->
                        val logColor = when {
                            logLine.contains("SINKHOLE") -> DangerCrimson
                            logLine.contains("SAFESEARCH") -> NeonCyan
                            logLine.contains("AI RESULT") -> CyberEmerald
                            logLine.contains("SCRAPED") -> AmberWarning
                            else -> Color(0xFF94A3B8)
                        }
                        Text(
                            text = logLine,
                            color = logColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
