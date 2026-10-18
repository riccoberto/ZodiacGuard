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
import com.zodiacguard.app.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inizializza NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            ndef.addDataType("*/*")
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            Log.e("MainActivity", "NFC IntentFilter error", e)
        }
        intentFiltersArray = arrayOf(ndef)
        techListsArray = arrayOf(arrayOf(android.nfc.tech.NfcA::class.java.name))
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            if (tag != null) {
                // Trova il fragment Settings se è attivo e passa il tag
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment
                
                if (currentFragment is SettingsFragment) {
                    currentFragment.onNfcTagDetected(tag)
                }
            }
        }
    }
}
