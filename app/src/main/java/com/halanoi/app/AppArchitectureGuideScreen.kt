package com.halanoi.app

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halanoi.app.ui.theme.*

// ==========================================
// 1. DATA MODEL FOR SYSTEM GUIDE SECTIONS
// ==========================================

data class GuideSection(
    val id: String,
    val title: String,
    val category: String, // PHILOSOPHY, SHIELD, VAULT, FILTERS, AI, DATASET, SCRATCHPAD, ADMIN
    val iconEmoji: String,
    val badge: String,
    val summary: String,
    val details: List<Pair<String, String>>
)

// ==========================================
// 2. GUIDE SCREEN COMPOSABLE
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppArchitectureGuideScreen(
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var expandedSectionIds by remember { mutableStateOf(setOf<String>()) }

    val allSections = remember { getSystemGuideSections() }

    val filteredSections = remember(allSections, searchQuery, selectedCategory) {
        allSections.filter { section ->
            val matchesCategory = selectedCategory == "ALL" || section.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                    section.title.contains(searchQuery, ignoreCase = true) ||
                    section.summary.contains(searchQuery, ignoreCase = true) ||
                    section.details.any { it.first.contains(searchQuery, ignoreCase = true) || it.second.contains(searchQuery, ignoreCase = true) }
            matchesCategory && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sovereign Architecture 📖", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Complete Feature & Engine Blueprint", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // 1. SEARCH BAR
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search features, AI, DNS, Vault...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. CATEGORY CHIPS
            val categories = listOf(
                "ALL" to "All",
                "PHILOSOPHY" to "🌟 Philosophy",
                "SHIELD" to "🛡️ Shield",
                "VAULT" to "🏰 Vault",
                "FILTERS" to "🔍 Filters",
                "AI" to "🧠 AI Layer",
                "DATASET" to "📊 Dataset Lab",
                "SCRATCHPAD" to "📝 Scratchpad",
                "ADMIN" to "🔒 Admin"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Scrollable category row
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { (key, label) ->
                        val isSelected = selectedCategory == key
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedCategory = key }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. SECTIONS LIST
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
            ) {
                items(filteredSections, key = { it.id }) { section ->
                    val isExpanded = expandedSectionIds.contains(section.id)
                    GuideSectionCard(
                        section = section,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedSectionIds = if (isExpanded) {
                                expandedSectionIds - section.id
                            } else {
                                expandedSectionIds + section.id
                            }
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. EXPANDABLE GUIDE SECTION CARD
// ==========================================

@Composable
fun GuideSectionCard(
    section: GuideSection,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emoji Icon Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(section.iconEmoji, fontSize = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = section.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = section.badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary text
            Text(
                text = section.summary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            // Expanded Detailed Points
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    section.details.forEach { (heading, desc) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = heading,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. MASTER KNOWLEDGE BASE DATA
// ==========================================

private fun getSystemGuideSections(): List<GuideSection> {
    return listOf(
        GuideSection(
            id = "philosophy",
            title = "The Sovereign Philosophy & Privacy",
            category = "PHILOSOPHY",
            iconEmoji = "🌟",
            badge = "100% On-Device & Zero-Cloud",
            summary = "Halanoi is a hardware-hardened, unbypassable focus defense system built for deep digital sovereignty.",
            details = listOf(
                "Zero Cloud Dependency" to "All DNS filtering, OCR screen scraping, NLP tokenization, and AI classification happen 100% locally on your phone. No telemetry or browsing data ever leaves your device.",
                "Zero Battery Drain" to "Native loopback routing processes thousands of DNS queries in microseconds without background battery consumption or thermal throttling.",
                "Impulse Resistance" to "Designed to prevent self-sabotage by eliminating instant uninstallation when willpower is low during focus sessions."
            )
        ),
        GuideSection(
            id = "shield",
            title = "Tab 1: Shield Master Defense",
            category = "SHIELD",
            iconEmoji = "🛡️",
            badge = "VPN Filter & Master Orb",
            summary = "The central command hub controlling the local VPN tunnel, browser access modes, and hardware lockdown.",
            details = listOf(
                "Hero Master Orb" to "Tap the orb to toggle between STANDBY MODE and SOVEREIGN ACTIVE. Engaging the orb activates the local loopback VPN packet filter.",
                "Interceptions Scoreboard (+1 Counter)" to "Increments automatically whenever Halanoi blocks a blacklisted website, catches a locked app, or deflects a distracting video via AI.",
                "Browser Access Modes" to "Choose between 'Standard' (vault only), 'Chrome Only' (forces browsing through policy Chrome), and 'Zero Web' (complete internet kill-switch for deep work).",
                "Emergency Hardware Lockdown 🔒" to "1-tap instant lock that secures the device screen via Android Device Admin policy."
            )
        ),
        GuideSection(
            id = "vault",
            title = "Tab 2: App Vault & Containment",
            category = "VAULT",
            iconEmoji = "🏰",
            badge = "Core 9 + Custom Apps",
            summary = "Guards your device against infinite-scroll dopamine loops and distracting third-party apps.",
            details = listOf(
                "Core 9 System Vault" to "Permanently shields against the top 9 dopamine traps: X (Twitter), YouTube, Instagram, Facebook, TikTok, Snapchat, Reddit, Netflix, and Tor with official web favicons.",
                "Custom Installed Apps Vault" to "Allows you to lock any user-installed application with 1-tap. Locked apps cannot be launched while the shield is active.",
                "De-Duplication Engine" to "Automatically filters out core system packages from the user apps list to keep the interface clean and organized."
            )
        ),
        GuideSection(
            id = "filters",
            title = "Tab 3: DNS & Content Filters",
            category = "FILTERS",
            iconEmoji = "🔍",
            badge = "DNS Blocklist & Keywords",
            summary = "Local kernel-level packet inspection against distracting domains, adult sites, and keywords.",
            details = listOf(
                "System Blocked Websites" to "Pre-loaded database of addictive video portals, gambling sites, adult platforms, and infinite-scroll feeds with official logos.",
                "Custom Blocked Domains" to "Easily add and delete specific domain names (e.g. reddit.com, twitch.tv) with instant real-time sync.",
                "Keyword Blocklist Engine" to "Blocks any URL or page title that contains specified trigger keywords (e.g. 'shorts', 'reels', 'gaming', 'gossip').",
                "Blocklist File Importer" to "Import large external blocklists directly from .txt files stored on your phone."
            )
        ),
        GuideSection(
            id = "ai_layer",
            title = "On-Device AI Classification Layer",
            category = "AI",
            iconEmoji = "🧠",
            badge = "Real-Time Screen Inference",
            summary = "Cognitive defense engine that reads onscreen text and deflects distracting content before you get trapped.",
            details = listOf(
                "Accessibility Screen Scraping" to "The accessibility engine inspects video titles, search queries, and post captions in real time with debounced low-overhead sampling.",
                "NLP Tokenization & Category Scoring" to "Normalizes text and scores it against intent categories: DISTRACTION 🚨, ENTERTAINMENT 🎬, PRODUCTIVITY 💡, and SAFE ✅.",
                "Dynamic Confidence Threshold (>= 70%)" to "If the probability of distraction exceeds 70%, the AI immediately dispatches GLOBAL_ACTION_HOME to eject you back to safety.",
                "Deflection Toast Notification" to "Displays '🚨 Distraction Deflected by Halanoi AI' and increments the lifetime interception counter."
            )
        ),
        GuideSection(
            id = "dataset_lab",
            title = "AI Dataset & Evaluation Lab",
            category = "DATASET",
            iconEmoji = "📊",
            badge = "ML Ground Truth & Export",
            summary = "Interactive machine learning studio for inspecting live inferences and exporting training datasets.",
            details = listOf(
                "Live Inferences Telemetry" to "Logs every screen analysis event (App, Scraped Text, Predicted Label, Confidence %) into the local SQLite database.",
                "Interactive Ground-Truth Tagging" to "Review live cards and rate them: 👍 Correct, ⚠️ False Positive (over-blocked), or ❌ False Negative (missed distraction).",
                "Live Accuracy Metrics" to "Calculates real-time model accuracy percentage, false positive counts, and false negative counts.",
                "1-Tap Dataset Exporter" to "Export complete datasets in RFC 4180 CSV (for Pandas / Excel / Jupyter) or structured JSON (for model fine-tuning)."
            )
        ),
        GuideSection(
            id = "scratchpad",
            title = "Tab 4: Activity & Scratchpad Studio",
            category = "SCRATCHPAD",
            iconEmoji = "📝",
            badge = "Notes, Tasks & Permanent Backup",
            summary = "Distraction-free focus scratchpad and task tracker with unbypassable 3-tier backup & 1-tap restore redundancy.",
            details = listOf(
                "Focus Tasks & Timeline" to "Create focus items with deadlines, checkbox completion animations, and milestone event logging.",
                "Full-Screen Scratchpad with Tap-to-Front" to "Minimalist editor with intelligent onTextLayout cursor tracking. Tapping any paragraph lower down glides that exact line directly to the front/center above the keyboard.",
                "3-Layer Permanent Auto-Backup" to "Every note, task, and scratchpad edit is automatically mirrored to MediaStore Downloads ('Download/Halanoi/halanoi_notes_backup.json') and public Documents storage. MediaStore files persist even if Halanoi is completely uninstalled.",
                "1-Tap Restore / Import (Admin Settings) 📥" to "If you reinstall Halanoi or switch devices, tap 'Restore Notes & Tasks' in Admin Settings to pick any backup JSON file. Android's OpenDocument system picker bypasses all Scoped Storage UID restrictions seamlessly.",
                "1-Tap Manual Export Snapshot 📤" to "Generate and export fresh backup snapshots on demand to public storage or share across devices."
            )
        ),
        GuideSection(
            id = "admin",
            title = "Hardware Admin & Security Core",
            category = "ADMIN",
            iconEmoji = "🔒",
            badge = "Anti-Uninstall & Data Control",
            summary = "Hardware-level Device Owner protection and central control hub for import/export, dataset evaluation, and ADB rules.",
            details = listOf(
                "1-Tap Backup Restore & Export Hub" to "Admin settings contains direct 1-tap actions to export notes/tasks snapshots and restore from backup JSON files via system storage framework.",
                "Bulk Rule Importer" to "Load custom DNS blocked sites, keyword filters, and locked applications in one go from export.txt.",
                "Device Owner Hardening" to "Protects Halanoi from on-device uninstallation or forced clearing, enforcing sovereign discipline.",
                "PC ADB Deactivation Guide" to "In Release builds, explains why admin cannot be disabled on the phone and provides the 1-tap copyable PC ADB command ('dpm remove-active-admin') to deactivate via USB."
            )
        )
    )
}
