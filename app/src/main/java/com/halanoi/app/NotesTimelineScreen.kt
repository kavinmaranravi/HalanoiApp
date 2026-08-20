package com.halanoi.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halanoi.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotesTimelineRoute(
    viewModel: NotesTimelineViewModel,
    isEmbedded: Boolean = true,
    onFullScreenModeChanged: (Boolean) -> Unit = {}
) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val events by viewModel.events.collectAsState(initial = emptyList())
    val scratchpads by viewModel.scratchpads.collectAsState(initial = emptyList())

    var activeScratchpad by remember { mutableStateOf<ScratchpadEntity?>(null) }

    LaunchedEffect(activeScratchpad) {
        onFullScreenModeChanged(activeScratchpad != null)
    }

    Crossfade(targetState = activeScratchpad != null, label = "Screen Transition") { isFullScreen ->
        if (isFullScreen && activeScratchpad != null) {
            FullScreenNoteEditor(
                pad = activeScratchpad!!,
                onBack = { 
                    activeScratchpad = null
                    onFullScreenModeChanged(false)
                },
                onUpdate = viewModel::updateScratchpad
            )
        } else {
            NotesTimelineScreen(
                notes = notes,
                events = events,
                scratchpads = scratchpads,
                isEmbedded = isEmbedded,
                onAddNote = { text, deadline ->
                    viewModel.addNote(text, deadline)
                    val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                    val deadlineText = if (deadline.isNotBlank()) " (Due: $deadline)" else ""
                    viewModel.addEvent(currentTime, "Task Added", "$text$deadlineText")
                },
                onToggleNote = viewModel::toggleNoteCheck,
                onDeleteNote = viewModel::deleteNote,
                onDeleteEvent = viewModel::deleteEvent,
                onCreateNewScratchpad = {
                    val newId = viewModel.createEmptyScratchpad()
                    activeScratchpad = ScratchpadEntity(
                        id = newId,
                        title = "",
                        content = "",
                        updatedAt = System.currentTimeMillis()
                    )
                },
                onOpenScratchpad = { pad ->
                    activeScratchpad = pad
                },
                onDeleteScratchpad = viewModel::deleteScratchpad
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenNoteEditor(
    pad: ScratchpadEntity,
    onBack: () -> Unit,
    onUpdate: (ScratchpadEntity, String, String) -> Unit
) {
    var title by remember(pad.id) { mutableStateOf(pad.title) }
    var contentValue by remember(pad.id) { 
        mutableStateOf(TextFieldValue(text = pad.content, selection = TextRange(0))) 
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val saveAndClose = {
        if (title != pad.title || contentValue.text != pad.content) {
            onUpdate(pad, title, contentValue.text)
        }
        onBack()
    }

    BackHandler(enabled = true) {
        saveAndClose()
    }

    // Debounced background auto-save to avoid recomposing parent and jumping cursor
    LaunchedEffect(title, contentValue.text) {
        if (title != pad.title || contentValue.text != pad.content) {
            kotlinx.coroutines.delay(600)
            onUpdate(pad, title, contentValue.text)
        }
    }

    val dateString = remember(pad.updatedAt) {
        SimpleDateFormat("EEEE, MMMM d 'at' HH:mm", Locale.getDefault()).format(Date(pad.updatedAt))
    }

    val scrollState = rememberScrollState()

    // When the user taps ANY paragraph anywhere in the note, instantly auto-scroll that paragraph front and center!
    LaunchedEffect(contentValue.selection) {
        textLayoutResult?.let { layout ->
            if (contentValue.selection.start in 0..contentValue.text.length && contentValue.text.isNotEmpty()) {
                val cursorRect = layout.getCursorRect(contentValue.selection.start)
                // Generous 70dp offset so the tapped line is clearly visible above the keyboard
                val targetScroll = (cursorRect.top - 70f).coerceAtLeast(0f).toInt()
                scrollState.animateScrollTo(targetScroll)
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = saveAndClose) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface) 
                    }
                },
                title = {
                    Text(
                        text = "Focus Scratchpad", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(onClick = saveAndClose) { 
                        Icon(
                            imageVector = Icons.Default.Check, 
                            contentDescription = "Save & Done", 
                            tint = CyberEmerald
                        ) 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp
                ),
                cursorBrush = SolidColor(CyberEmerald),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        if (title.isEmpty()) {
                            Text(
                                "Untitled Note",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                )
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateString,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "${contentValue.text.length} chars",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            BasicTextField(
                value = contentValue,
                onValueChange = { contentValue = it },
                onTextLayout = { textLayoutResult = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(CyberEmerald),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        if (contentValue.text.isEmpty()) {
                            Text(
                                "Start typing thoughts, focus notes, or code snippets...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 400.dp)
            )

            // Extra generous breathing space at the bottom so you can ALWAYS scroll past the last sentence!
            Spacer(modifier = Modifier.height(260.dp))
        }
    }
}

@Composable
fun NotesTimelineScreen(
    notes: List<NoteEntity>,
    events: List<TimelineEventEntity>,
    scratchpads: List<ScratchpadEntity>,
    isEmbedded: Boolean = true,
    onAddNote: (String, String) -> Unit,
    onToggleNote: (NoteEntity, Boolean) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onDeleteEvent: (TimelineEventEntity) -> Unit,
    onCreateNewScratchpad: () -> Unit,
    onOpenScratchpad: (ScratchpadEntity) -> Unit,
    onDeleteScratchpad: (ScratchpadEntity) -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("🎯 Tasks & Timeline", "📝 Scratchpads")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTabIndex == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedTabIndex = index },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (selectedTabIndex == 0) {
                TasksAndTimelineTab(notes, events, onAddNote, onToggleNote, onDeleteNote, onDeleteEvent)
            } else {
                ScratchpadsListTab(scratchpads, onCreateNewScratchpad, onOpenScratchpad, onDeleteScratchpad)
            }
        }
    }
}

@Composable
fun TasksAndTimelineTab(
    notes: List<NoteEntity>,
    events: List<TimelineEventEntity>,
    onAddNote: (String, String) -> Unit,
    onToggleNote: (NoteEntity, Boolean) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onDeleteEvent: (TimelineEventEntity) -> Unit
) {
    var newNoteText by remember { mutableStateOf("") }
    var newNoteDeadline by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add Focus Task",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newNoteText,
                        onValueChange = { newNoteText = it },
                        placeholder = { Text("What needs focus today?", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newNoteDeadline,
                            onValueChange = { newNoteDeadline = it },
                            placeholder = { Text("Deadline (e.g. 5:00 PM)", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = {
                                if (newNoteText.isNotBlank()) {
                                    onAddNote(newNoteText, newNoteDeadline)
                                    newNoteText = ""
                                    newNoteDeadline = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (notes.isNotEmpty()) {
            item {
                Text(
                    text = "Active Tasks (${notes.count { !it.isChecked }}/${notes.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }
            items(notes, key = { it.id }) { note ->
                ModernTaskItemCard(
                    note = note,
                    onCheckedChange = { onToggleNote(note, it) },
                    onDelete = { onDeleteNote(note) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Live Activity Timeline",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (events.isEmpty()) {
            item {
                Text(
                    text = "No timeline events recorded yet.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
        } else {
            itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
                ModernTimelineItemCard(
                    event = event,
                    isLast = (index == events.size - 1),
                    onDelete = { onDeleteEvent(event) }
                )
            }
        }
    }
}

@Composable
fun ModernTaskItemCard(
    note: NoteEntity,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (note.isChecked) MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = note.isChecked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = CyberEmerald)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.text,
                    fontSize = 14.sp,
                    fontWeight = if (note.isChecked) FontWeight.Normal else FontWeight.SemiBold,
                    textDecoration = if (note.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (note.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                )
                if (note.deadline.isNotBlank()) {
                    Text(
                        text = "⏰ ${note.deadline}",
                        fontSize = 11.sp,
                        color = if (note.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else NeonCyan
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ModernTimelineItemCard(
    event: TimelineEventEntity,
    isLast: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(RoyalViolet)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = event.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = event.time,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (event.description.isNotBlank()) {
                Text(
                    text = event.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete Event",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun ScratchpadsListTab(
    scratchpads: List<ScratchpadEntity>,
    onCreateNewScratchpad: () -> Unit,
    onOpenScratchpad: (ScratchpadEntity) -> Unit,
    onDeleteScratchpad: (ScratchpadEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onCreateNewScratchpad() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create New Scratchpad",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (scratchpads.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No scratchpads yet. Tap above to create one!",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            items(scratchpads, key = { it.id }) { pad ->
                val dateFormat = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
                val updatedString = dateFormat.format(Date(pad.updatedAt))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenScratchpad(pad) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pad.title.ifBlank { "Untitled Note" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pad.content.ifBlank { "Empty note..." },
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = updatedString,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(onClick = { onDeleteScratchpad(pad) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
