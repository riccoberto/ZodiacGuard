package com.zodiacguard.app.data

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.IsoDep
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.nfcDataStore: DataStore<Preferences> by preferencesDataStore(name = "nfc_prefs")

class NfcBadgeManager(private val context: Context) {

    companion object {
        val KEY_BADGE_UID = stringPreferencesKey("nfc_badge_uid")
        val KEY_BADGE_NAME = stringPreferencesKey("nfc_badge_name")
    }

    // Estrae l'UID da un tag NFC (funziona con tutti i tipi di tag)
    fun getTagUid(tag: Tag): String? {
        return try {
            val uidBytes = tag.id
            uidBytes.joinToString("") { String.format("%02X", it) }
        } catch (e: Exception) {
            null
        }
    }

    // Salva l'UID del badge associato
    suspend fun saveBadge(uid: String, name: String = "Il mio badge") {
        context.nfcDataStore.edit { prefs ->
            prefs[KEY_BADGE_UID] = uid
            prefs[KEY_BADGE_NAME] = name
        }
    }

    // Rimuove il badge associato
    suspend fun clearBadge() {
        context.nfcDataStore.edit { prefs ->
            prefs.remove(KEY_BADGE_UID)
            prefs.remove(KEY_BADGE_NAME)
        }
    }

    // Legge l'UID del badge salvato
    val savedBadgeUid: Flow<String?> = context.nfcDataStore.data.map { prefs ->
        prefs[KEY_BADGE_UID]
    }

    val savedBadgeName: Flow<String?> = context.nfcDataStore.data.map { prefs ->
        prefs[KEY_BADGE_NAME]
    }

    // Controlla se un tag corrisponde al badge salvato
    suspend fun isMatchingBadge(tag: Tag): Boolean {
        val savedUid = savedBadgeUid
        var result = false
        savedBadgeUid.collect { uid ->
            if (uid != null) {
                val tagUid = getTagUid(tag)
                result = (uid == tagUid)
            }
            return@collect
        }
        return result
    }

    // Controlla se il dispositivo supporta NFC
    fun isNfcAvailable(): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return nfcAdapter != null && nfcAdapter.isEnabled
    }
}
