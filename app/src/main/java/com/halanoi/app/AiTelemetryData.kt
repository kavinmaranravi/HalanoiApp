package com.halanoi.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// 1. AI TELEMETRY ENTITY (Ground Truth & Dataset)
// ==========================================

@Entity(tableName = "ai_telemetry")
data class AiTelemetryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String,
    val packageName: String,
    val scrapedText: String,
    val predictedLabel: String,
    val confidenceScore: Float,
    val wasBlocked: Boolean,
    val groundTruthFeedback: String = "UNRATED" // UNRATED, CORRECT, FALSE_POSITIVE, FALSE_NEGATIVE
)

// ==========================================
// 2. AI TELEMETRY DAO
// ==========================================

@Dao
interface AiTelemetryDao {
    @Query("SELECT * FROM ai_telemetry ORDER BY timestamp DESC LIMIT 500")
    fun getAllTelemetry(): Flow<List<AiTelemetryEntity>>

    @Query("SELECT * FROM ai_telemetry ORDER BY timestamp DESC")
    fun getAllTelemetryDirect(): List<AiTelemetryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: AiTelemetryEntity)

    @Query("UPDATE ai_telemetry SET groundTruthFeedback = :feedback WHERE id = :id")
    fun updateFeedback(id: String, feedback: String)

    @Delete
    fun delete(entity: AiTelemetryEntity)

    @Query("DELETE FROM ai_telemetry")
    fun clearAll()

    @Query("SELECT COUNT(*) FROM ai_telemetry")
    fun getCount(): Flow<Int>
}

// ==========================================
// 3. DATASET EXPORTER UTILITIES (CSV / JSON)
// ==========================================

object DatasetExporter {

    fun exportToCsv(context: Context, telemetryList: List<AiTelemetryEntity>): Uri? {
        return try {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val filename = "halanoi_ai_dataset_${dateFormat.format(Date())}.csv"
            val file = File(context.cacheDir, filename)

            val writer = FileWriter(file)
            writer.append("id,timestamp,formatted_date,appName,packageName,scrapedText,predictedLabel,confidenceScore,wasBlocked,groundTruthFeedback\n")

            val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            for (item in telemetryList) {
                val safeText = item.scrapedText.replace("\"", "\"\"").replace("\n", " ").trim()
                val dateStr = timeFormatter.format(Date(item.timestamp))
                writer.append("\"${item.id}\",")
                writer.append("${item.timestamp},")
                writer.append("\"$dateStr\",")
                writer.append("\"${item.appName}\",")
                writer.append("\"${item.packageName}\",")
                writer.append("\"$safeText\",")
                writer.append("\"${item.predictedLabel}\",")
                writer.append("${item.confidenceScore},")
                writer.append("${item.wasBlocked},")
                writer.append("\"${item.groundTruthFeedback}\"\n")
            }
            writer.flush()
            writer.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportToJson(context: Context, telemetryList: List<AiTelemetryEntity>): Uri? {
        return try {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val filename = "halanoi_ai_dataset_${dateFormat.format(Date())}.json"
            val file = File(context.cacheDir, filename)

            val rootArray = JSONArray()
            val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            for (item in telemetryList) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("formatted_date", timeFormatter.format(Date(item.timestamp)))
                    put("appName", item.appName)
                    put("packageName", item.packageName)
                    put("scrapedText", item.scrapedText)
                    put("predictedLabel", item.predictedLabel)
                    put("confidenceScore", item.confidenceScore)
                    put("wasBlocked", item.wasBlocked)
                    put("groundTruthFeedback", item.groundTruthFeedback)
                }
                rootArray.put(obj)
            }

            val writer = FileWriter(file)
            writer.write(rootArray.toString(2))
            writer.flush()
            writer.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareExportFile(context: Context, uri: Uri, mimeType: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
