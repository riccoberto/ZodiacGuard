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
        private const val BACKUP_FILE_NAME = "ZGuard_Backup.json" // UNICO FILE MASTER
    }

    /**
     * Esegue il backup incrementale (aggiorna il file master con lo stato attuale)
     */
    suspend fun performAutoBackup(): Boolean {
        return try {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            // 1. Prepara la cartella Download/ZGuard
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupDir = File(downloadDir, BACKUP_FOLDER_NAME)
            
            if (!backupDir.exists()) {
                val created = backupDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Impossibile creare la cartella di backup")
                    return false
                }
            }

            // 2. Raccogli tutti i dati attuali
            val entries = workDataStore.entriesFlow.first()
            val productionDays = workDataStore.productionDaysFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val workDays = contractDataStore.workDaysFlow.first()
            val nfcUid = nfcBadgeManager.savedBadgeUid.first()

            // 3. Costruisci il JSON
            val backupJson = JSONObject()
            backupJson.put("version", "1.0")
            backupJson.put("lastUpdate", now)
            
            // Impostazioni
            val settings = JSONObject()
            settings.put("weeklyHours", weeklyHours)
            settings.put("workDays", workDays)
            settings.put("nfcBadgeUid", nfcUid ?: "")
            backupJson.put("settings", settings)

            // Giorni produzione
            val prodArray = JSONArray()
            productionDays.forEach { prodArray.put(it) }
            backupJson.put("productionDays", prodArray)

            // Marcature
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

            // 4. Scrivi sul file master (sovrascrive il vecchio con i nuovi dati)
            val backupFile = File(backupDir, BACKUP_FILE_NAME)
            backupFile.writeText(backupJson.toString(2))

            Log.d(TAG, "Backup master aggiornato con successo: ${backupFile.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Errore durante il backup: ${e.message}", e)
            false
        }
    }

    /**
     * Controlla se esiste il file di backup master
     */
    fun getBackupFile(): File? {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val backupDir = File(downloadDir, BACKUP_FOLDER_NAME)
        val backupFile = File(backupDir, BACKUP_FILE_NAME)
        
        if (backupFile.exists()) {
            return backupFile
        }
        return null
    }
}
