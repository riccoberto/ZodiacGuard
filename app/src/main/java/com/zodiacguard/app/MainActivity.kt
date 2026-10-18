package com.zodiacguard.app

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.zodiacguard.app.data.NfcBadgeManager
import com.zodiacguard.app.ui.settings.NfcUnlockDialog
import com.zodiacguard.app.ui.settings.SettingsFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null
    private lateinit var nfcBadgeManager: NfcBadgeManager
    private var pendingNfcIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nfcBadgeManager = NfcBadgeManager(this)
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
        
        Log.d("MainActivity", "onCreate chiamato")
        
        // Salva l'intent NFC per elaborarlo dopo (in onResume)
        if (isNfcIntent(intent)) {
            pendingNfcIntent = intent
            Log.d("MainActivity", "Intent NFC salvato per elaborazione successiva")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume chiamato")
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
        
        // Elabora l'intent NFC salvato (se presente) dopo che il fragment è stato creato
        if (pendingNfcIntent != null) {
            Log.d("MainActivity", "Elaboro intent NFC salvato...")
            handleNfcIntent(pendingNfcIntent)
            pendingNfcIntent = null
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause chiamato")
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("MainActivity", "onNewIntent chiamato: ${intent.action}")
        handleNfcIntent(intent)
    }

    private fun isNfcIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || 
               NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
               NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) {
            Log.d("MainActivity", "handleNfcIntent: intent è null")
            return
        }
        
        Log.d("MainActivity", "handleNfcIntent: action = ${intent.action}")
        
        if (isNfcIntent(intent)) {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            if (tag != null) {
                val uid = nfcBadgeManager.getTagUid(tag)
                Log.d("MainActivity", "Tag NFC rilevato con UID: $uid")
                
                if (uid != null) {
                    // Controlla se siamo nel fragment Settings
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment
                    
                    Log.d("MainActivity", "Fragment corrente: ${currentFragment?.javaClass?.simpleName}")
                    
                    if (currentFragment is SettingsFragment) {
                        // Siamo nelle Impostazioni: passa il tag al fragment per la registrazione
                        Log.d("MainActivity", "Passo il tag a SettingsFragment")
                        currentFragment.onNfcTagDetected(tag)
                    } else {
                        // Non siamo nelle Impostazioni: controlla se è il badge registrato
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val savedUid = nfcBadgeManager.savedBadgeUid.first()
                                Log.d("MainActivity", "Badge salvato nel DB: $savedUid")
                                
                                if (savedUid != null && savedUid == uid) {
                                    Log.d("MainActivity", "Badge corrispondente! Apro popup...")
                                    val dialog = NfcUnlockDialog(uid) {
                                        Log.d("MainActivity", "Marcatura completata dal popup")
                                    }
                                    dialog.show(supportFragmentManager, "nfc_unlock_dialog")
                                } else {
                                    Log.w("MainActivity", "Badge non corrispondente o non registrato")
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Errore lettura badge: ${e.message}", e)
                            }
                        }
                    }
                }
            } else {
                Log.w("MainActivity", "Tag è null")
            }
        }
    }
}
