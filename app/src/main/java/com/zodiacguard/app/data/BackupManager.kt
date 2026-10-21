package com.zodiacguard.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
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
            settings.put("displayName", UserProfilePreferences.getDisplayName(context))
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

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val backupFile = getLegacyBackupFile()
                val backupDirectory = backupFile.parentFile
                    ?: throw IllegalStateException("Cartella di backup non disponibile")
                if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
                    throw IllegalStateException("Impossibile creare la cartella di backup")
                }
                backupFile.writeText(jsonString)
                Log.d(TAG, "Backup salvato nell'archivio privato dell'app")
            } else {
                saveBackupToDownloads(jsonString)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Errore backup: ${e.message}", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveBackupToDownloads(jsonString: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_FOLDER_NAME")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val existingBackups = queryBackupUris()
        val existingBackup = existingBackups.firstOrNull { it.first == BACKUP_FILE_NAME }
        val uri = existingBackup?.second ?: resolver.insert(collection, contentValues)
            ?: throw IOException("Impossibile creare il file di backup")

        try {
            resolver.openOutputStream(uri, if (existingBackup != null) "wt" else "w")
                ?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                } ?: throw IOException("Impossibile scrivere il file di backup")

            if (existingBackup == null) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
            }

            existingBackups
                .filter { it.second != uri }
                .forEach { (_, duplicateUri) ->
                    if (resolver.delete(duplicateUri, null, null) == 0) {
                        throw IOException("Impossibile rimuovere un backup duplicato")
                    }
                }

            Log.d(TAG, "Backup aggiornato via MediaStore: $uri")
        } catch (e: Exception) {
            if (existingBackup == null) {
                resolver.delete(uri, null, null)
            }
            throw e
        }
    }

    /**
     * Trova l'URI del backup esistente
     */
    private fun findExistingBackupUri(): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return getLegacyBackupUri()
        }

        val matchingBackups = queryBackupUris()
        return matchingBackups.firstOrNull { it.first == BACKUP_FILE_NAME }?.second
            ?: matchingBackups.firstOrNull()?.second
            ?: getLegacyBackupUri()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryBackupUris(): List<Pair<String, Uri>> {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_FOLDER_NAME/"
        val backups = mutableListOf<Pair<String, Uri>>()

        val cursor = context.contentResolver.query(
            collection,
            projection,
            selection,
            arrayOf(relativePath),
            null
        ) ?: throw IOException("Impossibile cercare il file di backup")

        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (it.moveToNext()) {
                val name = it.getString(nameColumn)
                if (isBackupFileName(name)) {
                    val id = it.getLong(idColumn)
                    backups.add(name to Uri.withAppendedPath(collection, id.toString()))
                }
            }
        }
        return backups
    }

    private fun isBackupFileName(name: String): Boolean =
        name == BACKUP_FILE_NAME ||
            name.matches(Regex("""^ZGuard_Backup \(\d+\)\.json$"""))

    private fun getLegacyBackupFile(): File =
        File(File(context.filesDir, BACKUP_FOLDER_NAME), BACKUP_FILE_NAME)

    private fun getLegacyBackupUri(): Uri? {
        val backupFile = getLegacyBackupFile()
        return if (backupFile.isFile) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )
        } else {
            null
        }
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
