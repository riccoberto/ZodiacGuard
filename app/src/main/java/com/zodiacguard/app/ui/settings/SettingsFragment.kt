package com.zodiacguard.app.ui.settings

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
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

    companion object {
        private const val TAG = "SettingsFragment"
    }

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
                Log.d(TAG, "savedBadgeUid flow: $uid")
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
            Log.d(TAG, "Modalità registrazione attivata")
            Toast.makeText(requireContext(), "Avvicina ORA il badge al retro del telefono...", Toast.LENGTH_LONG).show()
        }
    }

    // Metodo pubblico chiamato da MainActivity quando rileva un tag NFC
    fun onNfcTagDetected(tag: android.nfc.Tag) {
        Log.d(TAG, "onNfcTagDetected chiamato! isRegisteringNfc=$isRegisteringNfc")
        Log.d(TAG, "isAdded=$isAdded, _binding!=null=${_binding != null}")
        
        // Controllo di sicurezza: il fragment è ancora valido?
        if (!isAdded || _binding == null) {
            Log.w(TAG, "Fragment non valido, ignoro")
            return
        }
        
        if (isRegisteringNfc) {
            val uid = nfcBadgeManager.getTagUid(tag)
            Log.d(TAG, "UID estratto: $uid")
            
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
                            Log.d(TAG, "Chiamo saveBadge con UID: $uid")
                            nfcBadgeManager.saveBadge(uid, "Badge Principale")
                            Log.d(TAG, "saveBadge completato")
                            
                            if (isAdded && _binding != null) {
                                Toast.makeText(requireContext(), "Badge registrato con successo!", Toast.LENGTH_SHORT).show()
                            }
                            isRegisteringNfc = false
                            Log.d(TAG, "Registrazione completata")
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore salvataggio: ${e.message}", e)
                            if (isAdded && _binding != null) {
                                Toast.makeText(requireContext(), "Errore salvataggio: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Errore vibrazione: ${e.message}", e)
                }
            } else {
                Log.e(TAG, "UID è null!")
                if (isAdded && _binding != null) {
                    Toast.makeText(requireContext(), "Impossibile leggere il badge", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.w(TAG, "Non in modalità registrazione")
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
