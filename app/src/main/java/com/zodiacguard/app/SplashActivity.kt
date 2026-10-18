package com.zodiacguard.app

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Controlla se l'intent è NFC
        val isNfcIntent = intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED ||
                         intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
                         intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED

        if (isNfcIntent) {
            // Se è NFC, vai subito a MainActivity passando l'intent
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.action = intent?.action
            mainIntent.putExtra(NfcAdapter.EXTRA_TAG, intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG))
            startActivity(mainIntent)
            finish()
        } else {
            // Altrimenti, delay normale di 2 secondi
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }, 2000)
        }
    }
}
