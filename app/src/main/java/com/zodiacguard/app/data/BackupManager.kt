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
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackupManager(private val context: Context) {
    
    private val workDataStore = WorkDataStore(context)
    private val contractDataStore = ContractDataStore(context)
    private val nfcBadgeManager = NfcBadgeManager(context)
    private val manualCounterDataStore = ManualCounterDataStore(context)
    private val backupMutex = Mutex()

    data class BackupSummary(
        val entryCount: Int,
        val counterCount: Int,
        val productionDayCount: Int,
        val noteCount: Int
    )

    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_FOLDER_NAME = "ZGuard"
        private const val BACKUP_FILE_NAME = "ZGuard_Backup.json"
        private const val BACKUP_HISTORY_FOLDER_NAME = "History"
        private const val BACKUP_HISTORY_PREFIX = "ZGuard_Backup_"
        private const val MAX_BACKUP_HISTORY = 20
    }

    /**
     * Esegue il backup usando MediaStore (Android 11+)
     */
    suspend fun performAutoBackup(): Boolean {
        return backupMutex.withLock {
            performAutoBackupLocked()
        }
    }

    private suspend fun performAutoBackupLocked(): Boolean {
        return try {
            val backupJson = createBackupJson()
            val jsonString = backupJson.toString(2)
            saveInternalBackup(jsonString)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.d(TAG, "Backup aggiornato nell'archivio privato dell'app")
            } else {
                saveBackupToDownloads(jsonString)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Errore backup: ${e.message}", e)
            false
        }
    }

    suspend fun createBackupNow(): Boolean = performAutoBackup()

    suspend fun createInternalRecoveryBackup(): Boolean =
        try {
            saveInternalBackup(createBackupJson().toString(2))
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Backup locale di sicurezza non riuscito", exception)
            false
        }

    suspend fun exportBackup(uri: Uri): BackupSummary {
        val backup = createBackupJson()
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            output.write(backup.toString(2).toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: throw IOException("Impossibile scrivere il file di backup")
        return BackupSummary(
            entryCount = backup.getJSONArray("entries").length(),
            counterCount = backup.getJSONArray("manualCounters").length(),
            productionDayCount = backup.getJSONArray("productionDays").length(),
            noteCount = backup.getJSONObject("dailyNotes").length()
        )
    }

    fun getInternalBackupUri(): Uri? {
        val historyDirectory = File(
            File(context.filesDir, BACKUP_FOLDER_NAME),
            BACKUP_HISTORY_FOLDER_NAME
        )
        val latestHistory = historyDirectory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(BACKUP_HISTORY_PREFIX) }
            ?.maxByOrNull { it.name }
        return if (latestHistory != null) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                latestHistory
            )
        } else {
            getLegacyBackupUri()
        }
    }

    private suspend fun createBackupJson(): JSONObject {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val entries = workDataStore.entriesFlow.first()
        val productionDays = workDataStore.productionDaysFlow.first()
        val weeklyHours = contractDataStore.weeklyHoursFlow.first()
        val weeklyMinutes = contractDataStore.weeklyMinutesFlow.first()
        val workDays = contractDataStore.workDaysFlow.first()
        val nfcUid = nfcBadgeManager.savedBadgeUid.first()
        val manualCounters = manualCounterDataStore.countersFlow.first()
        val dailyNotes = workDataStore.notesFlow.first()

        val backupJson = JSONObject()
            .put("version", "1.1")
            .put("lastUpdate", now)
        val settings = JSONObject()
            .put("weeklyHours", weeklyHours)
            .put("weeklyMinutes", weeklyMinutes)
            .put("workDays", workDays)
            .put("nfcBadgeUid", nfcUid ?: "")
            .put("displayName", UserProfilePreferences.getDisplayName(context))
        backupJson.put("settings", settings)

        val countersArray = JSONArray()
        manualCounters.forEach { counter ->
            countersArray.put(
                JSONObject()
                    .put("id", counter.id)
                    .put("title", counter.title)
                    .put("initialMinutes", counter.initialMinutes)
                    .put("currentMinutes", counter.currentMinutes)
                    .put("dayLengthMinutes", counter.dayLengthMinutes)
            )
        }
        backupJson.put("manualCounters", countersArray)
        backupJson.put("productionDays", JSONArray().apply {
            productionDays.forEach { put(it) }
        })
        backupJson.put("dailyNotes", JSONObject().apply {
            dailyNotes.forEach { (date, note) -> put(date, note) }
        })
        backupJson.put("entries", JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject()
                        .put("timestamp", entry.timestamp)
                        .put("type", entry.type)
                        .put("activity", entry.activity)
                        .put("location", entry.location)
                        .put("date", entry.date)
                )
            }
        })
        return backupJson
    }

    private fun saveInternalBackup(jsonString: String) {
        val backupFile = getLegacyBackupFile()
        val backupDirectory = backupFile.parentFile
            ?: throw IllegalStateException("Cartella di backup non disponibile")
        val historyDirectory = File(backupDirectory, BACKUP_HISTORY_FOLDER_NAME)
        if (!historyDirectory.exists() && !historyDirectory.mkdirs()) {
            throw IllegalStateException("Impossibile creare lo storico dei backup")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())
        val existingContent = if (backupFile.isFile) backupFile.readText() else null
        if (existingContent == null || !hasSameBackupData(existingContent, jsonString)) {
            val historyFile = File(historyDirectory, "$BACKUP_HISTORY_PREFIX$timestamp.json")
            writeFileAtomically(historyFile, jsonString)
        }
        writeFileAtomically(backupFile, jsonString)

        historyDirectory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(BACKUP_HISTORY_PREFIX) }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_BACKUP_HISTORY)
            ?.forEach { oldBackup ->
                if (!oldBackup.delete()) {
                    Log.w(TAG, "Impossibile eliminare il vecchio backup ${oldBackup.name}")
                }
            }
    }

    private fun writeFileAtomically(destination: File, content: String) {
        val temporaryFile = File(destination.parentFile, "${destination.name}.tmp")
        try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (destination.exists() && !destination.delete()) {
                throw IOException("Impossibile sostituire ${destination.name}")
            }
            if (!temporaryFile.renameTo(destination)) {
                throw IOException("Impossibile salvare ${destination.name}")
            }
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    private fun hasSameBackupData(first: String, second: String): Boolean =
        try {
            JSONObject(first).apply { remove("lastUpdate") }.toString() ==
                JSONObject(second).apply { remove("lastUpdate") }.toString()
        } catch (_: Exception) {
            false
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveBackupToDownloads(jsonString: String) {
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())
        val existingBackups = queryBackupUris()
        val latestAvailable = existingBackups.firstOrNull { it.first == BACKUP_FILE_NAME }
            ?: existingBackups
                .filter { it.first.startsWith(BACKUP_HISTORY_PREFIX) }
                .maxByOrNull { it.first }
        val latestContent = latestAvailable?.let { (name, uri) ->
            resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IOException("Impossibile leggere il backup precedente $name")
        }
        if (latestContent == null || !hasSameBackupData(latestContent, jsonString)) {
            saveNewDownloadFile("$BACKUP_HISTORY_PREFIX$timestamp.json", jsonString)
        }

        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_FOLDER_NAME/"
        val existingLatest = existingBackups.firstOrNull { it.first == BACKUP_FILE_NAME }
        if (existingLatest == null) {
            saveNewDownloadFile(BACKUP_FILE_NAME, jsonString)
        } else {
            resolver.openOutputStream(existingLatest.second, "wt")
                ?.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("Impossibile aggiornare il backup più recente")
            Log.d(TAG, "Backup principale aggiornato in $relativePath")
        }
        trimDownloadHistory()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveNewDownloadFile(displayName: String, jsonString: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_FOLDER_NAME"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Impossibile creare $displayName")
        try {
            resolver.openOutputStream(uri, "w")
                ?.use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("Impossibile scrivere $displayName")
            val completed = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            if (resolver.update(uri, completed, null, null) != 1) {
                throw IOException("Impossibile completare $displayName")
            }
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            throw exception
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun trimDownloadHistory() {
        val resolver = context.contentResolver
        val history = queryBackupUris()
            .filter { it.first.matches(Regex("""^ZGuard_Backup_\d{8}_\d{6}_\d{3}\.json$""")) }
            .sortedByDescending { it.first }
        history.drop(MAX_BACKUP_HISTORY).forEach { (_, uri) ->
            if (resolver.delete(uri, null, null) == 0) {
                Log.w(TAG, "Impossibile eliminare uno snapshot di backup scaduto")
            }
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
            ?: matchingBackups
                .filter { it.first.startsWith(BACKUP_HISTORY_PREFIX) }
                .maxByOrNull { it.first }
                ?.second
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
            name.matches(Regex("""^ZGuard_Backup \(\d+\)\.json$""")) ||
            name.matches(Regex("""^ZGuard_Backup_\d{8}_\d{6}_\d{3}\.json$"""))

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
