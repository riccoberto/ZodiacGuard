package com.zodiacguard.app

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.zodiacguard.app.data.BackupManager
import com.zodiacguard.app.data.NfcBadgeManager
import com.zodiacguard.app.ui.settings.NfcUnlockDialog
import com.zodiacguard.app.ui.settings.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                Log.d(TAG, "Backup automatico salvato via MediaStore")
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
