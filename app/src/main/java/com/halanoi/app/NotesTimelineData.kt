package com.halanoi.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// ==========================================
// 1. ENTITIES (The Database Tables)
// ==========================================

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val deadline: String = "",
    val isChecked: Boolean = false
)

@Entity(tableName = "scratchpad")
data class ScratchpadEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "timeline_events")
data class TimelineEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val time: String,
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ==========================================
// 2. DAO (Data Access Object)
// ==========================================

@Dao
interface AppDao {
    // --- Notes ---
    @Query("SELECT * FROM notes")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    fun getAllNotesDirect(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNote(note: NoteEntity)

    @Update
    fun updateNote(note: NoteEntity)

    @Delete
    fun deleteNote(note: NoteEntity)

    // --- Timeline Events ---
    @Query("SELECT * FROM timeline_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events ORDER BY timestamp DESC")
    fun getAllEventsDirect(): List<TimelineEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEvent(event: TimelineEventEntity)

    @Delete
    fun deleteEvent(event: TimelineEventEntity)

    // --- Scratchpads ---
    @Query("SELECT * FROM scratchpad ORDER BY updatedAt DESC")
    fun getAllScratchpads(): Flow<List<ScratchpadEntity>>

    @Query("SELECT * FROM scratchpad ORDER BY updatedAt DESC")
    fun getAllScratchpadsDirect(): List<ScratchpadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertScratchpad(scratchpad: ScratchpadEntity)

    @Delete
    fun deleteScratchpad(scratchpad: ScratchpadEntity)
}

// ==========================================
// 3. ROOM DATABASE & PERMANENT BACKUP HELPER
// ==========================================
@Database(entities = [NoteEntity::class, TimelineEventEntity::class, ScratchpadEntity::class, AiTelemetryEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun aiTelemetryDao(): AiTelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "halanoi_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object PermanentBackupManager {
    private const val BACKUP_FILENAME = "halanoi_notes_backup.json"

    fun saveBackup(context: Context, scratchpads: List<ScratchpadEntity>, notes: List<NoteEntity>, events: List<TimelineEventEntity>) {
        if (scratchpads.isEmpty() && notes.isEmpty() && events.isEmpty()) return
        try {
            val root = JSONObject()

            val scratchpadArray = JSONArray()
            scratchpads.forEach {
                val obj = JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("content", it.content)
                    put("updatedAt", it.updatedAt)
                }
                scratchpadArray.put(obj)
            }
            root.put("scratchpads", scratchpadArray)

            val notesArray = JSONArray()
            notes.forEach {
                val obj = JSONObject().apply {
                    put("id", it.id)
                    put("text", it.text)
                    put("deadline", it.deadline)
                    put("isChecked", it.isChecked)
                }
                notesArray.put(obj)
            }
            root.put("notes", notesArray)

            val eventsArray = JSONArray()
            events.forEach {
                val obj = JSONObject().apply {
                    put("id", it.id)
                    put("time", it.time)
                    put("title", it.title)
                    put("description", it.description)
                    put("timestamp", it.timestamp)
                }
                eventsArray.put(obj)
            }
            root.put("events", eventsArray)

            val jsonText = root.toString(2)

            // 1. Internal filesDir
            try {
                File(context.filesDir, BACKUP_FILENAME).writeText(jsonText)
            } catch (e: Exception) {
                Log.e("HalanoiBackup", "Internal write failed: ${e.message}")
            }

            // 2. App-scoped external filesDir
            try {
                context.getExternalFilesDir(null)?.let {
                    File(it, BACKUP_FILENAME).writeText(jsonText)
                }
            } catch (e: Exception) {
                Log.e("HalanoiBackup", "External files write failed: ${e.message}")
            }

            // 3. Direct Public Documents & Downloads
            try {
                val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Halanoi")
                if (!docsDir.exists()) docsDir.mkdirs()
                File(docsDir, BACKUP_FILENAME).writeText(jsonText)
            } catch (e: Exception) {
                Log.w("HalanoiBackup", "Direct Documents write failed: ${e.message}")
            }

            try {
                val dlDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Halanoi")
                if (!dlDir.exists()) dlDir.mkdirs()
                File(dlDir, BACKUP_FILENAME).writeText(jsonText)
            } catch (e: Exception) {
                Log.w("HalanoiBackup", "Direct Downloads write failed: ${e.message}")
            }

            // 4. MediaStore Downloads (100% Android Scoped Storage compliant - survives uninstalls)
            try {
                writeToMediaStoreDownloads(context, jsonText)
            } catch (e: Exception) {
                Log.e("HalanoiBackup", "MediaStore backup error: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("HalanoiBackup", "Failed to save permanent backup: ${e.message}")
        }
    }

    private fun writeToMediaStoreDownloads(context: Context, jsonText: String) {
        val resolver = context.contentResolver
        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(BACKUP_FILENAME)

        var existingUri: Uri? = null
        resolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                existingUri = ContentUris.withAppendedId(queryUri, id)
            }
        }

        val targetUri = existingUri ?: run {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_FILENAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Halanoi")
                }
            }
            resolver.insert(queryUri, contentValues)
        }

        if (targetUri != null) {
            resolver.openOutputStream(targetUri, "wt")?.use { os ->
                os.write(jsonText.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            Log.i("HalanoiBackup", "Successfully saved backup via MediaStore: $targetUri")
        }
    }

    suspend fun restoreIfEmpty(context: Context, dao: AppDao) {
        try {
            val existingPads = dao.getAllScratchpadsDirect()
            val existingNotes = dao.getAllNotesDirect()
            if (existingPads.isNotEmpty() || existingNotes.isNotEmpty()) return

            var jsonStr = readFromMediaStoreDownloads(context)

            if (jsonStr.isNullOrBlank()) {
                val candidateFiles = listOf(
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Halanoi/$BACKUP_FILENAME"),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Halanoi/$BACKUP_FILENAME"),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BACKUP_FILENAME),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), BACKUP_FILENAME),
                    File(context.filesDir, BACKUP_FILENAME),
                    context.getExternalFilesDir(null)?.let { File(it, BACKUP_FILENAME) }
                ).filterNotNull()

                val targetFile = candidateFiles.firstOrNull { it.exists() && it.length() > 0 }
                if (targetFile != null) {
                    jsonStr = targetFile.readText()
                }
            }

            if (jsonStr.isNullOrBlank()) {
                Log.w("HalanoiBackup", "No existing backup found to restore.")
                return
            }

            val root = JSONObject(jsonStr)

            val scratchpads = root.optJSONArray("scratchpads")
            if (scratchpads != null) {
                for (i in 0 until scratchpads.length()) {
                    val obj = scratchpads.getJSONObject(i)
                    dao.insertScratchpad(
                        ScratchpadEntity(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title", ""),
                            content = obj.optString("content", ""),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            val notes = root.optJSONArray("notes")
            if (notes != null) {
                for (i in 0 until notes.length()) {
                    val obj = notes.getJSONObject(i)
                    dao.insertNote(
                        NoteEntity(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            text = obj.optString("text", ""),
                            deadline = obj.optString("deadline", ""),
                            isChecked = obj.optBoolean("isChecked", false)
                        )
                    )
                }
            }

            val events = root.optJSONArray("events")
            if (events != null) {
                for (i in 0 until events.length()) {
                    val obj = events.getJSONObject(i)
                    dao.insertEvent(
                        TimelineEventEntity(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            time = obj.optString("time", ""),
                            title = obj.optString("title", ""),
                            description = obj.optString("description", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
            Log.i("HalanoiBackup", "Successfully auto-restored notes and tasks from permanent backup!")
        } catch (e: Exception) {
            Log.e("HalanoiBackup", "Failed to auto-restore backup: ${e.message}")
        }
    }

    private fun readFromMediaStoreDownloads(context: Context): String? {
        try {
            val resolver = context.contentResolver
            val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(BACKUP_FILENAME)

            resolver.query(queryUri, projection, selection, selectionArgs, "${MediaStore.MediaColumns._ID} DESC")?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val contentUri = ContentUris.withAppendedId(queryUri, id)
                    resolver.openInputStream(contentUri)?.use { inputStream ->
                        val text = inputStream.bufferedReader().readText()
                        if (text.isNotBlank()) {
                            Log.i("HalanoiBackup", "Read backup via MediaStore ($contentUri)")
                            return text
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("HalanoiBackup", "MediaStore read failed: ${e.message}")
        }
        return null
    }
}

// ==========================================
// 4. VIEWMODEL (The Bridge)
// ==========================================

class NotesTimelineViewModel(
    private val dao: AppDao,
    private val context: Context? = null
) : ViewModel() {
    
    val notes: Flow<List<NoteEntity>> = dao.getAllNotes()
    val events: Flow<List<TimelineEventEntity>> = dao.getAllEvents()
    val scratchpads: Flow<List<ScratchpadEntity>> = dao.getAllScratchpads()

    init {
        context?.let { ctx ->
            viewModelScope.launch(Dispatchers.IO) {
                PermanentBackupManager.restoreIfEmpty(ctx, dao)
            }
        }
    }

    private fun triggerBackup() {
        context?.let { ctx ->
            viewModelScope.launch(Dispatchers.IO) {
                val pads = dao.getAllScratchpadsDirect()
                val nts = dao.getAllNotesDirect()
                val evts = dao.getAllEventsDirect()
                PermanentBackupManager.saveBackup(ctx, pads, nts, evts)
            }
        }
    }

    // --- Note Actions ---
    fun addNote(text: String, deadline: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertNote(NoteEntity(text = text, deadline = deadline))
            triggerBackup()
        }
    }

    fun toggleNoteCheck(note: NoteEntity, isChecked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateNote(note.copy(isChecked = isChecked))
            triggerBackup()
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) { 
            dao.deleteNote(note) 
            triggerBackup()
        }
    }

    // --- Event Actions ---
    fun addEvent(time: String, title: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertEvent(TimelineEventEntity(time = time, title = title, description = description))
            triggerBackup()
        }
    }

    fun deleteEvent(event: TimelineEventEntity) {
        viewModelScope.launch(Dispatchers.IO) { 
            dao.deleteEvent(event) 
            triggerBackup()
        }
    }

    // --- Scratchpad Actions ---
    fun createEmptyScratchpad(): String {
        val newId = UUID.randomUUID().toString()
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertScratchpad(ScratchpadEntity(id = newId, title = "", content = ""))
            triggerBackup()
        }
        return newId
    }

    fun updateScratchpad(scratchpad: ScratchpadEntity, newTitle: String, newContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertScratchpad(
                scratchpad.copy(
                    title = newTitle, 
                    content = newContent, 
                    updatedAt = System.currentTimeMillis()
                )
            )
            triggerBackup()
        }
    }

    fun deleteScratchpad(scratchpad: ScratchpadEntity) {
        viewModelScope.launch(Dispatchers.IO) { 
            dao.deleteScratchpad(scratchpad) 
            triggerBackup()
        }
    }
}
