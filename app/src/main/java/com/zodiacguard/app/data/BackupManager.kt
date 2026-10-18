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
    }

    /**
     * Esegue il backup totale dei dati nella cartella Download/ZGuard
     */
    suspend fun performAutoBackup(): Boolean {
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            
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

            // 2. Raccogli tutti i dati
            val entries = workDataStore.entriesFlow.first()
            val productionDays = workDataStore.productionDaysFlow.first()
            val weeklyHours = contractDataStore.weeklyHoursFlow.first()
            val workDays = contractDataStore.workDaysFlow.first()
            val nfcUid = nfcBadgeManager.savedBadgeUid.first()

            // 3. Costruisci il JSON
            val backupJson = JSONObject()
            backupJson.put("version", "1.0")
            backupJson.put("backupDate", today)
            
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

            // 4. Scrivi su file (sovrascrive se esiste già per oggi)
            val backupFile = File(backupDir, "ZGuard_Total_Backup_$today.json")
            backupFile.writeText(backupJson.toString(2)) // 2 = indentazione per leggibilità

            Log.d(TAG, "Backup totale salvato con successo in: ${backupFile.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Errore durante il backup automatico: ${e.message}", e)
            false
        }
    }

    /**
     * Controlla se esiste un backup nella cartella e ne restituisce il percorso
     */
    fun getLatestBackupFile(): File? {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val backupDir = File(downloadDir, BACKUP_FOLDER_NAME)
        
        if (backupDir.exists() && backupDir.isDirectory) {
            val files = backupDir.listFiles { _, name -> name.startsWith("ZGuard_Total_Backup_") && name.endsWith(".json") }
            if (!files.isNullOrEmpty()) {
                // Ordina per nome (che contiene la data) e prendi l'ultimo
                files.sortByDescending { it.name }
                return files.first()
            }
        }
        return null
    }
}
