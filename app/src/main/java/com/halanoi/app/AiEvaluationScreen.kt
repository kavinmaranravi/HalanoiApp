package com.halanoi.app

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halanoi.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// 1. AI EVALUATION VIEWMODEL
// ==========================================

class AiEvaluationViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).aiTelemetryDao()

    val telemetryList: StateFlow<List<AiTelemetryEntity>> = dao.getAllTelemetry()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateFeedback(id: String, feedback: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateFeedback(id, feedback)
        }
    }

    fun deleteItem(entity: AiTelemetryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(entity)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAll()
        }
    }
}

// ==========================================
// 2. AI EVALUATION & DATASET LAB SCREEN
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiEvaluationScreen(
    viewModel: AiEvaluationViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val telemetryList by viewModel.telemetryList.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, BLOCKED, ALLOWED, RATED, UNRATED
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredList = remember(telemetryList, searchQuery, selectedFilter) {
        telemetryList.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.scrapedText.contains(searchQuery, ignoreCase = true) ||
                    item.appName.contains(searchQuery, ignoreCase = true) ||
                    item.predictedLabel.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "BLOCKED" -> item.wasBlocked
                "ALLOWED" -> !item.wasBlocked
                "RATED" -> item.groundTruthFeedback != "UNRATED"
                "UNRATED" -> item.groundTruthFeedback == "UNRATED"
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    // Accuracy Calculation
    val ratedItems = telemetryList.filter { it.groundTruthFeedback != "UNRATED" }
    val correctCount = ratedItems.count { it.groundTruthFeedback == "CORRECT" }
    val falsePositiveCount = ratedItems.count { it.groundTruthFeedback == "FALSE_POSITIVE" }
    val falseNegativeCount = ratedItems.count { it.groundTruthFeedback == "FALSE_NEGATIVE" }
    val accuracyPercent = if (ratedItems.isNotEmpty()) (correctCount * 100f / ratedItems.size) else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Dataset & Evaluation Lab 🧠", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Ground-Truth Telemetry & ML Metrics", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All", tint = DangerCrimson)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // 1. ML METRICS DASHBOARD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model Performance Telemetry",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CyberEmerald.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "REAL-TIME ON-DEVICE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Metric 1: Total Scans
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${telemetryList.size}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyan)
                                Text("Inferences", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Metric 2: Accuracy %
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (ratedItems.isNotEmpty()) "%.1f%%".format(accuracyPercent) else "N/A",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (accuracyPercent >= 80f) CyberEmerald else if (accuracyPercent >= 50f) AmberWarning else DangerCrimson
                                )
                                Text("Accuracy (${ratedItems.size})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Metric 3: False Positives
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$falsePositiveCount", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AmberWarning)
                                Text("False Pos.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Metric 4: False Negatives
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$falseNegativeCount", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = DangerCrimson)
                                Text("False Neg.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. EXPORT DATASET ACTION BAR (CSV / JSON)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (telemetryList.isEmpty()) {
                            Toast.makeText(context, "No telemetry records to export yet", Toast.LENGTH_SHORT).show()
                        } else {
                            val uri = DatasetExporter.exportToCsv(context, telemetryList)
                            if (uri != null) {
                                DatasetExporter.shareExportFile(context, uri, "text/csv", "Share Halanoi AI Dataset (CSV)")
                            } else {
                                Toast.makeText(context, "Failed to generate CSV", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }

                OutlinedButton(
                    onClick = {
                        if (telemetryList.isEmpty()) {
                            Toast.makeText(context, "No telemetry records to export yet", Toast.LENGTH_SHORT).show()
                        } else {
                            val uri = DatasetExporter.exportToJson(context, telemetryList)
                            if (uri != null) {
                                DatasetExporter.shareExportFile(context, uri, "application/json", "Share Halanoi AI Dataset (JSON)")
                            } else {
                                Toast.makeText(context, "Failed to generate JSON", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonCyan)
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. SEARCH AND FILTER CHIPS
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search text, labels, apps...", fontSize = 13.sp) },
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

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ALL" to "All (${telemetryList.size})", "BLOCKED" to "Blocked", "ALLOWED" to "Allowed", "UNRATED" to "Unrated").forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedFilter = key }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. TELEMETRY LOG ITEMS LIST
            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧠", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "No AI Inferences Recorded Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "As you browse apps on your phone, Halanoi will log its screen analysis here for ML accuracy evaluation.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        AiTelemetryItemCard(
                            item = item,
                            onUpdateFeedback = { feedback -> viewModel.updateFeedback(item.id, feedback) },
                            onDelete = { viewModel.deleteItem(item) }
                        )
                    }
                }
            }
        }
    }

    // Clear Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All AI Telemetry?") },
            text = { Text("This will permanently remove all collected dataset records and accuracy ratings.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = DangerCrimson, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==========================================
// 3. AI TELEMETRY ITEM CARD (Interactive Ground Truth)
// ==========================================

@Composable
fun AiTelemetryItemCard(
    item: AiTelemetryEntity,
    onUpdateFeedback: (String) -> Unit,
    onDelete: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(item.timestamp) { timeFormatter.format(Date(item.timestamp)) }
    val confidencePct = (item.confidenceScore * 100).toInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (item.wasBlocked) DangerCrimson.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: App Name + Action Badge + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (item.wasBlocked) DangerCrimson.copy(alpha = 0.15f) else CyberEmerald.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (item.wasBlocked) "BLOCKED 🚨" else "ALLOWED ✅",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.wasBlocked) DangerCrimson else CyberEmerald,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Scraped Text Content
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Text(
                    text = "\"${item.scrapedText}\"",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(8.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Inference Label & Confidence Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Predicted:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NeonCyan.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.predictedLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "Confidence: $confidencePct%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (confidencePct >= 80) CyberEmerald else AmberWarning
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            Spacer(modifier = Modifier.height(6.dp))

            // Ground Truth Tagger Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ground Truth:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Option 1: Correct 👍
                    FilterChip(
                        selected = item.groundTruthFeedback == "CORRECT",
                        onClick = { onUpdateFeedback("CORRECT") },
                        label = { Text("👍 Correct", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = CyberEmerald
                        )
                    )

                    // Option 2: False Positive ⚠️
                    FilterChip(
                        selected = item.groundTruthFeedback == "FALSE_POSITIVE",
                        onClick = { onUpdateFeedback("FALSE_POSITIVE") },
                        label = { Text("⚠️ False Pos.", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberWarning.copy(alpha = 0.2f),
                            selectedLabelColor = AmberWarning
                        )
                    )

                    // Option 3: False Negative ❌
                    FilterChip(
                        selected = item.groundTruthFeedback == "FALSE_NEGATIVE",
                        onClick = { onUpdateFeedback("FALSE_NEGATIVE") },
                        label = { Text("❌ False Neg.", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DangerCrimson.copy(alpha = 0.2f),
                            selectedLabelColor = DangerCrimson
                        )
                    )
                }
            }
        }
    }
}
