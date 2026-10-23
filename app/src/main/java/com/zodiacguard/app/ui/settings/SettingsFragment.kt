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
import androidx.appcompat.app.AlertDialog
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
import com.zodiacguard.app.data.WorkEntryMerger
import com.zodiacguard.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
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
    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) exportBackupTo(uri)
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
        binding.btnSaveBackup.setOnClickListener {
            val timestamp = java.text.SimpleDateFormat(
                "yyyyMMdd_HHmm",
                java.util.Locale.ROOT
            ).format(java.util.Date())
            exportBackupLauncher.launch("ZGuard_Backup_$timestamp.json")
        }
        binding.btnRecoverAutoBackup.setOnClickListener {
            val uri = backupManager.getInternalBackupUri()
            if (uri == null) {
                Toast.makeText(
                    requireContext(),
                    "Non è disponibile un backup automatico locale.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                restoreFromBackupFile(uri)
            }
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
                    requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).readText()
                    } ?: throw IOException("Impossibile leggere il file selezionato")
                }
                logDebug("File letto, lunghezza: ${jsonContent.length} caratteri")
                val backupJson = JSONObject(jsonContent)
                val entries = parseBackupEntries(backupJson)
                validateBackupStructure(backupJson)
                val currentEntries = WorkDataStore(requireContext()).entriesFlow.first()
                val newEntryCount = WorkEntryMerger.merge(currentEntries, entries).addedCount
                val backupDate = backupJson.optString("lastUpdate").ifBlank {
                    "data non disponibile"
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Verifica backup")
                    .setMessage(
                        (if (entries.isEmpty()) {
                            "ATTENZIONE: il file non contiene marcature.\n\n"
                        } else {
                            ""
                        }) +
                            "File aggiornato: $backupDate\n" +
                            "Marcature nel file: ${entries.size}\n" +
                            "Nuove marcature da aggiungere: $newEntryCount\n\n" +
                            "L'importazione unirà i dati e ignorerà le marcature già presenti. " +
                            "I dati esistenti non verranno cancellati."
                    )
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Unisci backup") { _, _ ->
                        applyBackup(backupJson, entries)
                    }
                    .show()
            } catch (e: Exception) {
                logDebug("ERRORE nel controllo backup: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Backup non valido o illeggibile: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun parseBackupEntries(backupJson: JSONObject): List<WorkEntry> {
        val entriesArray = backupJson.optJSONArray("entries") ?: return emptyList()
        return (0 until entriesArray.length()).map { index ->
            val entryJson = entriesArray.getJSONObject(index)
            val timestamp = entryJson.getLong("timestamp")
            val type = entryJson.getString("type")
            val date = entryJson.getString("date")
            if (timestamp < 0 || type !in setOf("ENTRATA", "USCITA") ||
                !date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))
            ) {
                throw IOException("Marcatura non valida alla riga ${index + 1}")
            }
            WorkEntry(
                timestamp = timestamp,
                type = type,
                activity = entryJson.optString("activity", "Servizio"),
                location = entryJson.optString("location", "Modena"),
                date = date
            )
        }
    }

    private fun validateBackupStructure(backupJson: JSONObject) {
        if (!listOf(
                "entries",
                "settings",
                "manualCounters",
                "productionDays",
                "dailyNotes"
            ).any(backupJson::has)
        ) {
            throw IOException("Il file non ha una struttura di backup riconosciuta")
        }

        if (backupJson.has("settings")) {
            val settings = backupJson.getJSONObject("settings")
            val hours = if (settings.has("weeklyHours")) settings.getInt("weeklyHours") else 38
            val minutes = if (settings.has("weeklyMinutes")) settings.getInt("weeklyMinutes") else 0
            val days = if (settings.has("workDays")) settings.getInt("workDays") else 6
            if (hours < 0 || minutes !in 0..59 || days !in 1..7 ||
                (hours == 0 && minutes == 0)
            ) {
                throw IOException("Impostazioni contrattuali non valide nel backup")
            }
        }

        if (backupJson.has("entries") && backupJson.optJSONArray("entries") == null) {
            throw IOException("Elenco marcature non valido nel backup")
        }
        if (backupJson.has("manualCounters") && backupJson.optJSONArray("manualCounters") == null) {
            throw IOException("Elenco contatori non valido nel backup")
        }
        backupJson.optJSONArray("manualCounters")?.let { counters ->
            for (index in 0 until counters.length()) {
                val counter = counters.getJSONObject(index)
                if (counter.optString("id").isBlank() ||
                    counter.optString("title").isBlank() ||
                    !counter.has("initialMinutes") ||
                    counter.getInt("initialMinutes") < 0 ||
                    !counter.has("currentMinutes") ||
                    counter.getInt("currentMinutes") < 0 ||
                    (counter.has("dayLengthMinutes") &&
                        counter.getInt("dayLengthMinutes") <= 0)
                ) {
                    throw IOException("Contatore non valido nel backup")
                }
            }
        }

        if (backupJson.has("productionDays") && backupJson.optJSONArray("productionDays") == null) {
            throw IOException("Elenco giorni di produzione non valido nel backup")
        }
        backupJson.optJSONArray("productionDays")?.let { days ->
            for (index in 0 until days.length()) {
                if (!days.getString(index).matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                    throw IOException("Data di produzione non valida nel backup")
                }
            }
        }

        if (backupJson.has("dailyNotes") && backupJson.optJSONObject("dailyNotes") == null) {
            throw IOException("Elenco note non valido nel backup")
        }
        backupJson.optJSONObject("dailyNotes")?.let { notes ->
            val keys = notes.keys()
            while (keys.hasNext()) {
                val date = keys.next()
                if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                    throw IOException("Data di una nota non valida nel backup")
                }
                notes.getString(date)
            }
        }
    }

    private fun applyBackup(backupJson: JSONObject, importedEntries: List<WorkEntry>) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!withContext(Dispatchers.IO) {
                        backupManager.createInternalRecoveryBackup()
                    }
                ) {
                    throw IOException("Non è stato possibile salvare una copia di sicurezza locale")
                }

                val workDataStore = WorkDataStore(requireContext())
                val contractDataStore = ContractDataStore(requireContext())
                val manualCounterDataStore = ManualCounterDataStore(requireContext())
                if (backupJson.has("settings")) {
                    val settings = backupJson.getJSONObject("settings")
                    val weeklyHours = settings.optInt("weeklyHours", 38)
                    val weeklyMinutes = settings.optInt("weeklyMinutes", 0)
                    val workDays = settings.optInt("workDays", 6)
                    if (weeklyHours < 0 || weeklyMinutes !in 0..59 ||
                        workDays !in 1..7 || (weeklyHours == 0 && weeklyMinutes == 0)
                    ) {
                        throw IOException("Impostazioni del contratto non valide")
                    }
                    if (settings.has("displayName")) {
                        UserProfilePreferences.saveDisplayName(
                            requireContext(),
                            settings.optString("displayName", "")
                        )
                    }

                    contractDataStore.saveContract(weeklyHours, weeklyMinutes, workDays)
                    logDebug("Contratto ripristinato: $weeklyHours ore e $weeklyMinutes minuti, $workDays giorni")
                }

                if (backupJson.has("manualCounters")) {
                    val savedCounters = backupJson.getJSONArray("manualCounters")
                    val imported = (0 until savedCounters.length()).map { index ->
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
                    val existing = manualCounterDataStore.countersFlow.first()
                    val merged = (existing + imported).associateBy { it.id }.values.toList()
                    manualCounterDataStore.replaceCounters(merged)
                    logDebug("Contatori manuali uniti: ${imported.size}")
                }

                if (backupJson.has("settings")) {
                    val settings = backupJson.getJSONObject("settings")
                    val nfcUid = settings.optString("nfcBadgeUid", "")
                    if (nfcUid.isNotEmpty()) {
                        nfcBadgeManager.saveBadge(nfcUid, "Badge Principale")
                        logDebug("Badge NFC ripristinato: $nfcUid")
                    }
                }

                if (backupJson.has("productionDays")) {
                    val prodArray = backupJson.getJSONArray("productionDays")
                    val productionDays = workDataStore.productionDaysFlow.first() +
                        (0 until prodArray.length()).map { prodArray.getString(it) }
                    workDataStore.saveAllProductionDays(productionDays)
                    logDebug("Giorni produzione ripristinati: ${prodArray.length()}")
                }

                if (backupJson.has("dailyNotes")) {
                    val notesJson = backupJson.getJSONObject("dailyNotes")
                    val keys = notesJson.keys()
                    val notes = workDataStore.notesFlow.first().toMutableMap()
                    var noteCount = 0
                    while (keys.hasNext()) {
                        val date = keys.next()
                        notes[date] = notesJson.getString(date)
                        noteCount++
                    }
                    workDataStore.saveAllNotes(notes)
                    logDebug("Note ripristinate: $noteCount")
                }

                val existingEntries = workDataStore.entriesFlow.first()
                val mergeResult = WorkEntryMerger.merge(existingEntries, importedEntries)
                workDataStore.saveAllEntries(mergeResult.entries)
                val backupSaved = withContext(Dispatchers.IO) {
                    backupManager.createBackupNow()
                }
                if (!backupSaved) {
                    logDebug("Attenzione: ripristino completato, backup aggiornato solo parzialmente")
                }
                val message =
                    "Aggiunte ${mergeResult.addedCount} marcature; i dati presenti sono stati conservati."
                logDebug("RIPRISTINO COMPLETATO: $message")
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                logDebug("ERRORE nel ripristino: ${e.message}")
                Toast.makeText(requireContext(), "Errore nel ripristino: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportBackupTo(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    backupManager.exportBackup(uri)
                }
                val message = "Backup esportato: ${summary.entryCount} marcature, " +
                    "${summary.counterCount} contatori."
                logDebug(message)
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            } catch (exception: Exception) {
                logDebug("ERRORE esportazione backup: ${exception.message}")
                Toast.makeText(
                    requireContext(),
                    "Esportazione backup non riuscita: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
