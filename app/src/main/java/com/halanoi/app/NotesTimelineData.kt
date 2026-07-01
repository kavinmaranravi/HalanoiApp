package com.halanoi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
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

// CHANGED: Now acts as a list of multiple files/folders instead of a single canvas
@Entity(tableName = "scratchpad")
data class ScratchpadEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String, // E.g., "Technology", "Topic One"
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis() // To sort by recently edited
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNote(note: NoteEntity)

    @Update
    fun updateNote(note: NoteEntity)

    // NEW: Delete note
    @Delete
    fun deleteNote(note: NoteEntity)

    // --- Timeline Events ---
    @Query("SELECT * FROM timeline_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<TimelineEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEvent(event: TimelineEventEntity)

    // NEW: Delete event
    @Delete
    fun deleteEvent(event: TimelineEventEntity)

    // --- Scratchpads (Multiple Canvases) ---
    @Query("SELECT * FROM scratchpad ORDER BY updatedAt DESC")
    fun getAllScratchpads(): Flow<List<ScratchpadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertScratchpad(scratchpad: ScratchpadEntity)

    // NEW: Delete scratchpad
    @Delete
    fun deleteScratchpad(scratchpad: ScratchpadEntity)
}

// ==========================================
// 3. ROOM DATABASE
// ==========================================
@Database(entities = [NoteEntity::class, TimelineEventEntity::class, ScratchpadEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
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

// ==========================================
// 4. VIEWMODEL (The Bridge)
// ==========================================

class NotesTimelineViewModel(private val dao: AppDao) : ViewModel() {
    
    val notes: Flow<List<NoteEntity>> = dao.getAllNotes()
    val events: Flow<List<TimelineEventEntity>> = dao.getAllEvents()
    val scratchpads: Flow<List<ScratchpadEntity>> = dao.getAllScratchpads()

    // --- Note Actions ---
    fun addNote(text: String, deadline: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertNote(NoteEntity(text = text, deadline = deadline))
        }
    }

    fun toggleNoteCheck(note: NoteEntity, isChecked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateNote(note.copy(isChecked = isChecked))
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteNote(note) }
    }

    // --- Event Actions ---
    fun addEvent(time: String, title: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertEvent(TimelineEventEntity(time = time, title = title, description = description))
        }
    }

    fun deleteEvent(event: TimelineEventEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteEvent(event) }
    }

    // --- Scratchpad Actions ---
    
    // UPDATED: Creates an empty note and immediately returns its ID so we can open it full-screen
    fun createEmptyScratchpad(): String {
        val newId = UUID.randomUUID().toString()
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertScratchpad(ScratchpadEntity(id = newId, title = "", content = ""))
        }
        return newId
    }

    // UPDATED: Now saves both the title and the content
    fun updateScratchpad(scratchpad: ScratchpadEntity, newTitle: String, newContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertScratchpad(
                scratchpad.copy(
                    title = newTitle, 
                    content = newContent, 
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteScratchpad(scratchpad: ScratchpadEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteScratchpad(scratchpad) }
    }
}
