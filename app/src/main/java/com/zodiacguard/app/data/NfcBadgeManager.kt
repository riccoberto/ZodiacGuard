package com.zodiacguard.app.data

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.util.Log
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
        private const val TAG = "NfcBadgeManager"
    }

    // Estrae l'UID da un tag NFC (funziona con tutti i tipi di tag)
    fun getTagUid(tag: Tag): String? {
        return try {
            val uidBytes = tag.id
            if (uidBytes == null || uidBytes.isEmpty()) {
                Log.e(TAG, "Tag ID vuoto")
                return null
            }
            val uid = uidBytes.joinToString("") { String.format("%02X", it) }
            Log.d(TAG, "Tag UID letto: $uid")
            Log.d(TAG, "Tech list: ${tag.techList.joinToString(", ")}")
            uid
        } catch (e: Exception) {
            Log.e(TAG, "Errore lettura UID: ${e.message}")
            null
        }
    }

    // Salva l'UID del badge associato
    suspend fun saveBadge(uid: String, name: String = "Il mio badge") {
        Log.d(TAG, "Salvataggio badge: $uid")
        context.nfcDataStore.edit { prefs ->
            prefs[KEY_BADGE_UID] = uid
            prefs[KEY_BADGE_NAME] = name
        }
        Log.d(TAG, "Badge salvato con successo")
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

    // Controlla se il dispositivo supporta NFC
    fun isNfcAvailable(): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return nfcAdapter != null && nfcAdapter.isEnabled
    }
}
