package com.zodiacguard.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class BackupManager(private val context: Context) {
    
    private val workDataStore = WorkDataStore(context)
    private val contractDataStore = ContractDataStore(context)
    private val nfcBadgeManager = NfcBadgeManager(context)

    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_FOLDER_NAME = "ZGuard"
        private const val BACKUP_FILE_NAME = "ZGuard_Backup.json"
    }

    /**
     * Esegue il backup usando MediaStore (Android 11+)
     */
    suspend fun performAutoBackup(): Boolean {
        return try {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            // 1. Raccogli tutti i dati attuali
            val entries = workDataStore.entriesFlow.first()
            val productionDays = workDataStore.productionDaysFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val workDays = contractDataStore.workDaysFlow.first()
            val nfcUid = nfcBadgeManager.savedBadgeUid.first()

            // 2. Raccogli Note
            val dates = entries.map { it.date }.distinct()
            val notesJson = JSONObject()
            for (date in dates) {
                val note = workDataStore.getNoteForDate(date).first()
                if (note.isNotEmpty()) {
                    notesJson.put(date, note)
                }
            }

            // 3. Costruisci JSON
            val backupJson = JSONObject()
            backupJson.put("version", "1.0")
            backupJson.put("lastUpdate", now)
            
            val settings = JSONObject()
            settings.put("weeklyHours", weeklyHours)
            settings.put("workDays", workDays)
            settings.put("nfcBadgeUid", nfcUid ?: "")
            backupJson.put("settings", settings)

            val prodArray = JSONArray()
            productionDays.forEach { prodArray.put(it) }
            backupJson.put("productionDays", prodArray)

            backupJson.put("dailyNotes", notesJson)

            val entriesArray = JSONArray()
            entries.forEach { entry ->
                val entryJson = JSONObject()
                entryJson.put("timestamp", entry.timestamp)
                entryJson.put("type", entry.type)
                entryJson.put("activity", entry.activity)
                entryJson.put("location", entry.location)
                entryJson.put("date", entry.date)
                entriesArray.put(entryJson)
            }
            backupJson.put("entries", entriesArray)

            val jsonString = backupJson.toString(2)

            // 4. Salva usando MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_FOLDER_NAME")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            
            // Cerca se esiste già un file con lo stesso nome e cancellalo
            val existingUri = findExistingBackupUri()
            if (existingUri != null) {
                resolver.delete(existingUri, null, null)
                Log.d(TAG, "Vecchio backup eliminato")
            }

            // Inserisci nuovo file
            val uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                
                // Aggiorna IS_PENDING a 0 (file completo)
                val updateValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
                
                Log.d(TAG, "Backup salvato con successo via MediaStore: $uri")
                return true
            } else {
                Log.e(TAG, "Impossibile creare il file di backup")
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Errore backup: ${e.message}", e)
            return false
        }
    }

    /**
     * Trova l'URI del backup esistente
     */
    private fun findExistingBackupUri(): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.RELATIVE_PATH
        )
        
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(BACKUP_FILE_NAME, "%$BACKUP_FOLDER_NAME%")
        
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return Uri.withAppendedPath(collection, id.toString())
            }
        }
        return null
    }

    /**
     * Restituisce l'URI del backup esistente (per il ripristino)
     */
    fun getBackupUri(): Uri? {
        return findExistingBackupUri()
    }

    /**
     * Legge il contenuto del backup come stringa
     */
    fun readBackupContent(uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            resolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                reader.readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore lettura backup: ${e.message}", e)
            null
        }
    }
}
