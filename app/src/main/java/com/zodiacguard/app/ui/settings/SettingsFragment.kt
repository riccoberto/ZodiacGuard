package com.zodiacguard.app.ui.settings

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zodiacguard.app.data.ContractDataStore
import com.zodiacguard.app.data.NfcBadgeManager
import com.zodiacguard.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var contractDataStore: ContractDataStore
    private lateinit var nfcBadgeManager: NfcBadgeManager
    
    private var nfcAdapter: NfcAdapter? = null
    private var isRegisteringNfc = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        contractDataStore = ContractDataStore(requireContext())
        nfcBadgeManager = NfcBadgeManager(requireContext())
        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())
        return binding.root
    }

    // Funzione helper per scrivere nella console
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
                logDebug("ERRORE: NFC disattivato nelle impostazioni!")
                return@setOnClickListener
            }
            isRegisteringNfc = true
            logDebug(">>> MODALITÀ REGISTRAZIONE ATTIVATA <<<")
            logDebug("Avvicina ora il badge...")
            Toast.makeText(requireContext(), "Avvicina ORA il badge...", Toast.LENGTH_LONG).show()
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
                try {
                    // Vibrazione
                    val vibrator = requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)) // 300ms per distinguerla
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(300)
                    }
                    logDebug("Vibrazione inviata (300ms)")

                    // Salvataggio
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            logDebug("Tentativo di salvataggio...")
                            nfcBadgeManager.saveBadge(uid, "Badge Principale")
                            logDebug("SALVATAGGIO COMPLETATO!")
                            Toast.makeText(requireContext(), "Badge registrato!", Toast.LENGTH_SHORT).show()
                            isRegisteringNfc = false
                        } catch (e: Exception) {
                            logDebug("ERRORE salvataggio: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    logDebug("ERRORE vibrazione: ${e.message}")
                }
            } else {
                logDebug("ERRORE: UID è null, tag non leggibile")
            }
        } else {
            logDebug("Tag ricevuto MA non siamo in modalità registrazione")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
