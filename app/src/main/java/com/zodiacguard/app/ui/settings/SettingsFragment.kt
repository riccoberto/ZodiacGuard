package com.zodiacguard.app.ui.settings

import android.content.Intent
import android.nfc.NfcAdapter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zodiacguard.app.data.BackupManager
import com.zodiacguard.app.data.ContractDataStore
import com.zodiacguard.app.data.NfcBadgeManager
import com.zodiacguard.app.data.ManualCounter
import com.zodiacguard.app.data.ManualCounterDataStore
import com.zodiacguard.app.data.UserProfilePreferences
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.data.WorkEntry
import com.zodiacguard.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var contractDataStore: ContractDataStore
    private lateinit var nfcBadgeManager: NfcBadgeManager
    private lateinit var backupManager: BackupManager
    
    private var nfcAdapter: NfcAdapter? = null
    private var isRegisteringNfc = false

    // File picker per ripristino backup
    private val restoreBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            restoreFromBackupFile(uri)
        } else {
            logDebug("Ripristino annullato dall'utente")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        contractDataStore = ContractDataStore(requireContext())
        nfcBadgeManager = NfcBadgeManager(requireContext())
        backupManager = BackupManager(requireContext())
        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())
        return binding.root
    }

    private fun logDebug(msg: String) {
        if (_binding != null) {
            binding.tvDebugLog.append("$msg\n")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logDebug("Fragment creato. NFC disponibile: ${nfcAdapter != null}")

        binding.etDisplayName.setText(UserProfilePreferences.getDisplayName(requireContext()))
        binding.etDisplayName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                UserProfilePreferences.saveDisplayName(
                    requireContext(),
                    s?.toString().orEmpty()
                )
            }
        })

        // 1. Carica i valori contrattuali salvati
        viewLifecycleOwner.lifecycleScope.launch {
            contractDataStore.weeklyHoursFlow.collectLatest { hours ->
                if (isAdded && _binding != null) binding.etWeeklyHours.setText(hours.toString())
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            contractDataStore.weeklyMinutesFlow.collectLatest { minutes ->
                if (isAdded && _binding != null) binding.etWeeklyMinutes.setText(minutes.toString())
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            contractDataStore.workDaysFlow.collectLatest { days ->
                if (isAdded && _binding != null) binding.etWorkDays.setText(days.toString())
            }
        }

        // 2. Salva i valori contrattuali
        binding.btnSaveContract.setOnClickListener {
            val hours = binding.etWeeklyHours.text.toString().toIntOrNull() ?: 38
            val minutes = binding.etWeeklyMinutes.text.toString().toIntOrNull() ?: 0
            val days = binding.etWorkDays.text.toString().toIntOrNull() ?: 6
            if (hours < 0 || minutes !in 0..59 || days !in 1..7 || (hours == 0 && minutes == 0)) {
                Toast.makeText(
                    requireContext(),
                    "Inserisci ore valide, minuti da 0 a 59 e giorni da 1 a 7",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            UserProfilePreferences.saveDisplayName(
                requireContext(),
                binding.etDisplayName.text?.toString().orEmpty()
            )
            viewLifecycleOwner.lifecycleScope.launch {
                contractDataStore.saveContract(hours, minutes, days)
                Toast.makeText(requireContext(), "Impostazioni salvate!", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Osserva lo stato del badge NFC
        viewLifecycleOwner.lifecycleScope.launch {
            nfcBadgeManager.savedBadgeUid.collectLatest { uid ->
                logDebug("Flow DataStore aggiornato: UID = $uid")
                if (isAdded && _binding != null) {
                    if (uid != null) {
                        binding.tvNfcStatus.text = "✅ Badge registrato (UID: ${uid.take(4)}...${uid.takeLast(4)})"
                        binding.tvNfcStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_light))
                    } else {
                        binding.tvNfcStatus.text = "Stato: Nessun badge registrato"
                        binding.tvNfcStatus.setTextColor(requireContext().getColor(android.R.color.white))
                    }
                }
            }
        }

        // 4. Pulsante per registrare il badge
        binding.btnRegisterNfc.setOnClickListener {
            if (nfcAdapter == null) {
                logDebug("ERRORE: NFC non supportato!")
                return@setOnClickListener
            }
            if (!nfcAdapter!!.isEnabled) {
                logDebug("ERRORE: NFC disattivato!")
                return@setOnClickListener
            }
            isRegisteringNfc = true
            logDebug(">>> MODALITÀ REGISTRAZIONE ATTIVATA <<<")
            logDebug("Avvicina ora il badge...")
            Toast.makeText(requireContext(), "Avvicina ORA il badge...", Toast.LENGTH_LONG).show()
        }

        // 5. Pulsante per ripristinare da backup
        binding.btnRestoreBackup.setOnClickListener {
            logDebug("Apertura file picker per backup...")
            restoreBackupLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }

    fun onNfcTagDetected(tag: android.nfc.Tag) {
        logDebug("onNfcTagDetected chiamato! isRegisteringNfc=$isRegisteringNfc")
        
        if (!isAdded || _binding == null) {
            logDebug("ERRORE: Fragment non valido")
            return
        }
        
        if (isRegisteringNfc) {
            val uid = nfcBadgeManager.getTagUid(tag)
            logDebug("UID estratto dal tag: $uid")
            logDebug("Tech list tag: ${tag.techList.joinToString(", ")}")
            
            if (uid != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        logDebug("Tentativo di salvataggio...")
                        nfcBadgeManager.saveBadge(uid, "Badge Principale")
                        logDebug("SALVATAGGIO COMPLETATO!")
                        Toast.makeText(requireContext(), "Badge registrato con successo!", Toast.LENGTH_SHORT).show()
                        isRegisteringNfc = false
                    } catch (e: Exception) {
                        logDebug("ERRORE salvataggio: ${e.message}")
                    }
                }
            } else {
                logDebug("ERRORE: UID è null, tag non leggibile")
            }
        } else {
            logDebug("Tag ricevuto MA non siamo in modalità registrazione")
        }
    }

    override fun onPause() {
        saveDisplayName()
        super.onPause()
    }

    private fun saveDisplayName() {
        val currentBinding = _binding ?: return
        UserProfilePreferences.saveDisplayName(
            requireContext(),
            currentBinding.etDisplayName.text?.toString().orEmpty()
        )
    }

    private fun restoreFromBackupFile(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                logDebug("Inizio ripristino da: $uri")
                
                val jsonContent = withContext(Dispatchers.IO) {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    reader.readText()
                }
                
                logDebug("File letto, lunghezza: ${jsonContent.length} caratteri")
                val backupJson = JSONObject(jsonContent)
                
                val workDataStore = WorkDataStore(requireContext())
                val contractDataStore = ContractDataStore(requireContext())
                val manualCounterDataStore = ManualCounterDataStore(requireContext())
                
                // 1. Ripristina impostazioni contratto
                if (backupJson.has("settings")) {
                    val settings = backupJson.getJSONObject("settings")
                    if (settings.has("displayName")) {
                        UserProfilePreferences.saveDisplayName(
                            requireContext(),
                            settings.optString("displayName", "")
                        )
                    }

                    val weeklyHours = settings.optInt("weeklyHours", 38)
                    val weeklyMinutes = settings.optInt("weeklyMinutes", 0)
                    val workDays = settings.optInt("workDays", 6)
                    contractDataStore.saveContract(weeklyHours, weeklyMinutes, workDays)
                    logDebug("Contratto ripristinato: $weeklyHours ore e $weeklyMinutes minuti, $workDays giorni")
                }

                if (backupJson.has("manualCounters")) {
                    val savedCounters = backupJson.getJSONArray("manualCounters")
                    val counters = (0 until savedCounters.length()).map { index ->
                        val counter = savedCounters.getJSONObject(index)
                        ManualCounter(
                            id = counter.getString("id"),
                            title = counter.getString("title"),
                            initialMinutes = counter.getInt("initialMinutes"),
                            currentMinutes = counter.getInt("currentMinutes"),
                            dayLengthMinutes = counter.optInt("dayLengthMinutes", 24 * 60)
                                .coerceAtLeast(1)
                        )
                    }
                    manualCounterDataStore.replaceCounters(counters)
                    logDebug("Contatori manuali ripristinati: ${counters.size}")
                }
                
                // 2. Ripristina badge NFC
                if (backupJson.has("settings")) {
                    val settings = backupJson.getJSONObject("settings")
                    val nfcUid = settings.optString("nfcBadgeUid", "")
                    if (nfcUid.isNotEmpty()) {
                        nfcBadgeManager.saveBadge(nfcUid, "Badge Principale")
                        logDebug("Badge NFC ripristinato: $nfcUid")
                    }
                }
                
                // 3. Ripristina giorni produzione
                if (backupJson.has("productionDays")) {
                    val prodArray = backupJson.getJSONArray("productionDays")
                    for (i in 0 until prodArray.length()) {
                        val day = prodArray.getString(i)
                        workDataStore.saveProductionMode(day, true)
                    }
                    logDebug("Giorni produzione ripristinati: ${prodArray.length()}")
                }

                // 4. Ripristina note di giornata
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
                    logDebug("Note ripristinate: $noteCount")
                }
                
                // 5. Ripristina marcature
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
                    logDebug("Marcature ripristinate: $entryCount")
                }
                
                logDebug("RIPRISTINO COMPLETATO CON SUCCESSO!")
                Toast.makeText(requireContext(), "Backup ripristinato con successo!", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                logDebug("ERRORE nel ripristino: ${e.message}")
                Toast.makeText(requireContext(), "Errore nel ripristino: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
