package com.halanoi.app

import com.halanoi.app.BuildConfig
import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.halanoi.app.ui.BrandIconHelper
import com.halanoi.app.ui.SovereignAdminPolicyCard
import com.halanoi.app.ui.WebsiteFavicon
import com.halanoi.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SpaTab(val label: String, val icon: ImageVector) {
    SHIELD("Shield", Icons.Default.Lock),
    VAULT("Vault", Icons.Default.Home),
    FILTERS("Filters", Icons.Default.Search),
    ACTIVITY("Activity", Icons.Default.Edit)
}

class MainActivity : ComponentActivity() {

    private val notesViewModel: NotesTimelineViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = AppDatabase.getDatabase(applicationContext)
                return NotesTimelineViewModel(database.appDao()) as T
            }
        }
    }

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
                    HalanoiSpaApp(notesViewModel = notesViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HalanoiSpaApp(notesViewModel: NotesTimelineViewModel) {
    val context = LocalContext.current
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminComponent = remember { ComponentName(context, HalanoiDeviceAdminReceiver::class.java) }
    val sharedPrefs = remember { context.getSharedPreferences("HalanoiVault", Context.MODE_PRIVATE) }

    var currentTab by remember { mutableStateOf(SpaTab.SHIELD) }
    var isVpnActive by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(true) }
    var isDeviceOwner by remember { mutableStateOf(false) }
    var showAdminSheet by remember { mutableStateOf(false) }
    var isFullScreenEditorActive by remember { mutableStateOf(false) }

    var browserBlockMode by remember { 
        mutableStateOf(sharedPrefs.getString("BROWSER_BLOCK_MODE", "STANDARD") ?: "STANDARD") 
    }

    // Shared sets
    val lockedAppsSet = remember {
        mutableStateListOf<String>().apply {
            addAll(sharedPrefs.getStringSet("LOCKED_APPS", setOf()) ?: emptySet())
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

    // Notification Permission Launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Please enable notifications for focus alerts!", Toast.LENGTH_SHORT).show()
        }
    }

    // VPN Permission Launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startHalanoiVpn(context)
            isVpnActive = true
            Toast.makeText(context, "Halanoi Shield Engaged! 🔥", Toast.LENGTH_SHORT).show()
        }
    }

    // File Picker for Bulk Import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val fileContent = inputStream.bufferedReader().use { it.readText() }
                    val websites = parseSection(fileContent, "=== CUSTOM BLOCKED WEBSITES ===")
                    val keywords = parseKeywords(fileContent)
                    val apps = parseSection(fileContent, "=== LOCKED APPS ===")

                    val editor = sharedPrefs.edit()
                    if (websites.isNotEmpty()) {
                        val current = sharedPrefs.getStringSet("CUSTOM_SITES", setOf())?.toMutableSet() ?: mutableSetOf()
                        current.addAll(websites)
                        editor.putStringSet("CUSTOM_SITES", current)
                        customSites.clear()
                        customSites.addAll(current.sorted())
                    }
                    if (keywords.isNotEmpty()) {
                        val current = sharedPrefs.getStringSet("CUSTOM_KEYWORDS", setOf())?.toMutableSet() ?: mutableSetOf()
                        current.addAll(keywords)
                        editor.putStringSet("CUSTOM_KEYWORDS", current)
                        customKeywords.clear()
                        customKeywords.addAll(current.sorted())
                    }
                    if (apps.isNotEmpty()) {
                        val current = sharedPrefs.getStringSet("LOCKED_APPS", setOf())?.toMutableSet() ?: mutableSetOf()
                        current.addAll(apps)
                        editor.putStringSet("LOCKED_APPS", current)
                        lockedAppsSet.clear()
                        lockedAppsSet.addAll(current)
                    }
                    editor.apply()
                    Toast.makeText(context, "Imported ${websites.size} sites, ${keywords.size} keywords, ${apps.size} apps!", Toast.LENGTH_LONG).show()
                    startHalanoiVpn(context)
                    isVpnActive = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Lifecycle check loop
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
        if (isDeviceOwner) {
            try {
                dpm.setUninstallBlocked(adminComponent, context.packageName, true)
                dpm.addUserRestriction(adminComponent, "no_config_accessibility")
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                try {
                    dpm.setPermittedAccessibilityServices(adminComponent, listOf())
                } catch (e: Exception) {
                    Log.e("HalanoiAdmin", "Permitted services error: ${e.message}")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        dpm.setAlwaysOnVpnPackage(adminComponent, context.packageName, false)
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_VPN)
                    } catch (e: Exception) {
                        Log.e("HalanoiAdmin", "Always-on VPN error: ${e.message}")
                    }
                }
                startHalanoiVpn(context)
                isVpnActive = true
            } catch (e: Exception) {
                Log.e("HalanoiAdmin", "Init error: ${e.message}")
            }
        }

        while (true) {
            val expectedComponent = ComponentName(context, HalanoiAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            isAccessibilityEnabled = enabledServices.contains(expectedComponent.flattenToString()) ||
                                     enabledServices.contains(expectedComponent.flattenToShortString())

            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            isVpnActive = manager.getRunningServices(Integer.MAX_VALUE).any {
                HalanoiVpnService::class.java.name == it.service.className
            }

            kotlinx.coroutines.delay(1500)
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreenEditorActive) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isVpnActive && isAccessibilityEnabled) CyberEmerald else AmberWarning)
                            )
                            Text(
                                text = "Halanoi Sovereign",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDeviceOwner) CyberEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = if (isDeviceOwner) "ADMIN 🔒" else "ACTIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDeviceOwner) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        IconButton(onClick = { showAdminSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Admin & Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (!isFullScreenEditorActive) {
                // Floating Glass Dock
                FloatingGlassBottomDock(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullScreenEditorActive) PaddingValues(0.dp) else innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "SpaTabAnimation"
            ) { tab ->
                when (tab) {
                    SpaTab.SHIELD -> ShieldMasterTab(
                        isVpnActive = isVpnActive,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        isDeviceOwner = isDeviceOwner,
                        browserBlockMode = browserBlockMode,
                        onBrowserModeChange = { mode ->
                            browserBlockMode = mode
                            BrowserHelper.applyBrowserBlockMode(context, mode)
                        },
                        onToggleMasterShield = {
                            sharedPrefs.edit().putLong("SAFE_PASSAGE_TIME", System.currentTimeMillis()).apply()
                            val vpnIntent = VpnService.prepare(context)
                            if (vpnIntent != null) {
                                vpnPermissionLauncher.launch(vpnIntent)
                            } else {
                                startHalanoiVpn(context)
                                isVpnActive = true
                                Toast.makeText(context, "Halanoi Shield Engaged! 🔥", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onEmergencyLock = {
                            if (dpm.isAdminActive(adminComponent)) {
                                try {
                                    dpm.lockNow()
                                    Toast.makeText(context, "Device Locked.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Log.e("HalanoiAdmin", "Lock failed: ${e.message}")
                                }
                            } else {
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Halanoi requires Admin rights to lock.")
                                }
                                context.startActivity(intent)
                            }
                        },
                        onOpenAccessibilitySettings = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open settings: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    SpaTab.VAULT -> EmbeddedAppVaultTab(
                        lockedAppsSet = lockedAppsSet,
                        onLockApp = { pkg ->
                            if (lockedAppsSet.contains(pkg)) {
                                lockedAppsSet.remove(pkg)
                                sharedPrefs.edit().putStringSet("LOCKED_APPS", lockedAppsSet.toSet()).apply()
                                Toast.makeText(context, "App Unlocked 🔓", Toast.LENGTH_SHORT).show()
                            } else {
                                lockedAppsSet.add(pkg)
                                sharedPrefs.edit().putStringSet("LOCKED_APPS", lockedAppsSet.toSet()).apply()
                                Toast.makeText(context, "App Locked in Vault 🔒", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    SpaTab.FILTERS -> EmbeddedFiltersTab(
                        customSites = customSites,
                        customKeywords = customKeywords,
                        onAddSite = { site ->
                            val clean = site.trim().lowercase()
                            val set = sharedPrefs.getStringSet("CUSTOM_SITES", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                            set.add(clean)
                            sharedPrefs.edit().putStringSet("CUSTOM_SITES", set).apply()
                            if (!customSites.contains(clean)) customSites.add(clean)
                        },
                        onDeleteSite = { site ->
                            val set = sharedPrefs.getStringSet("CUSTOM_SITES", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                            set.remove(site)
                            sharedPrefs.edit().putStringSet("CUSTOM_SITES", set).apply()
                            customSites.remove(site)
                        },
                        onAddKeyword = { kw ->
                            val clean = kw.trim().lowercase()
                            val set = sharedPrefs.getStringSet("CUSTOM_KEYWORDS", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                            set.add(clean)
                            sharedPrefs.edit().putStringSet("CUSTOM_KEYWORDS", set).apply()
                            if (!customKeywords.contains(clean)) customKeywords.add(clean)
                        },
                        onDeleteKeyword = { kw ->
                            val set = sharedPrefs.getStringSet("CUSTOM_KEYWORDS", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                            set.remove(kw)
                            sharedPrefs.edit().putStringSet("CUSTOM_KEYWORDS", set).apply()
                            customKeywords.remove(kw)
                        },
                        onFullScreenChange = { isFullScreenEditorActive = it }
                    )
                    SpaTab.ACTIVITY -> NotesTimelineRoute(
                        viewModel = notesViewModel,
                        onFullScreenModeChanged = { isFullScreenEditorActive = it }
                    )
                }
            }

            // Admin & Quick Settings Bottom Sheet
            if (showAdminSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showAdminSheet = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    AdminSettingsSheetContent(
                        isDeviceOwner = isDeviceOwner,
                        onImportFile = {
                            showAdminSheet = false
                            filePickerLauncher.launch(arrayOf("text/plain"))
                        },
                        onOpenDevConsole = {
                            showAdminSheet = false
                            val intent = Intent(context, DebugConsoleActivity::class.java)
                            context.startActivity(intent)
                        },
                        onOpenGitHub = {
                            showAdminSheet = false
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kavinmaranravi/HalanoiApp"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// 1. SHIELD TAB (Hero VPN Master Orb Screen)
// ==========================================
@Composable
fun ShieldMasterTab(
    isVpnActive: Boolean,
    isAccessibilityEnabled: Boolean,
    isDeviceOwner: Boolean,
    browserBlockMode: String,
    onBrowserModeChange: (String) -> Unit,
    onToggleMasterShield: () -> Unit,
    onEmergencyLock: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Accessibility Warning Card
        AnimatedVisibility(visible = !isAccessibilityEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Accessibility Service Offline", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                        Text("Required to monitor screen and block distractions.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Enable", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        // --- MASTER PROTECTION ORB ---
        Spacer(modifier = Modifier.height(18.dp))
        MasterProtectionOrb(
            isActive = isVpnActive,
            onClick = onToggleMasterShield
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Status text
        Text(
            text = if (isVpnActive) "PROTECTION ACTIVE" else "STANDBY MODE",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
            color = if (isVpnActive) CyberEmerald else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (isVpnActive) "Cloudflare Family DNS (1.1.1.3) Enforced" else "Tap Orb to engage VPN & AI Shield",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
        )

        // --- BROWSER LOCKDOWN SELECTOR PILLS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Browser Access Shield",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Triple("STANDARD", "Standard", "Vault only"),
                        Triple("CHROME_ONLY", "Chrome", "Chrome only"),
                        Triple("ZERO_BROWSER", "Zero", "No web")
                    )

                    modes.forEach { (modeVal, title, sub) ->
                        val isSelected = (browserBlockMode == modeVal)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onBrowserModeChange(modeVal) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = sub,
                                    fontSize = 10.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- EMERGENCY TOTAL LOCKDOWN BUTTON ---
        Button(
            onClick = onEmergencyLock,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DangerCrimson
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
        ) {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Initiate Total Lockdown 🔒", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        }

        if (BuildConfig.DEBUG && isDeviceOwner) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onEmergencyLock, // Triggers deactivation / admin dialog
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DangerCrimson.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Deactivate Device Owner (Debug Only) 🔓", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DangerCrimson)
            }
        } else {
            // In Release Mode: Show Sovereign Lockdown & PC Deactivation Guide Card
            Spacer(modifier = Modifier.height(14.dp))
            SovereignAdminPolicyCard()
        }

        Spacer(modifier = Modifier.height(100.dp)) // Space for floating dock
    }
}

// Master Animated Protection Orb
@Composable
fun MasterProtectionOrb(
    isActive: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isActive) 0.45f else 0.1f,
        targetValue = if (isActive) 0.08f else 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    if (isActive) CyberEmerald.copy(alpha = pulseAlpha)
                    else MaterialTheme.colorScheme.outline.copy(alpha = pulseAlpha)
                )
        )

        // Middle ring
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) NeonCyan.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
                .border(
                    2.dp,
                    if (isActive) CyberEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    CircleShape
                )
        )

        // Inner Core Interactive Orb
        Surface(
            modifier = Modifier
                .size(126.dp)
                .clip(CircleShape)
                .clickable { onClick() }
                .shadow(elevation = if (isActive) 12.dp else 2.dp, shape = CircleShape),
            shape = CircleShape,
            color = if (isActive) CyberEmerald else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isActive) {
                            Brush.radialGradient(
                                colors = listOf(NeonCyan, CyberEmerald, CyberEmeraldDark)
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Check else Icons.Default.PlayArrow,
                        contentDescription = "Master Shield Switch",
                        tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isActive) "ACTIVE" else "START",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. EMBEDDED APP VAULT TAB (Single-Page)
// ==========================================
@Composable
fun EmbeddedAppVaultTab(
    lockedAppsSet: MutableList<String>,
    onLockApp: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<SelectableApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val launchable = packages.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

            val appList = launchable.map { appInfo ->
                val name = pm.getApplicationLabel(appInfo).toString()
                val pkg = appInfo.packageName
                val icon = try {
                    appInfo.loadIcon(pm).toImageBitmap()
                } catch (e: Exception) {
                    null
                }
                SelectableApp(
                    name = name,
                    packageName = pkg,
                    iconBitmap = icon,
                    isInitiallyLocked = lockedAppsSet.contains(pkg)
                )
            }
            installedApps = appList
            isLoading = false
        }
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val lockedApps = remember(filteredApps, lockedAppsSet.toList()) {
        filteredApps.filter { lockedAppsSet.contains(it.packageName) }
    }
    val availableApps = remember(filteredApps, lockedAppsSet.toList()) {
        filteredApps.filter { !lockedAppsSet.contains(it.packageName) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps (${lockedAppsSet.size} locked)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (filteredApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Pinned Locked Apps Section
                if (lockedApps.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Locked in Vault (${lockedApps.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DangerCrimson
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = DangerCrimson.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "RESTRICTED 🔒",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DangerCrimson,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    items(lockedApps, key = { "locked_" + it.packageName }) { app ->
                        AppVaultItemCard(
                            app = app,
                            isLocked = true,
                            onToggleLock = { onLockApp(app.packageName) }
                        )
                    }

                    item {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                // Available / Unlocked Apps Section
                if (availableApps.isNotEmpty()) {
                    item {
                        Text(
                            text = if (lockedApps.isNotEmpty()) "Available Apps (${availableApps.size})" else "Installed Apps (${availableApps.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                        )
                    }

                    items(availableApps, key = { "available_" + it.packageName }) { app ->
                        AppVaultItemCard(
                            app = app,
                            isLocked = false,
                            onToggleLock = { onLockApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

enum class FilterSubScreen {
    HUB, SITES, KEYWORDS, AI_TOPICS
}

// ==========================================
// 3. EMBEDDED CONTENT FILTERS TAB (Clean Modular Hub)
// ==========================================
@Composable
fun EmbeddedFiltersTab(
    customSites: List<String>,
    customKeywords: List<String>,
    onAddSite: (String) -> Unit,
    onDeleteSite: (String) -> Unit,
    onAddKeyword: (String) -> Unit,
    onDeleteKeyword: (String) -> Unit,
    onFullScreenChange: (Boolean) -> Unit = {}
) {
    var currentSubScreen by remember { mutableStateOf(FilterSubScreen.HUB) }

    val systemBlockedWebsites = remember {
        listOf(
            "reddit.com", "twitter.com", "x.com", "instagram.com", "facebook.com", "tiktok.com",
            "netflix.com", "twitch.tv", "disneyplus.com", "primevideo.com", "hulu.com",
            "pinterest.com", "tumblr.com", "deviantart.com", "spotify.com", "snapchat.com"
        ).sorted()
    }

    LaunchedEffect(currentSubScreen) {
        onFullScreenChange(currentSubScreen != FilterSubScreen.HUB)
    }

    Crossfade(targetState = currentSubScreen, label = "FilterSubScreenTransition") { screen ->
        when (screen) {
            FilterSubScreen.HUB -> {
                FiltersHubScreen(
                    totalSitesCount = customSites.size + systemBlockedWebsites.size,
                    customKeywordsCount = customKeywords.size,
                    onNavigateToSites = { currentSubScreen = FilterSubScreen.SITES },
                    onNavigateToKeywords = { currentSubScreen = FilterSubScreen.KEYWORDS },
                    onNavigateToAiTopics = { currentSubScreen = FilterSubScreen.AI_TOPICS }
                )
            }
            FilterSubScreen.SITES -> {
                FullScreenSitesManagerView(
                    customSites = customSites,
                    systemBlockedWebsites = systemBlockedWebsites,
                    onBack = { currentSubScreen = FilterSubScreen.HUB },
                    onAddSite = onAddSite,
                    onDeleteSite = onDeleteSite
                )
            }
            FilterSubScreen.KEYWORDS -> {
                FullScreenKeywordsManagerView(
                    customKeywords = customKeywords,
                    onBack = { currentSubScreen = FilterSubScreen.HUB },
                    onAddKeyword = onAddKeyword,
                    onDeleteKeyword = onDeleteKeyword
                )
            }
            FilterSubScreen.AI_TOPICS -> {
                FullScreenAiTopicsManagerView(
                    onBack = { currentSubScreen = FilterSubScreen.HUB }
                )
            }
        }
    }
}

// --- HUB SCREEN (3 Sleek Action Cards) ---
@Composable
fun FiltersHubScreen(
    totalSitesCount: Int,
    customKeywordsCount: Int,
    onNavigateToSites: () -> Unit,
    onNavigateToKeywords: () -> Unit,
    onNavigateToAiTopics: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Content Protection Hub",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Select a filter category to view and manage active rules in full-screen mode.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 2.dp, bottom = 18.dp)
        )

        // Card 1: Blocked Websites
        FilterModuleCard(
            title = "Blocked Websites",
            description = "Manage DNS sinkholed domains & add custom URLs with automatic brand logo recognition.",
            badgeText = "$totalSitesCount Domains Active",
            badgeColor = NeonCyan,
            iconEmoji = "🌐",
            iconBgColor = NeonCyan.copy(alpha = 0.15f),
            onClick = onNavigateToSites
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Card 2: Blocked Keywords
        FilterModuleCard(
            title = "Blocked Keywords",
            description = "Screen sniper OCR keywords blocking reels, feeds & custom text phrases in real-time.",
            badgeText = "$customKeywordsCount Keywords Active",
            badgeColor = RoyalViolet,
            iconEmoji = "🎯",
            iconBgColor = RoyalViolet.copy(alpha = 0.15f),
            onClick = onNavigateToKeywords
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Card 3: AI Vision Scanner Topics
        FilterModuleCard(
            title = "AI Vision Scanner Topics",
            description = "On-device ML Vision topic detection models for Adult/NSFW, Gaming, Sports & Politics.",
            badgeText = "4 AI Models Active",
            badgeColor = CyberEmerald,
            iconEmoji = "🧠",
            iconBgColor = CyberEmerald.copy(alpha = 0.15f),
            onClick = onNavigateToAiTopics
        )

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun FilterModuleCard(
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
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(text = iconEmoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    lineHeight = 16.sp
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
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// --- FULL SCREEN VIEW 1: SITES MANAGER ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenSitesManagerView(
    customSites: List<String>,
    systemBlockedWebsites: List<String>,
    onBack: () -> Unit,
    onAddSite: (String) -> Unit,
    onDeleteSite: (String) -> Unit
) {
    BackHandler(enabled = true) { onBack() }
    var siteInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = {
                    Text("Blocked Websites & Domains", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Add Custom Blocked Site", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = siteInput,
                                onValueChange = { siteInput = it },
                                placeholder = { Text("e.g. reddit.com", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    if (siteInput.isNotBlank()) {
                                        onAddSite(siteInput)
                                        siteInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    }
                }
            }

            if (customSites.isNotEmpty()) {
                if (BuildConfig.DEBUG) {
                    item {
                        Text(
                            text = "Your Custom Blocked Sites (${customSites.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(customSites) { site ->
                        val brand = BrandIconHelper.getWebsiteBrand(site)
                        BrandWebsiteCard(
                            domain = site,
                            brandName = brand.displayName,
                            emoji = brand.iconEmoji,
                            category = brand.category,
                            brandColor = brand.brandColor,
                            onDelete = { onDeleteSite(site) }
                        )
                    }
                } else {
                    // Release Mode: Show Enforced Count Card & Notice (No delete buttons)
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
                                    text = "To keep your focus unbreakable, individual custom site removal is disabled in the Release build. To view full domain names or remove specific URLs, install the Halanoi Debug APK.",
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
                                    Text("Download Debug Build for Editing ⬇️", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Permanent System Defaults (${systemBlockedWebsites.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            items(systemBlockedWebsites) { site ->
                val brand = BrandIconHelper.getWebsiteBrand(site)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
                            Text(brand.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(site, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
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

// --- FULL SCREEN VIEW 2: KEYWORDS MANAGER ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenKeywordsManagerView(
    customKeywords: List<String>,
    onBack: () -> Unit,
    onAddKeyword: (String) -> Unit,
    onDeleteKeyword: (String) -> Unit
) {
    BackHandler(enabled = true) { onBack() }
    var keywordInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = {
                    Text("AI Screen Blocked Keywords", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Add Screen Trigger Keyword", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = keywordInput,
                                onValueChange = { keywordInput = it },
                                placeholder = { Text("e.g. reels, gaming", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    if (keywordInput.isNotBlank()) {
                                        onAddKeyword(keywordInput)
                                        keywordInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalViolet),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    }
                }
            }

            if (customKeywords.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No custom keywords added yet.\nType a keyword above to trigger on-device screen blur!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (BuildConfig.DEBUG) {
                item {
                    Text(
                        text = "Active Screen Trigger Keywords (${customKeywords.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoyalViolet,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(customKeywords) { kw ->
                    val tag = BrandIconHelper.getKeywordTag(kw)
                    BrandKeywordCard(
                        keyword = kw,
                        emoji = tag.iconEmoji,
                        category = tag.category,
                        tagColor = tag.tagColor,
                        onDelete = { onDeleteKeyword(kw) }
                    )
                }
            } else {
                // Release Mode: Show Enforced Count Card & Notice (No delete buttons)
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
                                text = "All custom keyword triggers are actively running on-device OCR. Individual keyword deletion is locked in the Release build. To view and edit custom keyword triggers, install the Halanoi Debug APK.",
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
                                Text("Download Debug Build for Editing ⬇️", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- FULL SCREEN VIEW 3: AI TOPICS BREAKDOWN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenAiTopicsManagerView(
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val aiTopics = listOf(
        Triple("🔞 Adult / NSFW Content", "Detects adult, sexual, and erotic visual content in any active app or browser tab using on-device ML.", "Confidence Threshold: > 85%"),
        Triple("🎮 Gaming & Live Streams", "Identifies gameplay feeds, game UI elements, and twitch-style video streaming layouts.", "Confidence Threshold: > 90%"),
        Triple("⚽ Sports Scores & Live News", "Detects sports matches, match statistics, scoreboards, and cricket/football feeds.", "Confidence Threshold: > 80%"),
        Triple("📰 Political Debates & Polarization", "Identifies news debates, election rhetoric, and polarizing partisan talking heads.", "Confidence Threshold: > 80%")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = {
                    Text("AI Vision Classifiers", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyberEmerald.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "4 Models Live",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CyberEmerald.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛡️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "All AI Vision topic classification runs 100% locally on your device neural accelerator without sending any frames to external servers.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            items(aiTopics) { (title, desc, status) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = status,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrandWebsiteCard(
    domain: String,
    brandName: String,
    emoji: String,
    category: String,
    brandColor: Color,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, brandColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WebsiteFavicon(
                domain = domain,
                emoji = emoji,
                brandColor = brandColor,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = brandName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = brandColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = category,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = domain,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = DangerCrimson,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun BrandKeywordCard(
    keyword: String,
    emoji: String,
    category: String,
    tagColor: Color,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tagColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tagColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = keyword,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = tagColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = category,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = tagColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = "AI Screen & Vision Filter",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = DangerCrimson,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}


// ==========================================
// 4. FLOATING GLASS BOTTOM DOCK
// ==========================================
@Composable
fun FloatingGlassBottomDock(
    currentTab: SpaTab,
    onTabSelected: (SpaTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpaTab.values().forEach { tab ->
                    val isSelected = (currentTab == tab)
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(interactionSource = interactionSource, indication = null) {
                                onTabSelected(tab)
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. ADMIN SETTINGS BOTTOM SHEET
// ==========================================
@Composable
fun AdminSettingsSheetContent(
    isDeviceOwner: Boolean,
    onImportFile: () -> Unit,
    onOpenDevConsole: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text("Admin & Security Core", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(
            text = if (isDeviceOwner) "Device Owner Enforced (Safe uninstall locked)" else "Active Admin Mode (Reduced Security)",
            fontSize = 12.sp,
            color = if (isDeviceOwner) CyberEmerald else AmberWarning,
            modifier = Modifier.padding(top = 2.dp, bottom = 18.dp)
        )

        // Bulk Import
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onImportFile() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = NeonCyan)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("Bulk Import Rules", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Load websites, keywords, apps from export.txt", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Developer Console
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onOpenDevConsole() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = RoyalViolet)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("Developer Debug Console", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("View live AI scrapings and deactivation dialog", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // GitHub
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onOpenGitHub() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = AmberWarning)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("Star us on GitHub ⭐", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Support the open-source Halanoi project", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

fun startHalanoiVpn(context: Context) {
    val intent = Intent(context, HalanoiVpnService::class.java)
    context.startService(intent)
}

fun parseSection(content: String, header: String): List<String> {
    val lines = content.lines()
    val result = mutableListOf<String>()
    var inSection = false
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed == header) {
            inSection = true
            continue
        }
        if (inSection) {
            if (trimmed.startsWith("===")) break
            if (trimmed.isNotEmpty()) {
                var cleaned = trimmed
                if (header.contains("WEBSITES")) {
                    cleaned = cleaned.replace(Regex("^https?://"), "").replace(Regex("/$"), "")
                }
                if (cleaned.isNotEmpty()) result.add(cleaned)
            }
        }
    }
    return result
}

fun parseKeywords(content: String): List<String> {
    val lines = content.lines()
    var inSection = false
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed == "=== CUSTOM BLOCKED KEYWORDS ===") {
            inSection = true
            continue
        }
        if (inSection) {
            if (trimmed.startsWith("===")) break
            if (trimmed.isNotEmpty()) {
                return trimmed.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            }
        }
    }
    return emptyList()
}