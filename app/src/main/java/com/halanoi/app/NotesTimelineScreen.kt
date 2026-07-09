package com.halanoi.app

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotesTimelineRoute(viewModel: NotesTimelineViewModel) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val events by viewModel.events.collectAsState(initial = emptyList())
    val scratchpads by viewModel.scratchpads.collectAsState(initial = emptyList())

    var activeScratchpadId by remember { mutableStateOf<String?>(null) }
    
    val activeScratchpad = scratchpads.find { it.id == activeScratchpadId }

    Crossfade(targetState = activeScratchpadId != null, label = "Screen Transition") { isFullScreen ->
        if (isFullScreen && activeScratchpad != null) {
            FullScreenNoteEditor(
                pad = activeScratchpad,
                onBack = { activeScratchpadId = null },
                onUpdate = viewModel::updateScratchpad
            )
        } else {
            NotesTimelineScreen(
                notes = notes,
                events = events,
                scratchpads = scratchpads,
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
                    activeScratchpadId = newId
                },
                onOpenScratchpad = { pad ->
                    activeScratchpadId = pad.id
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
    var title by remember { mutableStateOf(pad.title) }
    var content by remember { mutableStateOf(pad.content) }

    LaunchedEffect(title, content) {
        onUpdate(pad, title, content)
    }

    val dateFormat = SimpleDateFormat("EEEE, MMMM d 'at' HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(pad.updatedAt))

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                title = {},
                actions = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Check, "Save/Done") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineLarge) },
                textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "$dateString  |  ${content.length} characters",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            TextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("Start typing...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesTimelineScreen(
    notes: List<NoteEntity>,
    events: List<TimelineEventEntity>,
    scratchpads: List<ScratchpadEntity>,
    onAddNote: (String, String) -> Unit,
    onToggleNote: (NoteEntity, Boolean) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onDeleteEvent: (TimelineEventEntity) -> Unit,
    onCreateNewScratchpad: () -> Unit,
    onOpenScratchpad: (ScratchpadEntity) -> Unit,
    onDeleteScratchpad: (ScratchpadEntity) -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Tasks & Timeline", "Notes")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (selectedTabIndex == 0) "Workspace" else "Notes", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTabIndex == 1) {
                FloatingActionButton(
                    onClick = onCreateNewScratchpad,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Note")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (selectedTabIndex == 0) {
                TasksAndTimelineTab(notes, events, onAddNote, onToggleNote, onDeleteNote, onDeleteEvent)
            } else {
                ScratchpadsListTab(scratchpads, onOpenScratchpad, onDeleteScratchpad)
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newNoteText,
                    onValueChange = { newNoteText = it },
                    placeholder = { Text("Task description...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newNoteDeadline,
                        onValueChange = { newNoteDeadline = it },
                        placeholder = { Text("Deadline (e.g. 5 PM)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newNoteText.isNotBlank()) {
                                onAddNote(newNoteText, newNoteDeadline)
                                newNoteText = ""
                                newNoteDeadline = ""
                            }
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        items(notes, key = { it.id }) { note ->
            NoteItemRow(note, onCheckedChange = { onToggleNote(note, it) }, onDelete = { onDeleteNote(note) })
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
            Text("Event Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
        }

        itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
            val isLastItem = index == events.size - 1
            TimelineItemRow(event, isLastItem, onDelete = { onDeleteEvent(event) })
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun ScratchpadsListTab(
    scratchpads: List<ScratchpadEntity>,
    onOpenScratchpad: (ScratchpadEntity) -> Unit,
    onDeleteScratchpad: (ScratchpadEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp) 
    ) {
        items(scratchpads, key = { it.id }) { pad ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onOpenScratchpad(pad) }, 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pad.title.ifBlank { "Untitled Note" }, 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pad.content.ifBlank { "No additional text..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(pad.updatedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = { onDeleteScratchpad(pad) }) {
                        Icon(Icons.Default.Delete, "Delete Note", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun NoteItemRow(note: NoteEntity, onCheckedChange: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = note.isChecked, onCheckedChange = onCheckedChange)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (note.isChecked) TextDecoration.LineThrough else TextDecoration.None
                )
                if (note.deadline.isNotBlank()) {
                    Text(text = "Due: ${note.deadline}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete Note", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun TimelineItemRow(event: TimelineEventEntity, isLastItem: Boolean, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            if (!isLastItem) {
                Box(modifier = Modifier.width(2.dp).height(80.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLastItem) 0.dp else 24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(event.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete Event", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                }
            }
        }
    }
}
