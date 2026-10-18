package com.zodiacguard.app.data

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    suspend fun performAutoBackup(): Boolean {
        return try {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupDir = File(downloadDir, BACKUP_FOLDER_NAME)
            
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            // 1. Raccogli dati
            val entries = workDataStore.entriesFlow.first()
            val productionDays = workDataStore.productionDaysFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val workDays = contractDataStore.workDaysFlow.first()
            val nfcUid = nfcBadgeManager.savedBadgeUid.first()

            // 2. Raccogli Note (per ogni data presente nelle marcature)
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

            // Aggiungi le Note
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

            // 4. Scrivi file
            val backupFile = File(backupDir, BACKUP_FILE_NAME)
            backupFile.writeText(backupJson.toString(2))

            Log.d(TAG, "Backup totale (con note) salvato in: ${backupFile.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Errore backup: ${e.message}", e)
            false
        }
    }

    fun getBackupFile(): File? {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val backupDir = File(downloadDir, BACKUP_FOLDER_NAME)
        val backupFile = File(backupDir, BACKUP_FILE_NAME)
        return if (backupFile.exists()) backupFile else null
    }
}
