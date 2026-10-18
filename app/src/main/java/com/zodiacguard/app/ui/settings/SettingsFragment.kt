package com.zodiacguard.app.ui.settings

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Carica i valori contrattuali salvati
        viewLifecycleOwner.lifecycleScope.launch {
            contractDataStore.weeklyHoursFlow.collectLatest { hours ->
                if (isAdded && _binding != null) {
                    binding.etWeeklyHours.setText(hours.toString())
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            contractDataStore.workDaysFlow.collectLatest { days ->
                if (isAdded && _binding != null) {
                    binding.etWorkDays.setText(days.toString())
                }
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
                Toast.makeText(requireContext(), "NFC non supportato su questo dispositivo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!nfcAdapter!!.isEnabled) {
                Toast.makeText(requireContext(), "Attiva l'NFC dalle impostazioni del telefono", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            isRegisteringNfc = true
            Toast.makeText(requireContext(), "Avvicina ORA il badge al retro del telefono...", Toast.LENGTH_LONG).show()
        }
    }

    // Metodo pubblico chiamato da MainActivity quando rileva un tag NFC
    fun onNfcTagDetected(tag: android.nfc.Tag) {
        // Controllo di sicurezza: il fragment è ancora valido?
        if (!isAdded || _binding == null) {
            return
        }
        
        if (isRegisteringNfc) {
            val uid = nfcBadgeManager.getTagUid(tag)
            if (uid != null) {
                try {
                    // Vibrazione di conferma
                    val vibrator = requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(200)
                    }

                    // Salvataggio in modo sicuro
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            nfcBadgeManager.saveBadge(uid, "Badge Principale")
                            if (isAdded && _binding != null) {
                                Toast.makeText(requireContext(), "Badge registrato con successo!", Toast.LENGTH_SHORT).show()
                            }
                            isRegisteringNfc = false
                        } catch (e: Exception) {
                            if (isAdded && _binding != null) {
                                Toast.makeText(requireContext(), "Errore salvataggio: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset flag quando torniamo al fragment
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
