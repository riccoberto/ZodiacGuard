package com.zodiacguard.app.ui.settings

import android.app.Dialog
import android.content.Intent
import android.nfc.NfcAdapter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.zodiacguard.app.R
import com.zodiacguard.app.data.BackupManager
import com.zodiacguard.app.data.ContractDataStore
import com.zodiacguard.app.data.NfcBadgeManager
import com.zodiacguard.app.data.WorkDataStore
import com.zodiacguard.app.data.WorkEntry
import com.zodiacguard.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar

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

        // 1. Carica i valori contrattuali salvati
        viewLifecycleOwner.lifecycleScope.launch {
            contractDataStore.weeklyHoursFlow.collectLatest { hours ->
                if (isAdded && _binding != null) binding.etWeeklyHours.setText(hours.toString())
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
            val days = binding.etWorkDays.text.toString().toIntOrNull() ?: 6
            viewLifecycleOwner.lifecycleScope.launch {
                contractDataStore.saveContract(hours, days)
                Toast.makeText(requireContext(), "Parametri salvati!", Toast.LENGTH_SHORT).show()
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

        // 5. NUOVO: Pulsante per ripristinare da backup
        binding.btnRestoreBackup.setOnClickListener {
            logDebug("Apertura file picker per backup...")
            restoreBackupLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }

    // Metodo pubblico chiamato da MainActivity quando rileva un tag NFC
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

    // NUOVO: Ripristina da file di backup selezionato manualmente
    private fun restoreFromBackupFile(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                logDebug("Inizio ripristino da: $uri")
                
                // Leggi il contenuto del file
                val jsonContent = withContext(Dispatchers.IO) {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    reader.readText()
                }
                
                logDebug("File letto, lunghezza: ${jsonContent.length} caratteri")
                val backupJson = JSONObject(jsonContent)
                
                val workDataStore = WorkDataStore(requireContext())
                val contractDataStore = ContractDataStore(requireContext())
                
                // 1. Ripristina impostazioni contratto
                if (backupJson.has("settings")) {
                    val settings = backupJson.getJSONObject("settings")
                    val weeklyHours = settings.optInt("weeklyHours", 38)
                    val workDays = settings.optInt("workDays", 6)
                    contractDataStore.saveContract(weeklyHours, workDays)
                    logDebug("Contratto ripristinato: $weeklyHours ore, $workDays giorni")
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

// Dialog per marcatura rapida NFC (mantenuto qui per compatibilità)
class NfcUnlockDialog(
    private val badgeUid: String,
    private val onMarkingComplete: () -> Unit
) : DialogFragment() {

    private lateinit var workDataStore: WorkDataStore
    private var isUnlocked = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        workDataStore = WorkDataStore(requireContext())
        return inflater.inflate(R.layout.dialog_nfc_unlock, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvBadgeInfo = view.findViewById<TextView>(R.id.tvBadgeInfo)
        val sliderUnlock = view.findViewById<Slider>(R.id.sliderUnlock)
        val layoutButtons = view.findViewById<LinearLayout>(R.id.layoutButtons)
        val btnCheckIn = view.findViewById<Button>(R.id.btnCheckIn)
        val btnCheckOut = view.findViewById<Button>(R.id.btnCheckOut)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        tvBadgeInfo.text = "Badge: ${badgeUid.take(4)}...${badgeUid.takeLast(4)}"

        btnCheckIn.isEnabled = false
        btnCheckOut.isEnabled = false

        sliderUnlock.addOnChangeListener(Slider.OnChangeListener { slider, value, fromUser ->
            if (fromUser && value >= 100f && !isUnlocked) {
                isUnlocked = true
                layoutButtons.alpha = 1.0f
                btnCheckIn.isEnabled = true
                btnCheckOut.isEnabled = true
                sliderUnlock.isEnabled = false
                Toast.makeText(requireContext(), "Sbloccato! Scegli ENTRATA o USCITA", Toast.LENGTH_SHORT).show()
            }
        })

        btnCheckIn.setOnClickListener {
            registerMarking("ENTRATA")
        }

        btnCheckOut.setOnClickListener {
            registerMarking("USCITA")
        }

        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun registerMarking(type: String) {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val minuteStr = String.format("%02d", minute)
        
        val entry = WorkEntry(
            timestamp = now.timeInMillis,
            type = type,
            activity = "Servizio",
            location = "Modena",
            date = WorkEntry.getCurrentDate()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                workDataStore.addEntry(entry)
                Toast.makeText(
                    requireContext(),
                    "$type registrata alle $hour:$minuteStr",
                    Toast.LENGTH_LONG
                ).show()
                onMarkingComplete()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
