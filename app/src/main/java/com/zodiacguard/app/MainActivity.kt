package com.zodiacguard.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.zodiacguard.app.data.BackupManager
import com.zodiacguard.app.data.ContractDataStore
import com.zodiacguard.app.data.NfcBadgeManager
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.data.WorkEntry
import com.zodiacguard.app.ui.settings.NfcUnlockDialog
import com.zodiacguard.app.ui.settings.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null
    private lateinit var nfcBadgeManager: NfcBadgeManager
    private lateinit var backupManager: BackupManager

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nfcBadgeManager = NfcBadgeManager(this)
        backupManager = BackupManager(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        
        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tagFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        intentFiltersArray = arrayOf(techFilter, tagFilter)
        
        techListsArray = arrayOf(
            arrayOf(android.nfc.tech.NfcA::class.java.name),
            arrayOf(android.nfc.tech.NfcB::class.java.name),
            arrayOf(android.nfc.tech.NfcF::class.java.name),
            arrayOf(android.nfc.tech.NfcV::class.java.name),
            arrayOf(android.nfc.tech.IsoDep::class.java.name),
            arrayOf(android.nfc.tech.MifareClassic::class.java.name),
            arrayOf(android.nfc.tech.MifareUltralight::class.java.name),
            arrayOf(android.nfc.tech.Ndef::class.java.name),
            arrayOf(android.nfc.tech.NdefFormatable::class.java.name)
        )
        
        checkForExistingBackup()
    }

    private fun checkForExistingBackup() {
        val backupFile = backupManager.getBackupFile()
        if (backupFile != null && backupFile.exists()) {
            Log.d(TAG, "Backup trovato: ${backupFile.absolutePath}")
            
            val prefs = getSharedPreferences("zguard_prefs", Context.MODE_PRIVATE)
            val hasImported = prefs.getBoolean("has_imported_backup", false)
            
            if (!hasImported) {
                AlertDialog.Builder(this)
                    .setTitle("Backup Trovato")
                    .setMessage("Ho trovato un backup precedente nella cartella Download. Vuoi ripristinare i tuoi dati (marcature, note, contratto, badge)?")
                    .setPositiveButton("SÌ, RIPRISTINA") { dialog, _ ->
                        restoreFromBackup(backupFile)
                        prefs.edit().putBoolean("has_imported_backup", true).apply()
                        dialog.dismiss()
                    }
                    .setNegativeButton("NO, INIZIA DA ZERO") { dialog, _ ->
                        prefs.edit().putBoolean("has_imported_backup", true).apply()
                        dialog.dismiss()
                    }
                    .show()
            }
        } else {
            Log.d(TAG, "Nessun backup trovato")
        }
    }

    private fun restoreFromBackup(file: File) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Inizio ripristino da: ${file.absolutePath}")
                
                val jsonContent = withContext(Dispatchers.IO) { 
                    file.readText() 
                }
                Log.d(TAG, "File letto, lunghezza: ${jsonContent.length} caratteri")
                
                val backupJson = JSONObject(jsonContent)
                
                val workDataStore = WorkDataStore(this@MainActivity)
                val contractDataStore = ContractDataStore(this@MainActivity)
                
                // 1. Impostazioni contratto
                if (backupJson.has("settings")) {
                    val settings = backupJson.getJSONObject("settings")
                    val weeklyHours = settings.optInt("weeklyHours", 38)
                    val workDays = settings.optInt("workDays", 6)
                    contractDataStore.saveContract(weeklyHours, workDays)
                    Log.d(TAG, "Contratto ripristinato: $weeklyHours ore, $workDays giorni")
                }
                
                // 2. Badge NFC
                if (backupJson.has("settings")) {
                    val settings = backupJson.getJSONObject("settings")
                    val nfcUid = settings.optString("nfcBadgeUid", "")
                    if (nfcUid.isNotEmpty()) {
                        nfcBadgeManager.saveBadge(nfcUid, "Badge Principale")
                        Log.d(TAG, "Badge NFC ripristinato: $nfcUid")
                    }
                }
                
                // 3. Giorni produzione
                if (backupJson.has("productionDays")) {
                    val prodArray = backupJson.getJSONArray("productionDays")
                    for (i in 0 until prodArray.length()) {
                        val day = prodArray.getString(i)
                        workDataStore.saveProductionMode(day, true)
                    }
                    Log.d(TAG, "Giorni produzione ripristinati: ${prodArray.length()}")
                }

                // 4. Note di giornata
                if (backupJson.has("dailyNotes")) {
                    val notesJson = backupJson.getJSONObject("dailyNotes")
                    val keys = notesJson.keys()
                    var noteCount = 0
                    while (keys.hasNext()) {
                        val date = keys.next()
                        val note = notesJson.getString(date)
                        workDataStore.saveNote(date, note)
                        noteCount++
                    }
                    Log.d(TAG, "Note ripristinate: $noteCount")
                }
                
                // 5. Marcature
                if (backupJson.has("entries")) {
                    val entriesArray = backupJson.getJSONArray("entries")
                    var entryCount = 0
                    for (i in 0 until entriesArray.length()) {
                        val entryJson = entriesArray.getJSONObject(i)
                        val entry = WorkEntry(
                            timestamp = entryJson.getLong("timestamp"),
                            type = entryJson.getString("type"),
                            activity = entryJson.optString("activity", "Servizio"),
                            location = entryJson.optString("location", "Modena"),
                            date = entryJson.getString("date")
                        )
                        workDataStore.addEntry(entry)
                        entryCount++
                    }
                    Log.d(TAG, "Marcature ripristinate: $entryCount")
                }
                
                Toast.makeText(
                    this@MainActivity, 
                    "Backup ripristinato con successo!", 
                    Toast.LENGTH_LONG
                ).show()
                Log.d(TAG, "Ripristino completato")
                
            } catch (e: Exception) {
                val errorMsg = "Errore nel ripristino: ${e.message}"
                Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                Log.e(TAG, "Errore ripristino", e)
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch(Dispatchers.IO) {
            val success = backupManager.performAutoBackup()
            if (success) {
                Log.d(TAG, "Backup automatico salvato")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            if (tag != null) {
                val uid = nfcBadgeManager.getTagUid(tag)
                if (uid != null) {
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment
                    
                    if (currentFragment is SettingsFragment) {
                        currentFragment.onNfcTagDetected(tag)
                    } else {
                        lifecycleScope.launch {
                            try {
                                val savedUid = nfcBadgeManager.savedBadgeUid.first()
                                if (savedUid != null && savedUid == uid) {
                                    val dialog = NfcUnlockDialog(uid) {
                                        Log.d(TAG, "Marcatura completata")
                                    }
                                    dialog.show(supportFragmentManager, "nfc_unlock_dialog")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Errore NFC: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        }
    }
}
