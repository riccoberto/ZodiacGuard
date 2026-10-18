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
        }
    }

    private fun restoreFromBackup(file: File) {
        lifecycleScope.launch {
            try {
                val jsonContent = withContext(Dispatchers.IO) { file.readText() }
                val backupJson = JSONObject(jsonContent)
                
                val workDataStore = WorkDataStore(this@MainActivity)
                val contractDataStore = ContractDataStore(this@MainActivity)
                
                // 1. Impostazioni
                val settings = backupJson.getJSONObject("settings")
                contractDataStore.saveContract(settings.getInt("weeklyHours"), settings.getInt("workDays"))
                
                // 2. Badge NFC
                val nfcUid = settings.getString("nfcBadgeUid")
                if (nfcUid.isNotEmpty()) {
                    nfcBadgeManager.saveBadge(nfcUid, "Badge Principale")
                }
                
                // 3. Giorni produzione
                val prodArray = backupJson.getJSONArray("productionDays")
                for (i in 0 until prodArray.length()) {
                    workDataStore.saveProductionMode(prodArray.getString(i), true)
                }

                // 4. Note di giornata (NUOVO)
                if (backupJson.has("dailyNotes")) {
                    val notesJson = backupJson.getJSONObject("dailyNotes")
                    val keys = notesJson.keys()
                    while (keys.hasNext()) {
                        val date = keys.next()
                        val note = notesJson.getString(date)
                        workDataStore.saveNote(date, note)
                    }
                }
                
                // 5. Marcature
                val entriesArray = backupJson.getJSONArray("entries")
                for (i in 0 until entriesArray.length()) {
                    val entryJson = entriesArray.getJSONObject(i)
                    val entry = WorkEntry(
                        timestamp = entryJson.getLong("timestamp"),
                        type = entryJson.getString("type"),
                        activity = entryJson.getString("activity"),
                        location = entryJson.getString("location"),
                        date = entryJson.getString("date")
                    )
                    workDataStore.addEntry(entry)
                }
                
                Toast.makeText(this@MainActivity, "Tutto ripristinato! (Marcature, Note, Viaggi e Buoni Pasto verranno ricalcolati)", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Errore nel ripristino: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "Errore ripristino", e)
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
            backupManager.performAutoBackup()
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
                                        Log.d("MainActivity", "Marcatura completata")
                                    }
                                    dialog.show(supportFragmentManager, "nfc_unlock_dialog")
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Errore: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        }
    }
}
